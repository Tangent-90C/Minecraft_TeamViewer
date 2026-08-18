package fun.prof_chen.teamviewer.main_code.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PluginStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Entry>>() { }.getType();
    private final Path path;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    PluginStateStore(Path path) {
        this.path = path;
        load();
    }

    synchronized boolean enabled(PluginManifest manifest) {
        Entry entry = entries.get(manifest.id());
        return entry == null ? manifest.defaultEnabled() : entry.enabled;
    }

    synchronized boolean contains(String pluginId) {
        return entries.containsKey(pluginId);
    }

    synchronized Map<String, Object> settings(PluginManifest manifest) {
        Entry entry = entries.computeIfAbsent(manifest.id(), ignored -> new Entry(
                manifest.defaultEnabled(), new LinkedHashMap<>(), new LinkedHashMap<>()));
        entry.normalize();
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (PluginManifest.SettingDefinition definition : manifest.settings()) {
            Object value = definition.normalize(entry.settings.get(definition.key()));
            normalized.put(definition.key(), value);
            entry.settings.put(definition.key(), value);
        }
        return normalized;
    }

    synchronized void setEnabled(String pluginId, boolean enabled) {
        entries.computeIfAbsent(pluginId, ignored -> new Entry(
                enabled, new LinkedHashMap<>(), new LinkedHashMap<>())).enabled = enabled;
        save();
    }

    synchronized void setSetting(String pluginId, String key, Object value) {
        entries.computeIfAbsent(pluginId, ignored -> new Entry(
                true, new LinkedHashMap<>(), new LinkedHashMap<>())).settings.put(key, value);
        save();
    }

    synchronized Map<String, Object> data(String pluginId) {
        Entry entry = entries.get(pluginId);
        if (entry == null) return Map.of();
        entry.normalize();
        return deepCopy(entry.data);
    }

    synchronized void setData(String pluginId, Map<String, Object> value) {
        Entry entry = entries.computeIfAbsent(pluginId, ignored -> new Entry(
                true, new LinkedHashMap<>(), new LinkedHashMap<>()));
        entry.normalize();
        entry.data = new LinkedHashMap<>(deepCopy(value));
        save();
    }

    synchronized void migrateBooleanOr(
            String pluginId, String targetKey, List<String> sourceKeys, boolean defaultValue) {
        Entry entry = entries.computeIfAbsent(pluginId,
                ignored -> new Entry(true, new LinkedHashMap<>(), new LinkedHashMap<>()));
        entry.normalize();
        if (entry.settings.containsKey(targetKey)) return;
        boolean found = false;
        boolean result = false;
        for (String sourceKey : sourceKeys) {
            if (!entry.settings.containsKey(sourceKey)) continue;
            found = true;
            Object raw = entry.settings.get(sourceKey);
            result |= raw instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(raw));
        }
        entry.settings.put(targetKey, found ? result : defaultValue);
        save();
    }

    private void load() {
        if (!Files.isRegularFile(path)) return;
        try {
            Map<String, Entry> loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), TYPE);
            if (loaded != null) entries.putAll(loaded);
        } catch (Exception ignored) {
            entries.clear();
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(entries, TYPE), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // Plugin persistence errors are exposed by the manager on the next operation.
        }
    }

    private static final class Entry {
        private boolean enabled;
        private Map<String, Object> settings;
        private Map<String, Object> data;

        private Entry(boolean enabled, Map<String, Object> settings, Map<String, Object> data) {
            this.enabled = enabled;
            this.settings = settings == null ? new LinkedHashMap<>() : settings;
            this.data = data == null ? new LinkedHashMap<>() : data;
        }

        private void normalize() {
            if (settings == null) settings = new LinkedHashMap<>();
            if (data == null) data = new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        Map<String, Object> copy = GSON.fromJson(GSON.toJson(value), Map.class);
        return copy == null ? Map.of() : copy;
    }
}
