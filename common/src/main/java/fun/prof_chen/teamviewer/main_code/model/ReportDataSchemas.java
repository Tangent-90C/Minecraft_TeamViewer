package fun.prof_chen.teamviewer.main_code.model;

import java.util.HashMap;
import java.util.Map;

public final class ReportDataSchemas {
    private ReportDataSchemas() {
    }

    public static final Map<String, Boolean> PLAYER_DATA_RELIABILITY = Map.ofEntries(
            Map.entry("x", false),
            Map.entry("y", false),
            Map.entry("z", false),
            Map.entry("vx", false),
            Map.entry("vy", false),
            Map.entry("vz", false),
            Map.entry("dimension", true),
            Map.entry("playerName", true),
            Map.entry("playerUUID", true),
            Map.entry("health", true),
            Map.entry("maxHealth", true),
            Map.entry("armor", true),
            Map.entry("isRiding", true),
            Map.entry("width", true),
            Map.entry("height", true));

    public static final Map<String, Boolean> ENTITY_DATA_RELIABILITY = Map.ofEntries(
            Map.entry("x", false),
            Map.entry("y", false),
            Map.entry("z", false),
            Map.entry("vx", false),
            Map.entry("vy", false),
            Map.entry("vz", false),
            Map.entry("dimension", true),
            Map.entry("entityType", true),
            Map.entry("entityName", true),
            Map.entry("width", true),
            Map.entry("height", true));

    public static final Map<String, Boolean> WAYPOINT_DATA_RELIABILITY = Map.ofEntries(
            Map.entry("x", false),
            Map.entry("y", false),
            Map.entry("z", false),
            Map.entry("dimension", true),
            Map.entry("name", true),
            Map.entry("symbol", true),
            Map.entry("color", true),
            Map.entry("ownerId", true),
            Map.entry("ownerName", true),
            Map.entry("createdAt", true),
            Map.entry("ttlSeconds", true),
            Map.entry("waypointKind", true),
            Map.entry("replaceOldQuick", true),
            Map.entry("maxQuickMarks", true),
            Map.entry("targetType", true),
            Map.entry("targetEntityId", true),
            Map.entry("targetEntityType", true),
            Map.entry("targetEntityName", true),
            Map.entry("roomCode", true),
            Map.entry("permanent", true),
            Map.entry("tacticalType", true),
            Map.entry("sourceType", true));

    public static final Map<String, Boolean> BATTLE_CHUNK_DATA_RELIABILITY = Map.ofEntries(
            Map.entry("chunkX", true),
            Map.entry("chunkZ", true),
            Map.entry("dimension", true),
            Map.entry("symbol", true),
            Map.entry("colorRaw", true),
            Map.entry("colorNote", true),
            Map.entry("observedAt", true),
            Map.entry("positionSampledAt", true),
            Map.entry("alignmentSource", true),
            Map.entry("reporterId", true),
            Map.entry("roomCode", true));

    public record PlayerDataPayload(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            String dimension,
            String playerName,
            String playerUUID,
            float health,
            float maxHealth,
            float armor,
            boolean isRiding,
            float width,
            float height) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("x", x);
            data.put("y", y);
            data.put("z", z);
            data.put("vx", vx);
            data.put("vy", vy);
            data.put("vz", vz);
            data.put("dimension", dimension);
            data.put("playerName", playerName);
            data.put("playerUUID", playerUUID);
            data.put("health", health);
            data.put("maxHealth", maxHealth);
            data.put("armor", armor);
            data.put("isRiding", isRiding);
            data.put("width", width);
            data.put("height", height);
            return data;
        }
    }

    public record EntityDataPayload(
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            String dimension,
            String entityType,
            String entityName,
            float width,
            float height) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("x", x);
            data.put("y", y);
            data.put("z", z);
            data.put("vx", vx);
            data.put("vy", vy);
            data.put("vz", vz);
            data.put("dimension", dimension);
            data.put("entityType", entityType);
            data.put("entityName", entityName);
            data.put("width", width);
            data.put("height", height);
            return data;
        }
    }

    public record WaypointDataPayload(
            int x,
            int y,
            int z,
            String dimension,
            String name,
            String symbol,
            int color,
            String ownerId,
            String ownerName,
            long createdAt,
            Integer ttlSeconds,
            String waypointKind,
            Boolean replaceOldQuick,
            Integer maxQuickMarks,
            String targetType,
            String targetEntityId,
            String targetEntityType,
            String targetEntityName,
            String roomCode,
            Boolean permanent,
            String tacticalType,
            String sourceType) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("x", x);
            data.put("y", y);
            data.put("z", z);
            data.put("dimension", dimension);
            data.put("name", name);
            data.put("symbol", symbol);
            data.put("color", color);
            data.put("ownerId", ownerId);
            data.put("ownerName", ownerName);
            data.put("createdAt", createdAt);
            data.put("ttlSeconds", ttlSeconds);
            data.put("waypointKind", waypointKind);
            data.put("replaceOldQuick", replaceOldQuick);
            data.put("maxQuickMarks", maxQuickMarks);
            data.put("targetType", targetType);
            data.put("targetEntityId", targetEntityId);
            data.put("targetEntityType", targetEntityType);
            data.put("targetEntityName", targetEntityName);
            data.put("roomCode", roomCode);
            data.put("permanent", permanent);
            data.put("tacticalType", tacticalType);
            data.put("sourceType", sourceType);
            return data;
        }
    }

    public record BattleChunkDataPayload(
            int chunkX,
            int chunkZ,
            String dimension,
            String symbol,
            String colorRaw,
            String colorNote,
            long observedAt,
            Long positionSampledAt,
            String alignmentSource,
            String reporterId,
            String roomCode) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("chunkX", chunkX);
            data.put("chunkZ", chunkZ);
            data.put("dimension", dimension);
            data.put("symbol", symbol);
            data.put("colorRaw", colorRaw);
            data.put("colorNote", colorNote);
            data.put("observedAt", observedAt);
            data.put("positionSampledAt", positionSampledAt);
            data.put("alignmentSource", alignmentSource);
            data.put("reporterId", reporterId);
            data.put("roomCode", roomCode);
            return data;
        }
    }

    public record BattleMapObservationCandidatePayload(
            int baseChunkX,
            int baseChunkZ,
            long positionSampledAt,
            String source) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("baseChunkX", baseChunkX);
            data.put("baseChunkZ", baseChunkZ);
            data.put("positionSampledAt", positionSampledAt);
            data.put("source", source);
            return data;
        }
    }

    public record BattleMapObservationCellPayload(
            int relChunkX,
            int relChunkZ,
            String symbol,
            String colorRaw) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("relChunkX", relChunkX);
            data.put("relChunkZ", relChunkZ);
            data.put("symbol", symbol);
            data.put("colorRaw", colorRaw);
            return data;
        }
    }

    public record BattleMapObservationPayload(
            String dimension,
            int mapSize,
            int anchorRow,
            int anchorCol,
            long snapshotObservedAt,
            long parsedAt,
            java.util.List<Map<String, Object>> candidates,
            java.util.List<Map<String, Object>> cells) {
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("dimension", dimension);
            data.put("mapSize", mapSize);
            data.put("anchorRow", anchorRow);
            data.put("anchorCol", anchorCol);
            data.put("snapshotObservedAt", snapshotObservedAt);
            data.put("parsedAt", parsedAt);
            data.put("candidates", candidates);
            data.put("cells", cells);
            return data;
        }
    }
}
