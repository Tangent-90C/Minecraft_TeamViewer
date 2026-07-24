package fun.prof_chen.teamviewer.main_code.client.model;

import java.util.HashMap;
import java.util.Map;

public record TabPlayerSnapshot(String playerId, String name, String prefixText, String prefixColored) {
    public Map<String, Object> toProtocolMap() {
        Map<String, Object> node = new HashMap<>();
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
