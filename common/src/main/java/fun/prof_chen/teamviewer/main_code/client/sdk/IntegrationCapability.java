package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.Objects;
import java.util.List;

/** Machine-readable support report for one optional adapter port. */
public record IntegrationCapability(
        String id,
        String role,
        IntegrationSupportStatus status,
        String detail,
        String pluginId,
        IntegrationImplementationSource implementationSource,
        PluginRuntimeStatus runtimeStatus,
        String displayName,
        List<String> requiredMods,
        List<String> targetLoaders,
        List<String> targetVersions) {
    public IntegrationCapability(String id, String role, IntegrationSupportStatus status, String detail) {
        this(id, role, status, detail, IntegrationIds.pluginIdForCapability(id),
                IntegrationImplementationSource.JAVA_NATIVE, PluginRuntimeStatus.ACTIVE, id,
                List.of(), List.of(), List.of());
    }

    public IntegrationCapability(
            String id, String role, IntegrationSupportStatus status, String detail,
            String pluginId, IntegrationImplementationSource implementationSource,
            PluginRuntimeStatus runtimeStatus) {
        this(id, role, status, detail, pluginId, implementationSource, runtimeStatus, id,
                List.of(), List.of(), List.of());
    }

    public IntegrationCapability(
            String id, String role, IntegrationSupportStatus status, String detail,
            String pluginId, IntegrationImplementationSource implementationSource,
            PluginRuntimeStatus runtimeStatus, String displayName) {
        this(id, role, status, detail, pluginId, implementationSource, runtimeStatus, displayName,
                List.of(), List.of(), List.of());
    }

    public IntegrationCapability {
        id = IntegrationIds.canonicalize(Objects.requireNonNull(id, "id"));
        role = Objects.requireNonNull(role, "role").trim();
        status = Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
        pluginId = Objects.requireNonNullElse(pluginId, IntegrationIds.pluginIdForCapability(id));
        implementationSource = Objects.requireNonNullElse(implementationSource,
                IntegrationImplementationSource.PLACEHOLDER);
        runtimeStatus = Objects.requireNonNullElse(runtimeStatus, PluginRuntimeStatus.DISABLED);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        requiredMods = List.copyOf(requiredMods == null ? List.of() : requiredMods);
        targetLoaders = List.copyOf(targetLoaders == null ? List.of() : targetLoaders);
        targetVersions = List.copyOf(targetVersions == null ? List.of() : targetVersions);
    }

    public IntegrationCapability withRuntimeStatus(PluginRuntimeStatus value) {
        return new IntegrationCapability(id, role, status, detail, pluginId, implementationSource, value,
                displayName, requiredMods, targetLoaders, targetVersions);
    }
}
