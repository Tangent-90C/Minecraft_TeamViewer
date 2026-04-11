package fun.prof_chen.teamviewer.main_code.battlemap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BattleMapProjectionUtil {
    private BattleMapProjectionUtil() {
    }

    public static Projection buildProjection(
            String dimension,
            int baseChunkX,
            int baseChunkZ,
            List<Map<String, Object>> cells
    ) {
        Set<String> chunkIds = new HashSet<>();
        List<String> semanticLines = new ArrayList<>();
        String normalizedDimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.trim();
        if (cells == null || cells.isEmpty()) {
            return new Projection("", chunkIds);
        }

        for (Map<String, Object> cell : cells) {
            if (cell == null || cell.isEmpty()) {
                continue;
            }
            Integer relChunkX = toInteger(cell.get("relChunkX"));
            Integer relChunkZ = toInteger(cell.get("relChunkZ"));
            if (relChunkX == null || relChunkZ == null) {
                continue;
            }
            int absoluteChunkX = baseChunkX + relChunkX;
            int absoluteChunkZ = baseChunkZ + relChunkZ;
            String chunkId = buildBattleChunkSyntheticId(normalizedDimension, absoluteChunkX, absoluteChunkZ);
            if (chunkId == null) {
                continue;
            }
            String symbol = normalizeText(cell.get("symbol"));
            String colorRaw = normalizeText(cell.get("colorRaw"));
            chunkIds.add(chunkId);
            semanticLines.add(chunkId + "|" + (symbol == null ? "" : symbol) + "|" + (colorRaw == null ? "" : colorRaw));
        }

        Collections.sort(semanticLines);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (String line : semanticLines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            byte[] bytes = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return new Projection(hex.toString(), chunkIds);
        } catch (Exception ignored) {
            return new Projection("projection_hash_error", chunkIds);
        }
    }

    public static String buildBattleChunkSyntheticId(String dimension, int chunkX, int chunkZ) {
        String normalizedDimension = dimension == null ? null : dimension.trim();
        if (normalizedDimension == null || normalizedDimension.isBlank()) {
            return null;
        }
        return normalizedDimension + "|" + chunkX + "|" + chunkZ;
    }

    private static Integer toInteger(Object raw) {
        if (raw instanceof Integer integer) {
            return integer;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalizeText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    public record Projection(String semanticHash, Set<String> chunkIds) {
    }
}
