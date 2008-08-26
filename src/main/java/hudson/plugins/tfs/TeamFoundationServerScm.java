package hudson.plugins.tfs;

import static hudson.Util.fixEmpty;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.tfs.actions.CheckoutAction;
import hudson.plugins.tfs.browsers.TeamFoundationServerRepositoryBrowser;
import hudson.plugins.tfs.model.Server;
import hudson.plugins.tfs.model.ChangeSet;
import hudson.plugins.tfs.util.BuildVariableResolver;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormFieldValidator;
import hudson.util.Scrambler;

/**
 * SCM for Microsoft Team Foundation Server.
 * 
 * @author Erik Ramfelt
 */
public class TeamFoundationServerScm extends SCM {

    public static final String WORKSPACE_ENV_STR = "TFS_WORKSPACE";
    
    private final String serverUrl;
    private final String projectPath;
    private final String localPath;
    private final String workspaceName;
    private final String userPassword;
    private final String userName;
    private final boolean useUpdate;
    
    private TeamFoundationServerRepositoryBrowser repositoryBrowser;

    private transient String normalizedWorkspaceName;

    @DataBoundConstructor
    public TeamFoundationServerScm(String serverUrl, String projectPath, String localPath, boolean useUpdate, String workspaceName, String userName, String userPassword) {
        this.serverUrl = serverUrl;
        this.projectPath = projectPath;
        this.useUpdate = useUpdate;
        this.localPath = (Util.fixEmptyAndTrim(localPath) == null ? "." : localPath);
        this.workspaceName = (Util.fixEmptyAndTrim(workspaceName) == null ? "Hudson-${JOB_NAME}" : workspaceName);
        this.userName = userName;
        this.userPassword = Scrambler.scramble(userPassword);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getLocalPath() {
        return localPath;
    }

    public boolean isUseUpdate() {
        return useUpdate;
    }

    public String getUserPassword() {
        return Scrambler.descramble(userPassword);
    }

    public String getUserName() {
        return userName;
    }

    String getNormalizedWorkspaceName(AbstractProject<?,?> project, Launcher launcher) {
        if (normalizedWorkspaceName == null) {
            normalizedWorkspaceName = Util.replaceMacro(workspaceName, new BuildVariableResolver(project, launcher));
        }
        return normalizedWorkspaceName;
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspaceFilePath, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        Server server = createServer(new TfTool(getDescriptor().getTfExecutable(), launcher, listener, workspaceFilePath));
        
        CheckoutAction action = new CheckoutAction(getNormalizedWorkspaceName(build.getProject(), launcher), 
                projectPath, localPath, useUpdate);
        try {
            List<ChangeSet> list = action.checkout(server, workspaceFilePath, (build.getPreviousBuild() != null ? build.getPreviousBuild().getTimestamp() : null));
            ChangeSetWriter writer = new ChangeSetWriter();
            writer.write(list, changelogFile);
        } catch (ParseException pe) {
            listener.fatalError(pe.getMessage());
            throw new AbortException();
        }
        return true;
    }

    @Override
    public boolean pollChanges(AbstractProject hudsonProject, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        Run<?,?> lastBuild = hudsonProject.getLastBuild();
        if (lastBuild == null) {
            return true;
        } else {
            Server server = createServer(new TfTool(getDescriptor().getTfExecutable(), launcher, listener, workspace));
            try {
                return (server.getProject(this.projectPath).getDetailedHistory(
                            lastBuild.getTimestamp(), 
                            Calendar.getInstance()
                        ).size() > 0);
            } catch (ParseException pe) {
                listener.fatalError(pe.getMessage());
                throw new AbortException();
            }
        }
    }
    
    protected Server createServer(TfTool tool) {
        return new Server(tool, getServerUrl(), getUserName(), getUserPassword());
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    @Override
    public boolean supportsPolling() {
        return true;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new ChangeSetReader();
    }

    @Override
    public FilePath getModuleRoot(FilePath workspace) {
        return workspace.child(localPath);
    }

    @Override
    public TeamFoundationServerRepositoryBrowser getBrowser() {
        return repositoryBrowser;
    }

    @Override
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
        super.buildEnvVars(build, env);
        if (normalizedWorkspaceName != null) {
            env.put(WORKSPACE_ENV_STR, normalizedWorkspaceName);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return PluginImpl.TFS_DESCRIPTOR;
    }

    public static class DescriptorImpl extends SCMDescriptor<TeamFoundationServerScm> {
        
        public static final String USER_AT_DOMAIN_REGEX = "\\w+@\\w+";
        public static final String DOMAIN_SLASH_USER_REGEX = "\\w+\\\\\\w+";
        public static final String PROJECT_PATH_REGEX = "^\\$\\/.*";
        private String tfExecutable;
        
        protected DescriptorImpl() {
            super(TeamFoundationServerScm.class, TeamFoundationServerRepositoryBrowser.class);
            load();
        }

        public String getTfExecutable() {
            if (tfExecutable == null) {
                return "tf";
            } else {
                return tfExecutable;
            }
        }
        
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            TeamFoundationServerScm scm = (TeamFoundationServerScm) super.newInstance(req, formData);
            scm.repositoryBrowser = RepositoryBrowsers.createInstance(TeamFoundationServerRepositoryBrowser.class,req,formData,"browser");
            return scm;
        }
        
        public void doExecutableCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.Executable(req, rsp).process();
        }

        private void doRegexCheck(final String[] regexArray, final String noMatchText, final String nullText,  
                StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req, rsp, false) {
                @Override
                protected void check() throws IOException, ServletException {
                    String value = fixEmpty(request.getParameter("value"));
                    if (value == null) {
                        if (nullText == null) {
                            ok();
                        } else {
                            error(nullText);
                        }
                        return;
                    }
                    for (String regex : regexArray) {
                        if (value.matches(regex)) {
                            ok();
                            return;
                        }
                    }
                    error(noMatchText);
                }
            }.process();
        }
        
        public void doUsernameCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            doRegexCheck(new String[]{DOMAIN_SLASH_USER_REGEX, USER_AT_DOMAIN_REGEX}, 
                    "Login name must contain the name of the domain and user", null, req, rsp );
        }
        
        public void doProjectPathCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            doRegexCheck(new String[]{PROJECT_PATH_REGEX}, 
                    "Project path must begin with '$/'.", 
                    "Project path is mandatory.", req, rsp );
        }
        
        public void doFieldCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            try {
                int hudsonMinorVersion = Integer.parseInt(Hudson.VERSION.substring(Hudson.VERSION.indexOf('.') + 1));
                if (hudsonMinorVersion >= 216) {
                    Hudson.getInstance().doFieldCheck(req, rsp);
                }
            } catch (NumberFormatException nfe) {
            }
        }
        
        @Override
        public boolean configure(StaplerRequest req) throws FormException {
            tfExecutable = Util.fixEmpty(req.getParameter("tfs.tfExecutable").trim());
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Team Foundation Server";
        }
    }
}
