package slanglsp;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;

import static java.util.List.of;

public class SlangUtils
{
    static Path getPluginDir()
    {
        String jarPath = PathManager.getJarPathForClass(SlangUtils.class);
        if(jarPath == null)
            throw new RuntimeException("Invalid 'getJarPathForClass'");
        return Paths.get(jarPath).getParent().toAbsolutePath();
    }
    static Path getVersionCacheLocation()
    {
        return slanglsp.SlangUtils.getPluginDir().resolve("versionCache.txt");
    }
    static File getVersionCacheFile()
    {
        File newFile = getVersionCacheLocation().toFile();
        return newFile;
    }

    static SlangVersion getVersion()
    {
        var fileStream = SlangUtils.class.getClassLoader().getResourceAsStream("version.txt");
        if(fileStream == null)
        {
            System.out.println("Missing the resource version.txt");
            return new SlangVersion("0.0");
        }
        return new SlangVersion(fileStream);
    }
}
