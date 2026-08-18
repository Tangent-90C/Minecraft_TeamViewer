package fun.prof_chen.teamviewer.main_code.client.model;

import java.util.HashMap;
import java.util.Map;

public record TabPlayerSnapshot(
        String playerId, String name, String prefixText, String prefixColored, String teamId) {
    /**
     * Backwards-compatible constructor for adapters that do not expose the scoreboard team ID.
     */
    public TabPlayerSnapshot(String playerId, String name, String prefixText, String prefixColored) {
        this(playerId, name, prefixText, prefixColored, null);
    }

    public Map<String, Object> toProtocolMap() {
        Map<String, Object> node = new HashMap<>(8);
        if (playerId != null && !playerId.isBlank()) {
            node.put("playerUUID", playerId);
        }
        node.put("name", name);
        if (prefixText != null && !prefixText.isBlank()) {
            node.put("prefixText", prefixText);
        }
        if (prefixColored != null && !prefixColored.isBlank()) {
            node.put("prefixColored", prefixColored);
        }
        return node;
    }
}
