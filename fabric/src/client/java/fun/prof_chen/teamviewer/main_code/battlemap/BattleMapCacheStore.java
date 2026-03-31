package fun.prof_chen.teamviewer.main_code.battlemap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BattleMapCacheStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_STATE_TYPE = new TypeToken<FileState>() {
    }.getType();

    private final Path storagePath;
    private final Map<String, CacheEntry> entries = new HashMap<>();

    public BattleMapCacheStore(Path storagePath) {
        this.storagePath = storagePath;
    }

    public void load() {
        entries.clear();
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }

        try {
            String content = Files.readString(storagePath);
            FileState fileState = GSON.fromJson(content, FILE_STATE_TYPE);
            if (fileState == null || fileState.entries == null) {
                return;
            }
            entries.putAll(fileState.entries);
        } catch (Exception ignored) {
        }
    }

    public void save() {
        if (storagePath == null) {
            return;
        }

        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileState fileState = new FileState();
            fileState.entries = new HashMap<>(entries);
            Files.writeString(storagePath, GSON.toJson(fileState));
        } catch (IOException ignored) {
        }
    }

    public CacheEntry get(String chunkId) {
        return entries.get(chunkId);
    }

    public void put(String chunkId, CacheEntry entry) {
        if (chunkId == null || chunkId.isBlank() || entry == null) {
            return;
        }
        entries.put(chunkId, entry);
    }

    public void remove(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            return;
        }
        entries.remove(chunkId);
    }

    public boolean pruneOlderThan(long cutoffMs) {
        int beforeSize = entries.size();
        entries.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().lastConfirmedAt < cutoffMs);
        return beforeSize != entries.size();
    }

    public Map<String, CacheEntry> snapshot() {
        return Collections.unmodifiableMap(entries);
    }

    public void clear() {
        entries.clear();
    }

    public static final class CacheEntry {
        private String stateHash;
        private long lastConfirmedAt;
        private long lastHeartbeatAt;

        public CacheEntry() {
        }

        public CacheEntry(String stateHash, long lastConfirmedAt, long lastHeartbeatAt) {
            this.stateHash = stateHash;
            this.lastConfirmedAt = lastConfirmedAt;
            this.lastHeartbeatAt = lastHeartbeatAt;
        }

        public String getStateHash() {
            return stateHash;
        }

        public long getLastConfirmedAt() {
            return lastConfirmedAt;
        }

        public long getLastHeartbeatAt() {
            return lastHeartbeatAt;
        }
    }

    private static final class FileState {
        private Map<String, CacheEntry> entries = new HashMap<>();
    }
}
