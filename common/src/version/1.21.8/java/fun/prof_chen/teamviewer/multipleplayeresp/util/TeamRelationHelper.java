package fun.prof_chen.teamviewer.multipleplayeresp.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import fun.prof_chen.teamviewer.multipleplayeresp.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamRelationHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("TeamViewer/TeamRelation");
    private TeamRelationHelper() {
    }

    public enum TeamRelation {
        FRIENDLY,
        ENEMY,
        NEUTRAL,
        UNKNOWN
    }

    private static String lastFriendlyPrefixes = null;
    private static String lastEnemyPrefixes = null;
    private static List<String> cachedFriendlyPrefixes = null;
    private static List<String> cachedEnemyPrefixes = null;
    
    private static final Map<UUID, CachedRelation> relationCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 2 * 60 * 1000;
    private static long lastCacheClearTime = 0;
    private static final long CACHE_CLEAR_INTERVAL_MS = 60 * 1000;

    private static class CachedRelation {
        final TeamRelation relation;
        final long timestamp;
        final String configFingerprint;
        
        CachedRelation(TeamRelation relation, String configFingerprint) {
            this.relation = relation;
            this.timestamp = System.currentTimeMillis();
            this.configFingerprint = configFingerprint;
        }
        
        boolean isValid(String currentFingerprint) {
            return configFingerprint.equals(currentFingerprint) && 
                   (System.currentTimeMillis() - timestamp) < CACHE_DURATION_MS;
        }
    }

    public static TeamRelation determineRelation(MinecraftClient client, UUID targetUuid, String targetName, Config config) {
        if (client == null || client.player == null || client.world == null) {
            return TeamRelation.UNKNOWN;
        }

        String configFingerprint = buildConfigFingerprint(config);
        CachedRelation cached = relationCache.get(targetUuid);
        if (cached != null && cached.isValid(configFingerprint)) {
            return cached.relation;
        }

        TeamRelation relation = computeRelation(client, targetUuid, targetName, config);
        relationCache.put(targetUuid, new CachedRelation(relation, configFingerprint));
        
        return relation;
    }
    
    private static String buildConfigFingerprint(Config config) {
        if (config == null) return "";
        return (config.getFriendlyPrefixes() == null ? "" : config.getFriendlyPrefixes()) + "|" +
               (config.getEnemyPrefixes() == null ? "" : config.getEnemyPrefixes());
    }
    
    private static TeamRelation computeRelation(MinecraftClient client, UUID targetUuid, String targetName, Config config) {
        String displayName = getPlayerDisplayName(client, targetUuid, targetName);
        String cleanDisplayName = stripFormatting(displayName);
        String originalName = targetName != null ? targetName : getPlayerNameByUuid(client, targetUuid);
        
        List<String> friendlyPrefixes = getCachedFriendlyPrefixes(config);
        List<String> enemyPrefixes = getCachedEnemyPrefixes(config);
        
        for (String prefix : friendlyPrefixes) {
            String cleanPrefix = stripFormatting(prefix);
            if (!cleanPrefix.isEmpty()) {
                if (cleanDisplayName != null && cleanDisplayName.startsWith(cleanPrefix)) {
                    return TeamRelation.FRIENDLY;
                }
                if (originalName != null && originalName.startsWith(cleanPrefix)) {
                    return TeamRelation.FRIENDLY;
                }
            }
        }
        
        for (String prefix : enemyPrefixes) {
            String cleanPrefix = stripFormatting(prefix);
            if (!cleanPrefix.isEmpty()) {
                if (cleanDisplayName != null && cleanDisplayName.startsWith(cleanPrefix)) {
                    return TeamRelation.ENEMY;
                }
                if (originalName != null && originalName.startsWith(cleanPrefix)) {
                    return TeamRelation.ENEMY;
                }
            }
        }

        Team localTeam = getLocalPlayerTeam(client);
        Team targetTeam = getPlayerTeam(client, targetUuid, targetName);

        if (localTeam != null && targetTeam != null) {
            if (localTeam == targetTeam || localTeam.getName().equals(targetTeam.getName())) {
                return TeamRelation.FRIENDLY;
            }
        }

        return TeamRelation.NEUTRAL;
    }
    
    public static void clearCache() {
        relationCache.clear();
        LOGGER.info("TeamRelation cache cleared");
    }
    
    public static void tickCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCacheClearTime > CACHE_CLEAR_INTERVAL_MS) {
            lastCacheClearTime = now;
            relationCache.entrySet().removeIf(entry -> 
                (now - entry.getValue().timestamp) > CACHE_DURATION_MS);
        }
    }

    private static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '\u00A7' || c == '&') {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private static List<String> getCachedFriendlyPrefixes(Config config) {
        if (config == null) return List.of();
        String current = config.getFriendlyPrefixes();
        if (current == null) current = "";
        if (!current.equals(lastFriendlyPrefixes)) {
            lastFriendlyPrefixes = current;
            cachedFriendlyPrefixes = config.getFriendlyPrefixList();
        }
        return cachedFriendlyPrefixes != null ? cachedFriendlyPrefixes : List.of();
    }

    private static List<String> getCachedEnemyPrefixes(Config config) {
        if (config == null) return List.of();
        String current = config.getEnemyPrefixes();
        if (current == null) current = "";
        if (!current.equals(lastEnemyPrefixes)) {
            lastEnemyPrefixes = current;
            cachedEnemyPrefixes = config.getEnemyPrefixList();
        }
        return cachedEnemyPrefixes != null ? cachedEnemyPrefixes : List.of();
    }

    public static String getPlayerDisplayName(MinecraftClient client, UUID uuid, String fallbackName) {
        if (client == null || client.getNetworkHandler() == null) {
            return fallbackName;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
        if (entry != null) {
            Text displayName = entry.getDisplayName();
            if (displayName != null) {
                String displayStr = displayName.getString();
                if (displayStr != null && !displayStr.isBlank()) {
                    return displayStr;
                }
            }
            if (entry.getProfile() != null) {
                return entry.getProfile().getName();
            }
        }
        return fallbackName;
    }

    private static String getPlayerNameByUuid(MinecraftClient client, UUID uuid) {
        if (client == null || client.getNetworkHandler() == null) {
            return null;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
        if (entry != null && entry.getProfile() != null) {
            return entry.getProfile().getName();
        }
        return null;
    }

    public static Team getLocalPlayerTeam(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }

        Team team = client.player.getScoreboardTeam();
        if (team != null) {
            return team;
        }

        if (client.world != null) {
            Scoreboard scoreboard = client.world.getScoreboard();
            if (scoreboard != null) {
                return scoreboard.getScoreHolderTeam(client.player.getName().getString());
            }
        }

        return null;
    }

    public static Team getPlayerTeam(MinecraftClient client, UUID playerUuid, String playerName) {
        if (client == null || client.world == null) {
            return null;
        }

        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerUuid);
            if (entry != null) {
                Team team = entry.getScoreboardTeam();
                if (team != null) {
                    return team;
                }
            }
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        if (scoreboard != null && playerName != null) {
            return scoreboard.getScoreHolderTeam(playerName);
        }

        return null;
    }

    public static String relationToString(TeamRelation relation) {
        if (relation == null) {
            return null;
        }
        return switch (relation) {
            case FRIENDLY -> "friendly";
            case ENEMY -> "enemy";
            case NEUTRAL -> "neutral";
            case UNKNOWN -> null;
        };
    }
}
