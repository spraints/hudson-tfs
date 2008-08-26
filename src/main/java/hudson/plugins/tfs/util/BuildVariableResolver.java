package hudson.plugins.tfs.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.util.VariableResolver;

/**
 * A {@link VariableResolver} that resolves certain Build variables.
 * <p>
 * The build variable resolver will resolve the following:
 * <ul>
 * <li> JOB_NAME - The name of the job</li>
 * <li> USER_NAME - The system property "user.name" on the Node that the Launcher
 * is being executed on (slave or master)</li>
 * <li> NODE_NAME - The name of the node that the Launcher is being executed on</li>
 * <li> Any environment variable that is set on the Node that the Launcher is
 * being executed on (slave or master)</li> 
 * </ul> 
 * 
 * @author Erik Ramfelt
 */
public class BuildVariableResolver implements VariableResolver<String> {
    
    private Map<String,LazyResolver> lazyResolvers = new HashMap<String, LazyResolver>();
    
    private final Launcher launcher;

    private static final Logger LOGGER = Logger.getLogger(BuildVariableResolver.class.getName());
    
    public BuildVariableResolver(final AbstractProject<?, ?> project, final Launcher launcher) {
        this.launcher = launcher;
        
        lazyResolvers.put("JOB_NAME", new LazyResolver() {
            public String getValue() {
                return project.getName();
            }            
        });
        lazyResolvers.put("NODE_NAME", new LazyComputerResolver() {
            public String getValue(Computer computer) {
                return (computer.getName() == null ? "MASTER" : computer.getName());
            }            
        });
        lazyResolvers.put("USER_NAME", new LazyComputerResolver() {
            public String getValue(Computer computer) throws IOException, InterruptedException {
                return (String) computer.getSystemProperties().get("user.name");
            }            
        });
    }

    /**
     * Constructor that can be used with a {@linkplain AbstractBuild} instance. 
     * <p>
     * This constructor should not be called in a method that may be called by
     * {@link AbstractBuild#getEnvVars()}.  
     * @param build used to get the project and the build env vars
     * @param launcher launcher used to get the computer
     */
    public BuildVariableResolver(final AbstractBuild<?, ?> build, final Launcher launcher) {
        this(build.getProject(), launcher);
        
        final Map<String, String> envVars = build.getEnvVars();
        if (envVars != null) {
            for (final String buildEnvVar : envVars.keySet()) {
                lazyResolvers.put(buildEnvVar, new LazyResolver() {
                    public String getValue() {
                        return envVars.get(buildEnvVar);
                    }
                });
            }
        }
    }
    
    public String resolve(String variable) {
        try {
            if (lazyResolvers.containsKey(variable)) {
                return lazyResolvers.get(variable).getValue();
            } else {
                if (launcher.getComputer() != null) {
                    return launcher.getComputer().getEnvVars().get(variable);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Variable name '" + variable + "' look up failed because of " + e);
        }
        return null;
    }

    /**
     * Simple lazy variable resolver
     */
    private interface LazyResolver {
        String getValue() throws IOException, InterruptedException;
    }
    
    /**
     * Class to handle cases when a Launcher was not created from a computer.
     * @see Launcher#getComputer()
     */
    private abstract class LazyComputerResolver implements LazyResolver {
        protected abstract String getValue(Computer computer) throws IOException, InterruptedException;
        public String getValue() throws IOException, InterruptedException {
            if (launcher.getComputer() == null) {
                return null;
            } else {
                return getValue(launcher.getComputer());
            }
        }
    }
}
