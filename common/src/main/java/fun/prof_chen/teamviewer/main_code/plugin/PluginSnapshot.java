package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Immutable view shared by the config UI and diagnostics. */
public record PluginSnapshot(
        String id,
        String name,
        String version,
        boolean builtIn,
        boolean enabled,
        boolean hotToggle,
        PluginRuntimeStatus runtimeStatus,
        String detail,
        Path source,
        Map<String, Object> settings,
        List<PluginManifest.SettingDefinition> settingDefinitions,
        List<IntegrationCapability> capabilities,
        boolean pendingRemoval) {
    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            List<IntegrationCapability> capabilities) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source,
                settings, settingDefinitions, capabilities, false);
    }

    public PluginSnapshot {
        settings = Map.copyOf(settings == null ? Map.of() : settings);
        settingDefinitions = List.copyOf(settingDefinitions == null ? List.of() : settingDefinitions);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
    }
}
