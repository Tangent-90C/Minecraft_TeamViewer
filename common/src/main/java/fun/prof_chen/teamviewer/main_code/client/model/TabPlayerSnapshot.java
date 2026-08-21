package fun.prof_chen.teamviewer.main_code.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record TabPlayerSnapshot(
        String playerId,
        String name,
        String prefixText,
        String prefixColored,
        String scoreboardTeamId,
        String scoreboardSuffix,
        Integer scoreboardColorRgb,
        String displayName,
        FormattedTextSnapshot formattedDisplayName,
        FormattedTextSnapshot formattedScoreboardPrefix,
        FormattedTextSnapshot formattedScoreboardSuffix) {
    /**
     * Backwards-compatible constructor for adapters that do not expose the scoreboard team ID.
     */
    public TabPlayerSnapshot(String playerId, String name, String prefixText, String prefixColored) {
        this(playerId, name, prefixText, prefixColored, null);
    }

    public TabPlayerSnapshot(
            String playerId, String name, String prefixText, String prefixColored, String scoreboardTeamId) {
        this(playerId, name, prefixText, prefixColored, scoreboardTeamId, null, null,
                prefixColored, null, null, null);
    }

    /** Compatibility alias for plugins compiled against the old ambiguous name. */
    @Deprecated(forRemoval = false)
    public String teamId() {
        return scoreboardTeamId;
    }

    public String scoreboardPrefix() {
        return prefixText;
    }

    public Map<String, Object> toProtocolMap() {
        Map<String, Object> node = new LinkedHashMap<>();
        if (playerId != null && !playerId.isBlank()) {
            node.put("uuid", playerId);
            node.put("playerUUID", playerId);
        }
        node.put("name", name);
        putText(node, "displayName", displayName == null ? prefixColored : displayName);
        putText(node, "prefixedName", prefixText);
        putText(node, "scoreboardTeamId", scoreboardTeamId);
        putText(node, "scoreboardPrefix", prefixText);
        putText(node, "scoreboardSuffix", scoreboardSuffix);
        if (scoreboardColorRgb != null) node.put("scoreboardColorRgb", scoreboardColorRgb & 0xFFFFFF);
        if (formattedDisplayName != null) node.put("formattedDisplayName", formattedDisplayName.toProtocolMap());
        if (formattedScoreboardPrefix != null) {
            node.put("formattedScoreboardPrefix", formattedScoreboardPrefix.toProtocolMap());
        }
        if (formattedScoreboardSuffix != null) {
            node.put("formattedScoreboardSuffix", formattedScoreboardSuffix.toProtocolMap());
        }
        return node;
    }

    private static void putText(Map<String, Object> node, String key, String value) {
        if (value != null && !value.isBlank()) node.put(key, value);
    }
}
