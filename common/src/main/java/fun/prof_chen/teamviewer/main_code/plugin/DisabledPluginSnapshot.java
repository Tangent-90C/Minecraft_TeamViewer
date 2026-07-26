package fun.prof_chen.teamviewer.main_code.plugin;

import java.nio.file.Path;

/** Read-only inventory item for one recoverable plugin package in plugins-disabled. */
public record DisabledPluginSnapshot(
        String storageId,
        String pluginId,
        String name,
        String version,
        String originalFileName,
        boolean archive,
        long disabledAt,
        Path storagePath) { }
