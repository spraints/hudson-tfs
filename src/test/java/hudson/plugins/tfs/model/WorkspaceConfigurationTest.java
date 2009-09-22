package hudson.plugins.tfs.model;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.Test;

public class WorkspaceConfigurationTest {

    @Test public void assertConfigurationsEquals() {
        WorkspaceConfiguration one = new WorkspaceConfiguration("server", "workspace", "project", "workfolder");
        WorkspaceConfiguration two = new WorkspaceConfiguration("server", "workspace", "project", "workfolder");
        assertThat(one, is(two));
        assertThat(two, is(one));
        assertThat(one, is(one));
        assertThat(one, not(new WorkspaceConfiguration("aserver", "workspace", "project", "workfolder")));
        assertThat(one, not(new WorkspaceConfiguration("server", "aworkspace", "project", "workfolder")));
        assertThat(one, not(new WorkspaceConfiguration("server", "workspace", "aproject", "workfolder")));
        assertThat(one, not(new WorkspaceConfiguration("server", "workspace", "project", "aworkfolder")));
    }

    @Test public void assertAssumesRootMapping() {
        WorkspaceConfiguration configuration = new WorkspaceConfiguration("server", "workspace", "$/path1", "workfolder");
        Entry<String, String> mapping = (Entry<String, String>) configuration.getProjectMappings().toArray()[0];
        assertEquals("workfolder", mapping.getValue());
    }
}
