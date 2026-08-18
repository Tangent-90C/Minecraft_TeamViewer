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
        Map<String, PluginSettingState> settingStates,
        List<IntegrationCapability> capabilities,
        boolean pendingRemoval,
        String description,
        List<PluginRuntimeState> runtimeState,
        List<PluginRuntimeAction> runtimeActions) {
    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            Map<String, PluginSettingState> settingStates,
            List<IntegrationCapability> capabilities, boolean pendingRemoval, String description,
            List<PluginRuntimeState> runtimeState) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source, settings,
                settingDefinitions, settingStates, capabilities, pendingRemoval, description, runtimeState,
                List.of());
    }

    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            Map<String, PluginSettingState> settingStates,
            List<IntegrationCapability> capabilities, boolean pendingRemoval, String description) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source, settings,
                settingDefinitions, settingStates, capabilities, pendingRemoval, description, List.of(), List.of());
    }

    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            Map<String, PluginSettingState> settingStates,
            List<IntegrationCapability> capabilities, boolean pendingRemoval) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source, settings,
                settingDefinitions, settingStates, capabilities, pendingRemoval, "", List.of(), List.of());
    }

    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            List<IntegrationCapability> capabilities) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source,
                settings, settingDefinitions, Map.of(), capabilities, false, "", List.of(), List.of());
    }

    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            List<IntegrationCapability> capabilities, boolean pendingRemoval) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source,
                settings, settingDefinitions, Map.of(), capabilities, pendingRemoval, "", List.of(), List.of());
    }

    public PluginSnapshot(
            String id, String name, String version, boolean builtIn, boolean enabled, boolean hotToggle,
            PluginRuntimeStatus runtimeStatus, String detail, Path source, Map<String, Object> settings,
            List<PluginManifest.SettingDefinition> settingDefinitions,
            Map<String, PluginSettingState> settingStates,
            List<IntegrationCapability> capabilities) {
        this(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail, source,
                settings, settingDefinitions, settingStates, capabilities, false, "", List.of(), List.of());
    }

    public PluginSnapshot {
        settings = Map.copyOf(settings == null ? Map.of() : settings);
        settingDefinitions = List.copyOf(settingDefinitions == null ? List.of() : settingDefinitions);
        settingStates = Map.copyOf(settingStates == null ? Map.of() : settingStates);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        description = description == null ? "" : description.trim();
        runtimeState = List.copyOf(runtimeState == null ? List.of() : runtimeState);
        runtimeActions = List.copyOf(runtimeActions == null ? List.of() : runtimeActions);
    }

    public PluginSettingState settingState(String key) {
        return settingStates.getOrDefault(key, PluginSettingState.defaults());
    }

    public List<PluginManifest.SettingDefinition> visibleSettingDefinitions() {
        return settingDefinitions.stream().filter(value -> settingState(value.key()).visible()).toList();
    }

    public PluginSnapshot withRuntimeState(List<PluginRuntimeState> state) {
        return new PluginSnapshot(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail,
                source, settings, settingDefinitions, settingStates, capabilities, pendingRemoval,
                description, state, runtimeActions);
    }

    public PluginSnapshot withRuntimeActions(List<PluginRuntimeAction> actions) {
        return new PluginSnapshot(id, name, version, builtIn, enabled, hotToggle, runtimeStatus, detail,
                source, settings, settingDefinitions, settingStates, capabilities, pendingRemoval,
                description, runtimeState, actions);
    }
}
