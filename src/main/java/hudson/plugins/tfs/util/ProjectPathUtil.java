package hudson.plugins.tfs.util;

import java.util.HashMap;
import java.util.Map;

public class ProjectPathUtil
{
    public static String [] getProjectPaths(String projectPaths)
    {
        return getProjectMappings(projectPaths, "").keySet().toArray(new String[0]);
    }

    public static Map<String, String> getProjectMappings(String projectPaths, String localRoot)
    {
        Map<String, String> mappedPaths = new HashMap<String, String>();
        String [] splitProjectPaths = projectPaths.split("\\s*;\\s*");
        for(String pathSpec : splitProjectPaths)
        {
            String [] pathSpecParts = pathSpec.split("\\s*:\\s*");
            String serverPath = pathSpecParts[0];
            String localPath = localRoot + (pathSpecParts.length == 1 ? "" : ("\\" + pathSpecParts[1]));
            mappedPaths.put(serverPath, localPath);
        }
        return mappedPaths;
    }
}
