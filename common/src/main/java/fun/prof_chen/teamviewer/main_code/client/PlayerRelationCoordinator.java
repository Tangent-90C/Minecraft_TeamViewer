package fun.prof_chen.teamviewer.main_code.client;

import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.PlayerRelationClassifier;
import fun.prof_chen.teamviewer.main_code.plugin.PluginSnapshot;
import fun.prof_chen.teamviewer.main_code.plugin.PluginRuntimeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Merges all active local relationship classifiers against one cached Tab snapshot. */
public final class PlayerRelationCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerRelationCoordinator.class);
    private static final long CONFLICT_WARNING_INTERVAL_MS = 30_000L;

    private final IntegrationRegistry integrations;
    private final Map<UUID, Long> conflictWarnings = new HashMap<>();
    private volatile Map<UUID, PlayerRelation> relations = Map.of();

    public PlayerRelationCoordinator(IntegrationRegistry integrations) {
        this.integrations = Objects.requireNonNull(integrations, "integrations");
    }

    public void refresh(List<TabPlayerSnapshot> tabPlayers) {
        List<TabPlayerSnapshot> snapshot = List.copyOf(tabPlayers == null ? List.of() : tabPlayers);
        Set<UUID> visiblePlayers = visiblePlayerIds(snapshot);
        if (visiblePlayers.isEmpty()) {
            relations = Map.of();
            return;
        }

        Map<UUID, PlayerRelation> merged = new LinkedHashMap<>();
        Set<UUID> conflicts = new HashSet<>();
        for (PlayerRelationClassifier classifier : integrations.activePlayerRelationClassifiers()) {
            Map<UUID, PlayerRelation> classified;
            try {
                classified = classifier.classify(snapshot);
            } catch (RuntimeException | LinkageError error) {
                LOGGER.warn("Player relation classifier {} failed: {}", classifier.id(), error.getMessage());
                continue;
            }
            if (classified == null || classified.isEmpty()) continue;
            for (Map.Entry<UUID, PlayerRelation> entry : classified.entrySet()) {
                UUID playerId = entry.getKey();
                PlayerRelation relation = entry.getValue();
                if (playerId == null || relation == null || !visiblePlayers.contains(playerId)) continue;
                PlayerRelation previous = merged.putIfAbsent(playerId, relation);
                if (previous != null && previous != relation) {
                    merged.put(playerId, PlayerRelation.NEUTRAL);
                    conflicts.add(playerId);
                }
            }
        }
        relations = Map.copyOf(merged);
        warnConflicts(conflicts);
    }

    public PlayerRelation relation(UUID playerId) {
        return playerId == null ? null : relations.get(playerId);
    }

    public Map<UUID, PlayerRelation> snapshot() {
        return relations;
    }

    /** Adds relation-specific diagnostics without leaking their keys into the main client coordinator. */
    public PluginSnapshot decoratePluginSnapshot(
            PluginSnapshot plugin, List<TabPlayerSnapshot> tabPlayers) {
        if (plugin == null || plugin.capabilities().stream().noneMatch(value ->
                IntegrationRole.PLAYER_RELATION.id().equals(value.role()))) {
            return plugin;
        }
        List<PluginRuntimeState> state = new java.util.ArrayList<>(plugin.runtimeState());
        state.removeIf(value -> value.key().startsWith("effective."));
        state.addAll(runtimeState(tabPlayers));
        return plugin.withRuntimeState(state);
    }

    /** Builds bounded display state for the plugin page without changing persisted settings. */
    public List<PluginRuntimeState> runtimeState(List<TabPlayerSnapshot> tabPlayers) {
        Map<PlayerRelation, List<String>> grouped = new LinkedHashMap<>();
        grouped.put(PlayerRelation.FRIENDLY, new java.util.ArrayList<>());
        grouped.put(PlayerRelation.ENEMY, new java.util.ArrayList<>());
        grouped.put(PlayerRelation.NEUTRAL, new java.util.ArrayList<>());
        for (TabPlayerSnapshot player : tabPlayers == null ? List.<TabPlayerSnapshot>of() : tabPlayers) {
            if (player == null || player.playerId() == null) continue;
            UUID id;
            try {
                id = UUID.fromString(player.playerId().trim());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            PlayerRelation relation = relations.getOrDefault(id, PlayerRelation.NEUTRAL);
            String name = player.name() == null || player.name().isBlank() ? id.toString() : player.name();
            List<String> values = grouped.get(relation);
            if (values.size() < 128) values.add(name);
        }
        return List.of(
                state("effective.friendly", "当前 Tab 友军", grouped.get(PlayerRelation.FRIENDLY)),
                state("effective.enemy", "当前 Tab 敌军", grouped.get(PlayerRelation.ENEMY)),
                state("effective.neutral", "当前 Tab 中立/未识别", grouped.get(PlayerRelation.NEUTRAL)));
    }

    private static PluginRuntimeState state(String key, String label, List<String> names) {
        return new PluginRuntimeState(key, label, names.size() + " 人: " + String.join(", ", names));
    }

    public void clear() {
        relations = Map.of();
        conflictWarnings.clear();
    }

    private static Set<UUID> visiblePlayerIds(List<TabPlayerSnapshot> players) {
        Set<UUID> result = new HashSet<>();
        for (TabPlayerSnapshot player : players) {
            if (player == null || player.playerId() == null || player.playerId().isBlank()) continue;
            try {
                result.add(UUID.fromString(player.playerId().trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private void warnConflicts(Set<UUID> conflicts) {
        long now = System.currentTimeMillis();
        conflictWarnings.keySet().retainAll(conflicts);
        for (UUID playerId : conflicts) {
            long previous = conflictWarnings.getOrDefault(playerId, Long.MIN_VALUE);
            if (previous != Long.MIN_VALUE && now - previous < CONFLICT_WARNING_INTERVAL_MS) continue;
            conflictWarnings.put(playerId, now);
            LOGGER.warn("Conflicting local player relation decisions for {}; using NEUTRAL", playerId);
        }
    }
}
