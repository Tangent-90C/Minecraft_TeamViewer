package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable runtime inventory for every declared optional integration. Declarations survive
 * disablement and load failures; only the executable implementation is detached.
 */
public final class IntegrationRegistry {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> issues = new ArrayList<>();

    public synchronized void declare(
            String id, String role, String pluginId, String displayName,
            IntegrationSupportStatus status, String detail) {
        declare(id, role, pluginId, displayName, status, detail, List.of(), List.of(), List.of());
    }

    public synchronized void declare(
            String id, String role, String pluginId, String displayName,
            IntegrationSupportStatus status, String detail, List<String> requiredMods,
            List<String> targetLoaders, List<String> targetVersions) {
        String canonicalId = requireId(id);
        String normalizedRole = IntegrationRole.fromId(role).id();
        Entry existing = entries.get(canonicalId);
        if (existing != null) {
            if (!existing.role.equals(normalizedRole) || !existing.pluginId.equals(pluginId)) {
                issues.add("capability declaration mismatch for " + canonicalId);
            } else if (displayName != null && !displayName.isBlank()) {
                existing.displayName = displayName;
                existing.requiredMods = List.copyOf(requiredMods == null ? List.of() : requiredMods);
                existing.targetLoaders = List.copyOf(targetLoaders == null ? List.of() : targetLoaders);
                existing.targetVersions = List.copyOf(targetVersions == null ? List.of() : targetVersions);
            }
            return;
        }
        entries.put(canonicalId, new Entry(canonicalId, normalizedRole,
                Objects.requireNonNull(pluginId, "pluginId"),
                displayName == null || displayName.isBlank() ? canonicalId : displayName,
                Objects.requireNonNull(status, "status"), detail == null ? "" : detail,
                IntegrationImplementationSource.PLACEHOLDER, PluginRuntimeStatus.DISABLED, null,
                requiredMods, targetLoaders, targetVersions));
    }

    public synchronized void registerNative(IntegrationCapability capability, Object implementation) {
        Objects.requireNonNull(capability, "capability");
        String id = requireId(capability.id());
        declare(id, capability.role(), capability.pluginId(), capability.displayName(),
                capability.status(), capability.detail());
        Entry entry = entries.get(id);
        if (!entry.role.equals(capability.role())) {
            issues.add("native capability role mismatch for " + id);
            return;
        }
        entry.supportStatus = capability.status();
        entry.detail = defaultDetail(capability.status(), capability.detail());
        entry.source = implementation == null
                ? IntegrationImplementationSource.PLACEHOLDER : capability.implementationSource();
        entry.implementation = implementation;
        entry.nativeDeclaration = true;
    }

    public synchronized void registerPluginImplementation(
            String pluginId, String capabilityId, String role, Object implementation,
            IntegrationImplementationSource source) {
        String id = requireId(capabilityId);
        Entry entry = entries.get(id);
        if (entry == null) {
            issues.add("plugin " + pluginId + " registered undeclared capability " + id);
            throw new IllegalArgumentException("Undeclared capability: " + id);
        }
        if (!entry.pluginId.equals(pluginId) || !entry.role.equals(IntegrationRole.fromId(role).id())) {
            issues.add("plugin capability ownership mismatch for " + id);
            throw new IllegalArgumentException("Capability ownership mismatch: " + id);
        }
        entry.implementation = Objects.requireNonNull(implementation, "implementation");
        entry.source = Objects.requireNonNull(source, "source");
        entry.supportStatus = IntegrationSupportStatus.AVAILABLE;
        entry.detail = "";
    }

    public synchronized void registerPluginUnavailable(
            String pluginId, String capabilityId, String role,
            IntegrationSupportStatus status, String detail) {
        if (status == IntegrationSupportStatus.AVAILABLE) {
            throw new IllegalArgumentException("Unavailable capability cannot claim AVAILABLE: " + capabilityId);
        }
        String id = requireId(capabilityId);
        Entry entry = entries.get(id);
        if (entry == null || !entry.pluginId.equals(pluginId)
                || !entry.role.equals(IntegrationRole.fromId(role).id())) {
            throw new IllegalArgumentException("Capability ownership mismatch: " + id);
        }
        entry.implementation = null;
        entry.source = IntegrationImplementationSource.PLACEHOLDER;
        entry.supportStatus = Objects.requireNonNull(status, "status");
        entry.detail = defaultDetail(status, detail);
    }

    public synchronized void bindNativeImplementation(
            String pluginId, String capabilityId, String role, String nativeCapabilityId) {
        String targetId = requireId(capabilityId);
        String sourceId = requireId(nativeCapabilityId);
        Entry target = entries.get(targetId);
        Entry sourceEntry = entries.get(sourceId);
        if (target == null) throw new IllegalArgumentException("Undeclared capability: " + targetId);
        if (sourceEntry == null) throw new IllegalArgumentException("Unknown native capability: " + sourceId);
        if (!target.pluginId.equals(pluginId) || !target.role.equals(IntegrationRole.fromId(role).id())) {
            throw new IllegalArgumentException("Capability ownership mismatch: " + targetId);
        }
        if (!target.role.equals(sourceEntry.role)) {
            throw new IllegalArgumentException("Native capability role mismatch: " + sourceId);
        }
        target.implementation = sourceEntry.implementation;
        target.source = sourceEntry.source;
        target.supportStatus = sourceEntry.supportStatus;
        target.detail = sourceEntry.detail;
    }

    public synchronized void setPluginRuntime(String pluginId, PluginRuntimeStatus status, String detail) {
        for (Entry entry : entries.values()) {
            if (!entry.pluginId.equals(pluginId)) continue;
            entry.runtimeStatus = status;
            if (status != PluginRuntimeStatus.PENDING_RESTART) {
                entry.runtimeAttached = status == PluginRuntimeStatus.ACTIVE;
            }
            if (detail != null && !detail.isBlank()
                    && (status == PluginRuntimeStatus.LOAD_FAILED || status == PluginRuntimeStatus.INCOMPATIBLE
                    || status == PluginRuntimeStatus.SUSPENDED)) {
                entry.detail = detail;
                if (status == PluginRuntimeStatus.LOAD_FAILED) {
                    entry.supportStatus = IntegrationSupportStatus.FAILED;
                }
            }
        }
    }

    public synchronized void detachPluginImplementations(String pluginId) {
        for (Entry entry : entries.values()) {
            if (!entry.pluginId.equals(pluginId)) continue;
            // A built-in capability is first predeclared by the platform matrix and is therefore
            // marked nativeDeclaration. Once Lua replaces that placeholder, a failed staged load
            // must still remove the partial Lua wrapper rather than leave a failed runtime behind.
            if (!entry.nativeDeclaration || entry.source == IntegrationImplementationSource.LUA) {
                entry.implementation = null;
                entry.source = IntegrationImplementationSource.PLACEHOLDER;
            }
        }
    }

    public synchronized Object implementation(String id) {
        Entry entry = entries.get(IntegrationIds.canonicalize(id));
        return entry == null ? null : entry.implementation;
    }

    public synchronized boolean hasImplementation(String id) {
        Entry entry = entries.get(IntegrationIds.canonicalize(id));
        return entry != null && entry.implementation != null;
    }

    public synchronized void setCapabilitySupport(
            String id, IntegrationSupportStatus status, String detail) {
        Entry entry = entries.get(IntegrationIds.canonicalize(id));
        if (entry == null) return;
        entry.supportStatus = Objects.requireNonNull(status, "status");
        entry.detail = detail == null ? "" : detail;
        if (status != IntegrationSupportStatus.AVAILABLE && entry.source == IntegrationImplementationSource.LUA) {
            entry.implementation = null;
            entry.source = IntegrationImplementationSource.PLACEHOLDER;
        }
    }

    public synchronized List<IntegrationCapability> capabilitiesForPlugin(String pluginId) {
        entries.values().forEach(this::refreshSupport);
        return entries.values().stream()
                .filter(entry -> entry.pluginId.equals(pluginId))
                .sorted(Comparator.comparing(entry -> entry.id))
                .map(Entry::capability)
                .toList();
    }

    public synchronized List<RemotePlayerProjection> activeRemotePlayerProjections() {
        return activeImplementations(IntegrationRole.REMOTE_PLAYER, RemotePlayerProjection.class);
    }

    public synchronized List<SharedWaypointMapAdapter> activeSharedWaypointAdapters() {
        return activeImplementations(IntegrationRole.SHARED_WAYPOINT, SharedWaypointMapAdapter.class);
    }

    public synchronized List<BattleMapSource> activeBattleMapSources() {
        return activeImplementations(IntegrationRole.BATTLE_MAP_SOURCE, BattleMapSource.class);
    }

    public synchronized BattleMapSource activeBattleMapSource(String id) {
        Entry entry = entries.get(IntegrationIds.canonicalize(id));
        if (entry != null) refreshSupport(entry);
        if (entry == null || !entry.runtimeAttached
                || entry.supportStatus != IntegrationSupportStatus.AVAILABLE
                || !(entry.implementation instanceof BattleMapSource source)) return null;
        return source;
    }

    public synchronized List<IntegrationCapability> capabilities() {
        entries.values().forEach(this::refreshSupport);
        return entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.id))
                .map(Entry::capability)
                .toList();
    }

    public synchronized List<String> issues() {
        entries.values().forEach(this::refreshSupport);
        List<String> result = new ArrayList<>(issues);
        for (Entry entry : entries.values()) {
            if (entry.supportStatus == IntegrationSupportStatus.AVAILABLE && entry.implementation == null) {
                result.add("available capability has no implementation: " + entry.id);
            }
        }
        return List.copyOf(result);
    }

    private <T> List<T> activeImplementations(IntegrationRole role, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            refreshSupport(entry);
            if (!entry.role.equals(role.id()) || !entry.runtimeAttached
                    || entry.supportStatus != IntegrationSupportStatus.AVAILABLE
                    || !type.isInstance(entry.implementation)) continue;
            result.add(type.cast(entry.implementation));
        }
        return List.copyOf(result);
    }

    private void refreshSupport(Entry entry) {
        if (entry.runtimeStatus == PluginRuntimeStatus.LOAD_FAILED
                || entry.runtimeStatus == PluginRuntimeStatus.INCOMPATIBLE) return;
        if (entry.implementation == null) return;
        if (entry.implementation instanceof RemotePlayerProjection projection) {
            entry.supportStatus = projection.supportStatus();
            entry.detail = defaultDetail(entry.supportStatus, projection.supportDetail());
        } else if (entry.implementation instanceof SharedWaypointMapAdapter adapter) {
            entry.supportStatus = adapter.supportStatus();
            entry.detail = defaultDetail(entry.supportStatus, adapter.supportDetail());
        } else if (entry.implementation instanceof BattleMapSource source) {
            entry.supportStatus = source.supportStatus();
            entry.detail = defaultDetail(entry.supportStatus, source.supportDetail());
        }
    }

    private static String requireId(String id) {
        String value = IntegrationIds.canonicalize(Objects.requireNonNull(id, "id"));
        if (value.isBlank()) throw new IllegalArgumentException("capability id must not be blank");
        return value;
    }

    private static String defaultDetail(IntegrationSupportStatus status, String detail) {
        if (detail != null && !detail.isBlank()) return detail;
        return switch (status) {
            case AVAILABLE -> "";
            case MOD_NOT_INSTALLED -> "Required external mod is not installed";
            case UNSUPPORTED_VERSION -> "Integration does not support this runtime version";
            case NOT_IMPLEMENTED -> "Integration is not implemented by this platform adapter";
            case ENTRYPOINT_NOT_READY -> "External mod entrypoint is not ready";
            case FAILED -> "Integration initialization failed";
        };
    }

    private static final class Entry {
        private final String id;
        private final String role;
        private final String pluginId;
        private String displayName;
        private IntegrationSupportStatus supportStatus;
        private String detail;
        private IntegrationImplementationSource source;
        private PluginRuntimeStatus runtimeStatus;
        private Object implementation;
        private boolean nativeDeclaration;
        private boolean runtimeAttached;
        private List<String> requiredMods;
        private List<String> targetLoaders;
        private List<String> targetVersions;

        private Entry(String id, String role, String pluginId, String displayName,
                      IntegrationSupportStatus supportStatus, String detail,
                      IntegrationImplementationSource source, PluginRuntimeStatus runtimeStatus,
                      Object implementation, List<String> requiredMods,
                      List<String> targetLoaders, List<String> targetVersions) {
            this.id = id;
            this.role = role;
            this.pluginId = pluginId;
            this.displayName = displayName;
            this.supportStatus = supportStatus;
            this.detail = detail;
            this.source = source;
            this.runtimeStatus = runtimeStatus;
            this.implementation = implementation;
            this.requiredMods = List.copyOf(requiredMods == null ? List.of() : requiredMods);
            this.targetLoaders = List.copyOf(targetLoaders == null ? List.of() : targetLoaders);
            this.targetVersions = List.copyOf(targetVersions == null ? List.of() : targetVersions);
        }

        private IntegrationCapability capability() {
            return new IntegrationCapability(
                    id, role, supportStatus, detail, pluginId, source, runtimeStatus, displayName,
                    requiredMods, targetLoaders, targetVersions);
        }
    }
}
