package hudson.plugins.tfs.actions;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import hudson.FilePath;
import hudson.plugins.tfs.model.ChangeSet;
import hudson.plugins.tfs.model.Project;
import hudson.plugins.tfs.model.Server;
import hudson.plugins.tfs.model.Workspace;
import hudson.plugins.tfs.model.Workspaces;
import hudson.plugins.tfs.model.WorkspaceConfiguration;

public class CheckoutAction {

    private final WorkspaceConfiguration workspaceConfiguration;
    private final boolean useUpdate;

    public CheckoutAction(WorkspaceConfiguration workspaceConfiguration, boolean useUpdate) {
        this.workspaceConfiguration = workspaceConfiguration;
        this.useUpdate = useUpdate;
    }

    public List<ChangeSet> checkout(Server server, FilePath workspacePath, Calendar lastBuildTimestamp) throws IOException, InterruptedException, ParseException {
        
        String workspaceName = workspaceConfiguration.getWorkspaceName();
        Workspaces workspaces = server.getWorkspaces();
        
        if (workspaces.exists(workspaceName) && !useUpdate) {
            Workspace workspace = workspaces.getWorkspace(workspaceName);
            workspaces.deleteWorkspace(workspace);
        }

        Map<Project, String> projectMappings = getProjectMappings(server);
        
        Workspace workspace;
        if (! workspaces.exists(workspaceName)) {
            for(Project project : projectMappings.keySet()) {
                FilePath localFolderPath = workspacePath.child(projectMappings.get(project));
                if (!useUpdate && localFolderPath.exists()) {
                    localFolderPath.deleteContents();
                }
            }
            workspace = workspaces.newWorkspace(workspaceName);
            for(Project project : projectMappings.keySet()) {
                workspace.mapWorkfolder(project, projectMappings.get(project));
            }
        } else {
            workspace = workspaces.getWorkspace(workspaceName);
        }
        
        List<ChangeSet> changes = new ArrayList<ChangeSet>();
        for(Project project : projectMappings.keySet()) {
            project.getFiles(projectMappings.get(project));
        
            if (lastBuildTimestamp != null) {
                changes.addAll(project.getDetailedHistory(lastBuildTimestamp, Calendar.getInstance()));
            }
        }
        return changes;
    }

    private Map<Project, String> getProjectMappings(Server server)
    {
        Map<Project, String> mappings = new java.util.HashMap<Project, String>();
        for(Entry<String, String> mapping : workspaceConfiguration.getProjectMappings())
        {
            mappings.put(server.getProject(mapping.getKey()), mapping.getValue());
        }
        return mappings;
    }
}
