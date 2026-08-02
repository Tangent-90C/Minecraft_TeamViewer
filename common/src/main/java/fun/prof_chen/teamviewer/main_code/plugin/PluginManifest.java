package fun.prof_chen.teamviewer.main_code.plugin;

import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** JSON manifest for built-in and local integration plugins. */
public record PluginManifest(
        int schemaVersion,
        String apiVersion,
        String id,
        String name,
        String version,
        String entry,
        boolean defaultEnabled,
        String hotToggle,
        List<String> loaders,
        List<String> minecraftVersions,
        List<String> requiredMods,
        List<String> optionalMods,
        List<String> dependencies,
        List<CapabilityDeclaration> provides,
        List<SettingDefinition> settings,
        List<EntrypointDefinition> entrypoints,
        String documentation) {

    public PluginManifest normalized() {
        String normalizedId = requireToken(id, "plugin id");
        String normalizedEntry = entry == null || entry.isBlank() ? "main.lua" : entry.trim();
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported plugin schemaVersion: " + schemaVersion);
        if (!"1".equals(apiVersion)) throw new IllegalArgumentException("Unsupported plugin apiVersion: " + apiVersion);
        if (normalizedEntry.startsWith("/") || normalizedEntry.contains("..") || normalizedEntry.contains("\\")) {
            throw new IllegalArgumentException("Unsafe plugin entry path: " + normalizedEntry);
        }
        List<CapabilityDeclaration> normalizedProvides = safe(provides).stream()
                .map(CapabilityDeclaration::normalized).toList();
        if (normalizedProvides.isEmpty()) throw new IllegalArgumentException("Plugin must declare at least one capability");
        Set<String> ids = new HashSet<>();
        for (CapabilityDeclaration capability : normalizedProvides) {
            if (!ids.add(capability.id())) throw new IllegalArgumentException("Duplicate capability id: " + capability.id());
        }
        List<SettingDefinition> normalizedSettings = safe(settings).stream().map(SettingDefinition::normalized).toList();
        Set<String> settingKeys = new HashSet<>();
        for (SettingDefinition setting : normalizedSettings) {
            if (!settingKeys.add(setting.key())) throw new IllegalArgumentException("Duplicate setting key: " + setting.key());
        }
        List<EntrypointDefinition> normalizedEntrypoints = safe(entrypoints).stream()
                .map(EntrypointDefinition::normalized).toList();
        String normalizedDocumentation = documentation == null || documentation.isBlank()
                ? null : requireSafeResource(documentation, "documentation");
        return new PluginManifest(1, "1", normalizedId,
                name == null || name.isBlank() ? normalizedId : name.trim(),
                version == null || version.isBlank() ? "0.0.0" : version.trim(), normalizedEntry,
                defaultEnabled, normalizeHotToggle(hotToggle), lower(loaders), safeStrings(minecraftVersions),
                lower(requiredMods), lower(optionalMods), safeStrings(dependencies),
                normalizedProvides, normalizedSettings, normalizedEntrypoints, normalizedDocumentation);
    }

    public boolean supports(String loader, String minecraftVersion) {
        return (loaders().isEmpty() || loaders().contains(loader.toLowerCase(Locale.ROOT)))
                && (minecraftVersions().isEmpty() || minecraftVersions().contains(minecraftVersion))
                && selectedEntrypoint(loader, minecraftVersion) != null;
    }

    public String selectedEntrypoint(String loader, String minecraftVersion) {
        if (entrypoints.isEmpty()) return entry;
        String normalizedLoader = Objects.requireNonNullElse(loader, "unknown").toLowerCase(Locale.ROOT);
        String normalizedVersion = Objects.requireNonNullElse(minecraftVersion, "unknown");
        List<EntrypointDefinition> matches = entrypoints.stream()
                .filter(value -> value.matches(normalizedLoader, normalizedVersion)).toList();
        if (matches.isEmpty()) return null;
        int priority = matches.stream().mapToInt(EntrypointDefinition::priority).max().orElse(0);
        List<EntrypointDefinition> preferred = matches.stream()
                .filter(value -> value.priority() == priority).toList();
        if (preferred.size() != 1) {
            throw new IllegalArgumentException("Multiple plugin entrypoints match at priority " + priority
                    + ": " + preferred.stream().map(EntrypointDefinition::entry).toList());
        }
        return preferred.get(0).entry();
    }

    public List<String> resourcePaths() {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        // Keep the legacy entry in the package as well. It is ignored for runtime selection when
        // entrypoints[] is present, but retaining it makes a copied tutorial self-contained if an
        // author later removes the multi-entry declaration.
        paths.add(entry);
        entrypoints.forEach(value -> paths.add(value.entry()));
        if (documentation != null) paths.add(documentation);
        return List.copyOf(paths);
    }

    public boolean managedHotToggle() {
        return "managed".equals(hotToggle) || "author-managed".equals(hotToggle);
    }

    private static String normalizeHotToggle(String value) {
        String normalized = value == null ? "restart" : value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("restart", "managed", "author-managed").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported hotToggle mode: " + value);
        }
        return normalized;
    }

    private static String requireToken(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (!normalized.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid " + label + ": " + value);
        return normalized;
    }

    private static String requireSafeResource(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim().replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("../")
                || normalized.equals("..")) {
            throw new IllegalArgumentException("Unsafe plugin " + label + " path: " + value);
        }
        return normalized;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static List<String> safeStrings(List<String> values) {
        return safe(values).stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static List<String> lower(List<String> values) {
        return safeStrings(values).stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    public record CapabilityDeclaration(String id, String role, String name) {
        private CapabilityDeclaration normalized() {
            String capabilityId = IntegrationIds.canonicalize(requireToken(id, "capability id"));
            String normalizedRole = IntegrationRole.fromId(role).id();
            return new CapabilityDeclaration(capabilityId, normalizedRole,
                    name == null || name.isBlank() ? capabilityId : name.trim());
        }
    }

    public record EntrypointDefinition(
            String entry, List<String> loaders, List<String> minecraftVersions, int priority) {
        private EntrypointDefinition normalized() {
            return new EntrypointDefinition(requireSafeResource(entry, "entrypoint"),
                    lower(loaders), safeStrings(minecraftVersions), priority);
        }

        private boolean matches(String loader, String minecraftVersion) {
            return (loaders.isEmpty() || loaders.contains(loader))
                    && (minecraftVersions.isEmpty() || minecraftVersions.contains(minecraftVersion));
        }
    }

    public record SettingDefinition(
            String key, String type, String name, Object defaultValue,
            Double min, Double max, List<String> options, boolean restartRequired) {
        private SettingDefinition normalized() {
            String normalizedKey = requireToken(key, "setting key");
            String normalizedType = Objects.requireNonNullElse(type, "string").toLowerCase(Locale.ROOT);
            if (!Set.of("boolean", "integer", "number", "string", "enum", "color").contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported setting type: " + normalizedType);
            }
            List<String> normalizedOptions = safeStrings(options);
            if ("enum".equals(normalizedType) && normalizedOptions.isEmpty()) {
                throw new IllegalArgumentException("Enum setting requires options: " + normalizedKey);
            }
            return new SettingDefinition(normalizedKey, normalizedType,
                    name == null || name.isBlank() ? normalizedKey : name.trim(),
                    normalizeValue(normalizedType, defaultValue, normalizedOptions), min, max,
                    normalizedOptions, restartRequired);
        }

        public Object normalize(Object value) {
            return normalizeValue(type, value == null ? defaultValue : value, options);
        }

        private static Object normalizeValue(String type, Object value, List<String> options) {
            return switch (type) {
                case "boolean" -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
                case "integer" -> value instanceof Number number ? number.intValue() : parseInt(value, 0);
                case "number" -> value instanceof Number number ? number.doubleValue() : parseDouble(value, 0D);
                case "color" -> normalizeColor(value);
                case "enum" -> options.contains(String.valueOf(value)) ? String.valueOf(value) : options.get(0);
                default -> value == null ? "" : String.valueOf(value);
            };
        }

        private static int parseInt(Object value, int fallback) {
            try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
        }

        private static double parseDouble(Object value, double fallback) {
            try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
        }

        private static String normalizeColor(Object value) {
            String raw = value == null ? "#FFFFFF" : String.valueOf(value).trim();
            if (!raw.startsWith("#")) raw = "#" + raw;
            return raw.matches("#[0-9a-fA-F]{6,8}") ? raw.toUpperCase(Locale.ROOT) : "#FFFFFF";
        }
    }
}
