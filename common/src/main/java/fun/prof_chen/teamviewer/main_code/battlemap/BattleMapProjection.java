package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.model.BattleChunkRefData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BattleMapProjection {
    private BattleMapProjection() { }

    public static Result build(String dimension, int baseChunkX, int baseChunkZ, List<Map<String, Object>> cells) {
        Set<BattleChunkRefData> refs = new HashSet<>();
        List<String> semantic = new ArrayList<>();
        for (Map<String, Object> cell : cells) {
            int relX = ((Number) cell.get("relChunkX")).intValue();
            int relZ = ((Number) cell.get("relChunkZ")).intValue();
            BattleChunkRefData ref = new BattleChunkRefData(
                    dimension, baseChunkX + relX, baseChunkZ + relZ);
            String id = ref.identityKey();
            refs.add(ref);
            semantic.add(id + "|" + cell.getOrDefault("symbol", "") + "|" + cell.getOrDefault("colorRaw", ""));
        }
        Collections.sort(semantic);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (String line : semantic) digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            byte[] bytes = digest.digest();
            for (int i = 0; i < 8; i++) hash.append(String.format("%02x", bytes[i]));
            return new Result(hash.toString(), refs);
        } catch (Exception ignored) {
            return new Result("projection_hash_error", refs);
        }
    }

    public record Result(String semanticHash, Set<BattleChunkRefData> chunkRefs) { }
}
