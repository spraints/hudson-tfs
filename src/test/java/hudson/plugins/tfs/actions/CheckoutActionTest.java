package hudson.plugins.tfs.actions;

import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.*;
import static org.mockito.Mockito.*;

import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import hudson.FilePath;
import hudson.plugins.tfs.Util;
import hudson.plugins.tfs.model.ChangeSet;
import hudson.plugins.tfs.model.Project;
import hudson.plugins.tfs.model.Server;
import hudson.plugins.tfs.model.Workspace;
import hudson.plugins.tfs.model.Workspaces;
import hudson.plugins.tfs.model.WorkspaceConfiguration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CheckoutActionTest {

    private FilePath hudsonWs;
    private @Mock Server server;
    private @Mock Workspaces workspaces;
    private @Mock Workspace workspace;
    private @Mock Project project;
    private @Mock Project project2;
    private @Mock ChangeSet changeset;
    
    @Before public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        hudsonWs = Util.createTempFilePath();
    }

    @After public void teardown() throws Exception {
        if (hudsonWs != null) {
            hudsonWs.deleteRecursive();
        }
    }
    
    @Test
    public void assertFirstCheckoutNotUsingUpdate() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.exists("workspace")).thenReturn(true).thenReturn(false);
        when(workspaces.newWorkspace("workspace")).thenReturn(workspace);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), false).checkout(server, hudsonWs,null);
        
        verify(workspaces).newWorkspace("workspace");
        verify(workspace).mapWorkfolder(project, ".");
        verify(project).getFiles(".");
        verify(workspaces).deleteWorkspace(workspace);
    }

    @Test
    public void assertFirstCheckoutUsingUpdate() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("$/path1")).thenReturn(project);
        when(server.getProject("$/path2")).thenReturn(project2);
        when(workspaces.exists(new Workspace(server, "workspace"))).thenReturn(false);
        when(workspaces.newWorkspace("workspace")).thenReturn(workspace);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "$/path1 : a ; $/path2 : b", "."), true).checkout(server, hudsonWs,null);
        
        verify(workspaces).newWorkspace("workspace");
        verify(workspace).mapWorkfolder(project, ".\\a");
        verify(workspace).mapWorkfolder(project2, ".\\b");
        verify(project).getFiles(".\\a");
        verify(project2).getFiles(".\\b");
        verify(workspaces, never()).deleteWorkspace(isA(Workspace.class));
    }

    @Test
    public void assertSecondCheckoutUsingUpdate() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("$/path1")).thenReturn(project);
        when(server.getProject("$/path2")).thenReturn(project2);
        when(workspaces.exists("workspace")).thenReturn(true);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        when(server.getLocalHostname()).thenReturn("LocalComputer");
        when(workspace.getComputer()).thenReturn("LocalComputer");
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "$/path1 : a ; $/path2 : b", "."), true).checkout(server, hudsonWs, null);

        verify(project).getFiles(".\\a");
        verify(project2).getFiles(".\\b");
        verify(workspaces, never()).newWorkspace("workspace");
        verify(workspace, never()).mapWorkfolder(isA(Project.class), isA(String.class));
        verify(workspaces, never()).deleteWorkspace(isA(Workspace.class));
    }

    @Test
    public void assertSecondCheckoutNotUsingUpdate() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.exists("workspace")).thenReturn(true).thenReturn(false);
        when(workspaces.newWorkspace("workspace")).thenReturn(workspace);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), false).checkout(server, hudsonWs,null);

        verify(workspaces).newWorkspace("workspace");
        verify(workspace).mapWorkfolder(project, ".");
        verify(project).getFiles(".");
        verify(workspaces).deleteWorkspace(workspace);
    }
   
    @Test
    public void assertDetailedHistoryIsNotRetrievedInFirstBuild() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.exists("workspace")).thenReturn(true);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        when(server.getLocalHostname()).thenReturn("LocalComputer");
        when(workspace.getComputer()).thenReturn("LocalComputer");
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), true).checkout(server, hudsonWs, null);
        
        verify(project, never()).getDetailedHistory(isA(Calendar.class), isA(Calendar.class));
    }
    
    @Test
    public void assertDetailedHistoryIsRetrievedInSecondBuild() throws Exception {
        List<ChangeSet> list = new ArrayList<ChangeSet>();
        list.add(changeset);
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.exists("workspace")).thenReturn(true);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        when(server.getLocalHostname()).thenReturn("LocalComputer");
        when(workspace.getComputer()).thenReturn("LocalComputer");
        when(project.getDetailedHistory(isA(Calendar.class), isA(Calendar.class))).thenReturn(list);
        
        CheckoutAction action = new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), true);
        List<ChangeSet> actualList = action.checkout(server, hudsonWs, Util.getCalendar(2008, 9, 24));
        assertThat("The list from the detailed history should contain the correct changeset.", actualList, hasItem(changeset));
        assertEquals("The list from the detailed history should contain only the one returned changeset.", 1, actualList.size());
        
        verify(project).getDetailedHistory(eq(Util.getCalendar(2008, 9, 24)), isA(Calendar.class));
    }
    
    @Test
    public void assertWorkFolderIsCleanedIfNotUsingUpdate() throws Exception {
        hudsonWs.createTempFile("temp", "txt");
        FilePath tfsWs = hudsonWs.child("tfs-ws");
        tfsWs.mkdirs();
        tfsWs.createTempFile("temp", "txt");
        
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.exists(new Workspace(server, "workspace"))).thenReturn(false);
        when(workspaces.newWorkspace("workspace")).thenReturn(workspace);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "tfs-ws"), false).checkout(server, hudsonWs, null);
        
        assertTrue("The local folder was removed", tfsWs.exists());
        assertEquals("The local TFS folder was not cleaned", 0, tfsWs.list((FileFilter)null).size());
        assertEquals("The Hudson workspace path was cleaned", 2, hudsonWs.list((FileFilter)null).size());
    }

    @Test
    public void assertWorkspaceIsNotCleanedIfUsingUpdate() throws Exception {
        FilePath tfsWs = hudsonWs.child("tfs-ws");
        tfsWs.mkdirs();
        tfsWs.createTempFile("temp", "txt");
        
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.exists("workspace")).thenReturn(true);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        when(server.getLocalHostname()).thenReturn("LocalComputer");
        when(workspace.getComputer()).thenReturn("LocalComputer");
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "tfs-ws"), true).checkout(server, hudsonWs, null);

        assertTrue("The local folder was removed", tfsWs.exists());
        assertEquals("The TFS workspace path was cleaned", 1, hudsonWs.list((FileFilter)null).size());
    }
    
    @Bug(3882)
    @Test
    public void assertCheckoutDeletesWorkspaceAtStartIfNotUsingUpdate() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(workspaces.exists("workspace")).thenReturn(true).thenReturn(false);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        when(server.getProject("project")).thenReturn(project);
        when(workspaces.newWorkspace("workspace")).thenReturn(workspace);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), false).checkout(server, hudsonWs, null);
        
        verify(server).getWorkspaces();
        verify(workspaces, times(2)).exists("workspace");
        verify(workspaces).getWorkspace("workspace");
        verify(workspaces).deleteWorkspace(workspace);
        verify(workspaces).newWorkspace("workspace");
        verifyNoMoreInteractions(workspaces);
    }
    
    @Bug(3882)
    @Test
    public void assertCheckoutDoesNotDeleteWorkspaceAtStartIfUsingUpdate() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(workspaces.exists("workspace")).thenReturn(true).thenReturn(true);
        when(workspaces.getWorkspace("workspace")).thenReturn(workspace);
        when(server.getProject("project")).thenReturn(project);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), true).checkout(server, hudsonWs, null);
        
        verify(server).getWorkspaces();
        verify(workspaces, times(2)).exists("workspace");
        verify(workspaces).getWorkspace("workspace");
        verifyNoMoreInteractions(workspaces);
    }
    
    @Bug(3882)
    @Test
    public void assertCheckoutDoesNotDeleteWorkspaceIfNotUsingUpdateAndThereIsNoWorkspace() throws Exception {
        when(server.getWorkspaces()).thenReturn(workspaces);
        when(workspaces.exists("workspace")).thenReturn(false).thenReturn(false);
        when(workspaces.newWorkspace("workspace")).thenReturn(workspace);
        when(server.getProject("project")).thenReturn(project);
        
        new CheckoutAction(new WorkspaceConfiguration("don't care", "workspace", "project", "."), false).checkout(server, hudsonWs, null);
        
        verify(server).getWorkspaces();
        verify(workspaces, times(2)).exists("workspace");
        verify(workspaces).newWorkspace("workspace");
        verifyNoMoreInteractions(workspaces);
    }
}
