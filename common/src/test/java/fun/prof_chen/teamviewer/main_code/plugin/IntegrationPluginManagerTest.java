package fun.prof_chen.teamviewer.main_code.plugin;

import com.google.gson.JsonParser;
import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.BattleMapSourceSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationCapability;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationIds;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationImplementationSource;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRegistry;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationRole;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import fun.prof_chen.teamviewer.main_code.client.sdk.PluginRuntimeStatus;
import fun.prof_chen.teamviewer.main_code.battlemap.ScoreboardSnapshot;
import fun.prof_chen.teamviewer.main_code.battlemap.BattleMapCoordinator;
import fun.prof_chen.teamviewer.main_code.bridge.NetworkManager;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.SystemChatMessageSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.PlayerRelationClassifier;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.SharedWaypointMapAdapter;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.MapWaypointCommand;
import fun.prof_chen.teamviewer.main_code.mapbridge.model.NativeMapWaypointSnapshot;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.network.abstraction.RuntimeGateway;
import fun.prof_chen.teamviewer.main_code.network.abstraction.TransportProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationPluginManagerTest {
    @TempDir Path temporary;
    private static final MinecraftClientObjects TEST_CLIENT_OBJECTS = new MinecraftClientObjects() {
        @Override public Object blockPosition(int x, int y, int z) {
            return new net.minecraft.util.math.BlockPos(x, y, z);
        }
        @Override public Object dimensionKey(String dimensionId) {
            return new net.minecraft.world.World.Key(dimensionId == null || dimensionId.isBlank()
                    ? "minecraft:overworld" : dimensionId);
        }
    };

    @Test
    void builtinsActivateLuaCapabilitiesAndExampleRemainsDisabled() {
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));

        assertEquals(6, manager.snapshots().stream().filter(PluginSnapshot::builtIn).count());
        assertEquals(PluginRuntimeStatus.DISABLED,
                manager.snapshot(IntegrationIds.PLUGIN_EXAMPLE).runtimeStatus());
        assertEquals(PluginRuntimeStatus.DISABLED,
                manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS).runtimeStatus());
        assertTrue(manager.snapshots().stream()
                .filter(plugin -> !Set.of(
                        IntegrationIds.PLUGIN_EXAMPLE,
                        IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS).contains(plugin.id()))
                .allMatch(plugin -> plugin.runtimeStatus() == PluginRuntimeStatus.ACTIVE));
        assertEquals(0, registry.activeRemotePlayerProjections().size());
        assertEquals(0, registry.activeSharedWaypointAdapters().size());
        assertNotNull(registry.activeBattleMapSource(IntegrationIds.NODEMC_BATTLE_MAP));
        assertEquals(IntegrationImplementationSource.LUA,
                manager.snapshot(IntegrationIds.PLUGIN_NODEMC).capabilities().get(0).implementationSource());
        assertTrue(manager.snapshot(IntegrationIds.PLUGIN_XAERO).capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.MOD_NOT_INSTALLED));
        assertEquals("NodeMC Scoreboard Battle Map",
                manager.snapshot(IntegrationIds.PLUGIN_NODEMC).capabilities().get(0).displayName());

        assertTrue(manager.setEnabled(IntegrationIds.PLUGIN_XAERO, false));
        PluginSnapshot xaero = manager.snapshot(IntegrationIds.PLUGIN_XAERO);
        assertNotNull(xaero);
        assertEquals(PluginRuntimeStatus.PENDING_RESTART, xaero.runtimeStatus());
        assertEquals(3, xaero.capabilities().size());
        assertTrue(xaero.capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.MOD_NOT_INSTALLED));
        assertTrue(xaero.capabilities().stream().allMatch(value ->
                value.runtimeStatus() == PluginRuntimeStatus.PENDING_RESTART));
        assertEquals(0, registry.activeRemotePlayerProjections().size());

        manager.shutdown();
    }

    @Test
    void tabLabelRelationsMatchCachedFieldsAndUpdateSettingsWithoutReload() {
        IntegrationRegistry registry = completeRegistry();
        List<String> notifications = new ArrayList<>();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")),
                new PluginHostAccess(null, null, null, null, List::of,
                        Map.<String, java.util.function.Supplier<?>>of(
                                PluginNotificationSink.SERVICE_ID,
                                () -> (PluginNotificationSink) notifications::add)));
        PluginSnapshot disabled = manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS);
        assertFalse(disabled.enabled());
        assertEquals("automatic_only", disabled.settings().get("relation_source_mode"));
        assertEquals("饶州", disabled.settings().get("friendly_tags"));
        assertEquals("星辉", disabled.settings().get("enemy_tags"));

        assertTrue(manager.setEnabled(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, true));
        assertTrue(manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS)
                .settingState("friendly_tags").detail().contains("/town"));
        assertEquals(6, manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS).runtimeState().size());
        PlayerRelationClassifier classifier = registry.activePlayerRelationClassifiers().stream()
                .filter(value -> IntegrationIds.TAB_LABEL_RELATIONS.equals(value.id()))
                .findFirst().orElseThrow();
        UUID defaultIgnoredManual = UUID.randomUUID();
        assertEquals(PlayerRelation.NEUTRAL, classifier.classify(List.of(
                new TabPlayerSnapshot(defaultIgnoredManual.toString(), "Player", "[饶州]", "")))
                .get(defaultIgnoredManual));
        assertTrue(manager.setSetting(
                IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, "relation_source_mode", "manual_first"));
        UUID friendly = UUID.randomUUID();
        UUID enemy = UUID.randomUUID();
        UUID fallback = UUID.randomUUID();
        UUID both = UUID.randomUUID();
        UUID unmatched = UUID.randomUUID();
        Map<UUID, PlayerRelation> relations = classifier.classify(List.of(
                new TabPlayerSnapshot(friendly.toString(), "Player", "[饶州]", ""),
                new TabPlayerSnapshot(enemy.toString(), "Player", "[星辉]", ""),
                new TabPlayerSnapshot(fallback.toString(), "饶州Player", "NoTown", ""),
                new TabPlayerSnapshot(both.toString(), "Player", "[星辉饶州]", ""),
                new TabPlayerSnapshot(unmatched.toString(), "Player", "[Other]", ""),
                new TabPlayerSnapshot(null, "饶州Missing", "", "")));

        assertEquals(PlayerRelation.FRIENDLY, relations.get(friendly));
        assertEquals(PlayerRelation.ENEMY, relations.get(enemy));
        assertEquals(PlayerRelation.FRIENDLY, relations.get(fallback));
        assertEquals(PlayerRelation.FRIENDLY, relations.get(both));
        assertEquals(PlayerRelation.NEUTRAL, relations.get(unmatched));
        assertEquals(5, relations.size());

        UUID secondFriendly = UUID.randomUUID();
        assertTrue(manager.setSetting(
                IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, "friendly_tags", "极乐净土,饶州"));
        Map<UUID, PlayerRelation> multipleFriendlyTags = classifier.classify(List.of(
                new TabPlayerSnapshot(friendly.toString(), "Player", "[饶州]", ""),
                new TabPlayerSnapshot(secondFriendly.toString(), "Player", "[极乐净土]", "")));
        assertEquals(PlayerRelation.FRIENDLY, multipleFriendlyTags.get(friendly));
        assertEquals(PlayerRelation.FRIENDLY, multipleFriendlyTags.get(secondFriendly));

        assertTrue(manager.setSetting(
                IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, "friendly_tags", "新城， 第二城;第三城"));
        Map<UUID, PlayerRelation> updated = classifier.classify(List.of(
                new TabPlayerSnapshot(friendly.toString(), "Player", "[饶州]", ""),
                new TabPlayerSnapshot(fallback.toString(), "Player", "[第二城]", "")));
        assertEquals(PlayerRelation.NEUTRAL, updated.get(friendly));
        assertEquals(PlayerRelation.FRIENDLY, updated.get(fallback));

        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b§l城镇 马德里:", true)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b§l城镇 马德里:", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b- 关系§f: §a[你]", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b- 盟友§f: 罗马, 赫尔辛基", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b- 敌对§f: 汉城", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b- 正在交战§f: 东京", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b- 领袖§f: H14_M1dori", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("§b- 官员[2]§f: PeterPG_, H14_M1dori", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot(
                "§b- 居民[2]§f: §fWlxfg, §aprofessor_chen§f", false)));
        assertTrue(manager.onSystemChatMessage(new SystemChatMessageSnapshot(
                "§b输入 \"/town help\" 查看指令", false)));
        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).contains("马德里"));
        assertTrue(notifications.get(0).contains("友军 4 人"));
        Map<String, String> importedState = manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS)
                .runtimeState().stream().collect(java.util.stream.Collectors.toMap(
                        PluginRuntimeState::key, PluginRuntimeState::value));
        assertEquals("马德里", importedState.get("source.local_town"));
        assertTrue(importedState.get("source.friendly_towns").contains("罗马"));
        assertTrue(importedState.get("source.enemy_towns").contains("汉城"));
        assertTrue(importedState.get("source.members").contains("4 人"));

        UUID localTown = UUID.randomUUID();
        UUID ally = UUID.randomUUID();
        UUID hostile = UUID.randomUUID();
        UUID war = UUID.randomUUID();
        UUID internalFallback = UUID.randomUUID();
        UUID resident = UUID.randomUUID();
        UUID neutral = UUID.randomUUID();
        Map<UUID, PlayerRelation> townRelations = classifier.classify(List.of(
                new TabPlayerSnapshot(localTown.toString(), "Player", "[马德里]", "", "汉城"),
                new TabPlayerSnapshot(ally.toString(), "Player", "[罗马]", "", "汉城"),
                new TabPlayerSnapshot(hostile.toString(), "Player", "[汉城]", "", "罗马"),
                new TabPlayerSnapshot(war.toString(), "Player", "[东京]", "", "罗马"),
                new TabPlayerSnapshot(internalFallback.toString(), "Player", "NoTown", "", "罗马"),
                new TabPlayerSnapshot(resident.toString(), "wLxFg", "", "", ""),
                new TabPlayerSnapshot(neutral.toString(), "Other", "[Other]", "", ""),
                new TabPlayerSnapshot(null, "Player", "[汉城]", "", "")));
        assertEquals(PlayerRelation.FRIENDLY, townRelations.get(localTown));
        assertEquals(PlayerRelation.FRIENDLY, townRelations.get(ally));
        assertEquals(PlayerRelation.ENEMY, townRelations.get(hostile));
        assertEquals(PlayerRelation.ENEMY, townRelations.get(war));
        assertEquals(PlayerRelation.FRIENDLY, townRelations.get(internalFallback));
        assertEquals(PlayerRelation.FRIENDLY, townRelations.get(resident));
        assertEquals(PlayerRelation.NEUTRAL, townRelations.get(neutral));
        assertEquals(7, townRelations.size());

        UUID sourceConflict = UUID.randomUUID();
        TabPlayerSnapshot conflictingPlayer = new TabPlayerSnapshot(
                sourceConflict.toString(), "第二城Player", "[汉城]", "");
        assertTrue(manager.setSetting(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS,
                "relation_source_mode", "automatic_only"));
        assertEquals(PlayerRelation.ENEMY,
                classifier.classify(List.of(conflictingPlayer)).get(sourceConflict));
        assertTrue(manager.setSetting(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS,
                "relation_source_mode", "manual_only"));
        assertEquals(PlayerRelation.FRIENDLY,
                classifier.classify(List.of(conflictingPlayer)).get(sourceConflict));
        assertTrue(manager.setSetting(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS,
                "relation_source_mode", "manual_first"));
        assertEquals(PlayerRelation.FRIENDLY,
                classifier.classify(List.of(conflictingPlayer)).get(sourceConflict));
        assertTrue(manager.setSetting(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS,
                "relation_source_mode", "automatic_first"));
        assertEquals(PlayerRelation.ENEMY,
                classifier.classify(List.of(conflictingPlayer)).get(sourceConflict));

        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("城镇 罗马:", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 关系: [中立]", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 敌对: 极乐净土", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("输入 \"/town help\" 查看指令", false)));
        assertEquals(1, notifications.size());
        assertEquals(PlayerRelation.FRIENDLY, classifier.classify(List.of(
                new TabPlayerSnapshot(localTown.toString(), "Player", "[马德里]", ""))).get(localTown));

        String manyEnemies = java.util.stream.IntStream.range(0, 130)
                .mapToObj(index -> "敌城" + index)
                .collect(java.util.stream.Collectors.joining(","));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("城镇 马德里:", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 关系: [你]", false)));
        assertFalse(manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 敌对: " + manyEnemies, false)));
        assertTrue(manager.onSystemChatMessage(new SystemChatMessageSnapshot("输入 \"/town help\" 查看指令", false)));
        UUID retainedEnemy = UUID.randomUUID();
        UUID cappedEnemy = UUID.randomUUID();
        Map<UUID, PlayerRelation> cappedRelations = classifier.classify(List.of(
                new TabPlayerSnapshot(retainedEnemy.toString(), "Player", "[敌城127]", ""),
                new TabPlayerSnapshot(cappedEnemy.toString(), "Player", "[敌城128]", "")));
        assertEquals(PlayerRelation.ENEMY, cappedRelations.get(retainedEnemy));
        assertEquals(PlayerRelation.NEUTRAL, cappedRelations.get(cappedEnemy));

        manager.onPlaySessionEnded();
        assertFalse(manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS).runtimeState().isEmpty());
        UUID manualFriendly = UUID.randomUUID();
        Map<UUID, PlayerRelation> afterSessionEnd = classifier.classify(List.of(
                new TabPlayerSnapshot(localTown.toString(), "Player", "[马德里]", ""),
                new TabPlayerSnapshot(hostile.toString(), "Player", "[汉城]", ""),
                new TabPlayerSnapshot(war.toString(), "Player", "[东京]", ""),
                new TabPlayerSnapshot(retainedEnemy.toString(), "Player", "[敌城127]", ""),
                new TabPlayerSnapshot(manualFriendly.toString(), "Player", "[第二城]", "")));
        assertEquals(PlayerRelation.FRIENDLY, afterSessionEnd.get(localTown));
        assertEquals(PlayerRelation.NEUTRAL, afterSessionEnd.get(hostile));
        assertEquals(PlayerRelation.NEUTRAL, afterSessionEnd.get(war));
        assertEquals(PlayerRelation.ENEMY, afterSessionEnd.get(retainedEnemy));
        assertEquals(PlayerRelation.FRIENDLY, afterSessionEnd.get(manualFriendly));
        manager.onPlaySessionStarted();
        manager.shutdown();
    }

    @Test
    void tabRelationsPersistAndClearThroughGenericRuntimeAction() {
        IntegrationRegistry registry = completeRegistry();
        PluginHostAccess host = new PluginHostAccess(null, null, null, null, List::of,
                Map.<String, java.util.function.Supplier<?>>of(
                        PluginNotificationSink.SERVICE_ID,
                        () -> (PluginNotificationSink) ignored -> { }));
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")), host);
        assertTrue(manager.setEnabled(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, true));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("城镇 马德里:", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 关系: [你]", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 盟友: 罗马", false));
        assertTrue(manager.onSystemChatMessage(new SystemChatMessageSnapshot("输入 \"/town help\" 查看指令", false)));
        PluginSnapshot imported = manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS);
        assertEquals(2, imported.runtimeActions().size());
        assertTrue(imported.runtimeActions().stream().allMatch(PluginRuntimeAction::enabled));
        assertTrue(imported.runtimeState().stream().anyMatch(state ->
                "source.collected_at".equals(state.key()) && state.observedAtMillis() != null));
        assertTrue(manager.invokeRuntimeAction(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS,
                "clear_automatic_relations"));
        PluginSnapshot cleared = manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS);
        assertTrue(cleared.runtimeState().stream().anyMatch(state ->
                "未采集".equals(state.value()) && "source.collected_at".equals(state.key())));
        assertTrue(cleared.runtimeActions().stream().noneMatch(PluginRuntimeAction::enabled));
        assertEquals("automatic_only", cleared.settings().get("relation_source_mode"));
        manager.shutdown();
    }

    @Test
    void tabRelationsExportVersionedProfileToNativeClipboard() {
        AtomicReference<String> clipboard = new AtomicReference<>();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new ClipboardRuntime(temporary, clipboard), completeRegistry(),
                Config.load(temporary.resolve("config.json")), PluginHostAccess.empty(), () -> 1_234_567L);
        assertTrue(manager.setEnabled(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, true));
        PluginRuntimeAction unavailable = manager.snapshot(IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS)
                .runtimeActions().stream().filter(action -> "copy_relation_export".equals(action.id()))
                .findFirst().orElseThrow();
        assertFalse(unavailable.enabled());

        manager.onSystemChatMessage(new SystemChatMessageSnapshot("城镇 马德里:", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 关系: [你]", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 盟友: 罗马, 赫尔辛基", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 敌对: 汉城", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 正在交战: 东京", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 领袖: Leader", false));
        manager.onSystemChatMessage(new SystemChatMessageSnapshot("- 居民[2]: Alice, Bob", false));
        assertTrue(manager.onSystemChatMessage(new SystemChatMessageSnapshot(
                "输入 \"/town help\" 查看指令", false)));

        assertFalse(manager.invokeRuntimeAction(
                IntegrationIds.PLUGIN_TAB_LABEL_RELATIONS, "copy_relation_export"));
        var exported = JsonParser.parseString(clipboard.get()).getAsJsonObject();
        assertEquals("team_view_relay_relation_profile", exported.get("kind").getAsString());
        assertEquals(1, exported.get("schemaVersion").getAsInt());
        assertEquals("teamviewer.tab-label-relations", exported.get("sourcePlugin").getAsString());
        assertEquals(1_234_567L, exported.get("exportedAtUtcMs").getAsLong());
        assertEquals(1_234_567L, exported.get("collectedAtUtcMs").getAsLong());
        assertEquals("马德里", exported.get("localTown").getAsString());
        assertEquals(Set.of("赫尔辛基", "罗马", "马德里"), exported.getAsJsonArray("friendlyTowns")
                .asList().stream().map(value -> value.getAsString()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("东京", "汉城"), exported.getAsJsonArray("enemyTowns")
                .asList().stream().map(value -> value.getAsString()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("alice", "bob", "leader"), exported.getAsJsonArray("friendlyPlayerNames")
                .asList().stream().map(value -> value.getAsString()).collect(java.util.stream.Collectors.toSet()));
        manager.shutdown();
    }

    @Test
    void syntaxFailureKeepsManifestCapabilitiesRegisteredAsFailed() throws Exception {
        Path plugin = temporary.resolve("team-view-relay/plugins/custom.bad");
        Files.createDirectories(plugin);
        Files.writeString(plugin.resolve("plugin.json"), """
                {
                  "schemaVersion": 1,
                  "apiVersion": "1",
                  "id": "custom.bad",
                  "name": "Broken plugin",
                  "version": "1.0.0",
                  "entry": "main.lua",
                  "defaultEnabled": true,
                  "hotToggle": "managed",
                  "provides": [
                    {"id":"custom-battle-map", "role":"battle-map-source", "name":"Custom"}
                  ]
                }
                """);
        Files.writeString(plugin.resolve("main.lua"), "this is not valid lua !!!");

        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        PluginSnapshot failed = manager.snapshot("custom.bad");

        assertNotNull(failed);
        assertEquals(PluginRuntimeStatus.LOAD_FAILED, failed.runtimeStatus());
        assertEquals(1, failed.capabilities().size());
        assertEquals(IntegrationSupportStatus.FAILED, failed.capabilities().get(0).status());
        assertEquals(PluginRuntimeStatus.LOAD_FAILED, failed.capabilities().get(0).runtimeStatus());
        assertFalse(failed.detail().isBlank());
        manager.shutdown();
    }

    @Test
    void doesNotRewriteLegacyPlaceholderIds() {
        IntegrationCapability capability = new IntegrationCapability(
                "xaero-world-map-players", IntegrationRole.REMOTE_PLAYER.id(),
                IntegrationSupportStatus.NOT_IMPLEMENTED, "not implemented");
        assertEquals("xaero-world-map-players", capability.id());
        assertEquals("external", capability.pluginId());
    }

    @Test
    void exposesJavaTypeAliasToTrustedLuaPlugins() throws Exception {
        writePlugin("custom.java", List.of(), """
                local String = java.type("java.lang.String")
                if String == nil then error("java.type returned nil") end
                if java.method("java.lang.String", "length") == nil then error("java.method returned nil") end
                if java.field("java.lang.Integer", "MAX_VALUE") == nil then error("java.field returned nil") end
                if java["new"] == nil or java.proxy == nil then error("java aliases are missing") end
                local list = java["new"](java.type("java.util.ArrayList"))
                if list == nil or list:size() ~= 0 then error("java.new failed") end
                if java.type("java.lang.Integer"):parseInt("7") ~= 7 then error("static Java method failed") end
                local OptionalApi = java.type("optional.mod.HiddenApi")
                if OptionalApi:staticValue() ~= 42 then error("mod-aware java.type failed") end
                local optional = java["new"]("optional.mod.HiddenApi", "loaded")
                if optional:value() ~= "loaded" then error("mod-aware java.new failed") end
                local valueMethod = java.method("optional.mod.HiddenApi", "value")
                if valueMethod:invoke(optional, nil) ~= "loaded" then error("mod-aware java.method failed") end
                local codeField = java.field("optional.mod.HiddenApi", "CODE")
                if codeField:get(nil) ~= 42 then error("mod-aware java.field failed") end
                local callback = java.proxy("optional.mod.HiddenCallback", {
                  apply = function(value) return "lua:" .. value end
                })
                if callback:apply("proxy") ~= "lua:proxy" then error("mod-aware java.proxy failed") end
                local world = snapshots.world()
                if world ~= "world-snapshot" then
                  error("world snapshot is unavailable: " .. tostring(world) .. " (" .. type(world) .. ")")
                end
                if snapshots.players()[1] ~= "player-snapshot" then error("player snapshot is unavailable") end
                tv.register_battle_map_source({
                  id = "custom-java-battle-map",
                  capture = function() return nil end
                })
                """, "custom-java-battle-map");

        IntegrationRegistry registry = completeRegistry();
        PluginHostAccess hostAccess = new PluginHostAccess(
                () -> "world-snapshot", () -> List.of("player-snapshot"), Map::of, Map::of);
        assertEquals("world-snapshot", hostAccess.snapshot("world"));
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")),
                hostAccess);

        assertEquals(PluginRuntimeStatus.ACTIVE, manager.snapshot("custom.java").runtimeStatus());
        assertTrue(manager.setEnabled("custom.java", false));
        assertEquals(IntegrationSupportStatus.AVAILABLE,
                manager.snapshot("custom.java").capabilities().get(0).status());
        assertTrue(registry.issues().isEmpty(), registry.issues().toString());
        manager.shutdown();
    }

    @Test
    void ignoresRemovedJourneyMapAndBattleMapLegacyConfig() throws Exception {
        Path configPath = temporary.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "showJourneyMapRemotePlayerBeacons": false,
                  "showJourneyMapRemotePlayerMapMarkers": true,
                  "battleMapMode": "simmc"
                }
                """);
        Path statePath = temporary.resolve("team-view-relay/plugin-state.json");
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, """
                {
                  "teamviewer.journeymap": {
                    "enabled": true,
                    "settings": {"show_beacons": true, "show_map_markers": false}
                  }
                }
                """);
        Config config = Config.load(configPath);
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), config);

        PluginSnapshot journeyMap = manager.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP);
        assertEquals(true, journeyMap.settings().get("show_beacons"));
        assertEquals(false, journeyMap.settings().get("show_map_markers"));
        assertEquals(true, journeyMap.settings().get("show_remote_players"));
        assertEquals(IntegrationIds.NODEMC_BATTLE_MAP, config.getBattleMapSourceId());
        assertFalse(Files.readString(statePath).contains("$migrations"));

        assertFalse(manager.setSetting(IntegrationIds.PLUGIN_JOURNEYMAP, "show_beacons", false),
                "hidden family-specific settings must not be writable through the UI-facing API");
        manager.shutdown();
        config.save();
        String savedConfig = Files.readString(configPath);
        assertFalse(savedConfig.contains("showJourneyMapRemotePlayer"));
        assertFalse(savedConfig.contains("battleMapMode"));
        assertTrue(savedConfig.contains(IntegrationIds.NODEMC_BATTLE_MAP));
        IntegrationPluginManager reloaded = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(configPath));
        assertEquals(true, reloaded.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP).settings().get("show_beacons"));
        reloaded.shutdown();
    }

    @Test
    void rejectsLuaRegistrationThatDoesNotMatchManifestProvides() throws Exception {
        writePlugin("custom.mismatch", List.of(), "-- intentionally registers nothing", "custom-mismatch-map");

        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        PluginSnapshot snapshot = manager.snapshot("custom.mismatch");

        assertEquals(PluginRuntimeStatus.LOAD_FAILED, snapshot.runtimeStatus());
        assertEquals(IntegrationSupportStatus.FAILED, snapshot.capabilities().get(0).status());
        assertTrue(snapshot.detail().contains("registered []"), snapshot.detail());
        manager.shutdown();
    }

    @Test
    void failedLoadRollsBackStagedNativeBindings() throws Exception {
        writePlugin("custom.rollback", List.of(), """
                tv.use_native_capability("custom-rollback-map", "nodemc-scoreboard-battle-map")
                error("fail after registration")
                """, "custom-rollback-map");
        IntegrationRegistry registry = completeRegistry();

        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));

        assertEquals(PluginRuntimeStatus.LOAD_FAILED, manager.snapshot("custom.rollback").runtimeStatus());
        assertNull(registry.implementation("custom-rollback-map"));
        assertEquals(IntegrationSupportStatus.FAILED,
                manager.snapshot("custom.rollback").capabilities().get(0).status());
        manager.shutdown();
    }

    @Test
    void pendingRestartKeepsThePreviouslyActiveImplementationAttached() throws Exception {
        writePlugin("custom.restart", List.of(), """
                tv.register_battle_map_source({id="custom-restart-map", capture=function() return nil end})
                """, "custom-restart-map", "restart");
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));

        assertNotNull(registry.activeBattleMapSource("custom-restart-map"));
        assertTrue(manager.setEnabled("custom.restart", false));
        assertEquals(PluginRuntimeStatus.PENDING_RESTART, manager.snapshot("custom.restart").runtimeStatus());
        assertNotNull(registry.activeBattleMapSource("custom-restart-map"));
        manager.shutdown();
    }

    @Test
    void reportsDependencyCyclesAndKeepsBothDeclarations() throws Exception {
        writePlugin("custom.cycle-a", List.of("custom.cycle-b"), """
                tv.register_battle_map_source({id="cycle-a-map", capture=function() return nil end})
                """, "cycle-a-map");
        writePlugin("custom.cycle-b", List.of("custom.cycle-a"), """
                tv.register_battle_map_source({id="cycle-b-map", capture=function() return nil end})
                """, "cycle-b-map");

        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));

        assertEquals(PluginRuntimeStatus.LOAD_FAILED, manager.snapshot("custom.cycle-a").runtimeStatus());
        assertEquals(PluginRuntimeStatus.LOAD_FAILED, manager.snapshot("custom.cycle-b").runtimeStatus());
        assertEquals(IntegrationSupportStatus.FAILED, manager.snapshot("custom.cycle-a").capabilities().get(0).status());
        assertEquals(IntegrationSupportStatus.FAILED, manager.snapshot("custom.cycle-b").capabilities().get(0).status());
        manager.shutdown();
    }

    @Test
    void rejectsArchiveContainingUnsafePaths() throws Exception {
        Path plugins = temporary.resolve("team-view-relay/plugins");
        Files.createDirectories(plugins);
        Path archive = plugins.resolve("unsafe.tvr-plugin");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            output.putNextEntry(new ZipEntry("../outside.txt"));
            output.write("should not escape".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("plugin.json"));
            output.write("{}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));

        assertNull(manager.snapshot("unsafe"));
        assertFalse(Files.exists(temporary.resolve("team-view-relay/outside.txt")));
        manager.shutdown();
    }

    @Test
    void copiedBuiltinsUseUniquePluginAndCapabilityIds() throws Exception {
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));

        assertNotNull(manager.copyBuiltin(IntegrationIds.PLUGIN_NODEMC));
        assertNotNull(manager.copyBuiltin(IntegrationIds.PLUGIN_NODEMC));
        manager.shutdown();

        IntegrationPluginManager reloaded = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        PluginSnapshot first = reloaded.snapshot(IntegrationIds.PLUGIN_NODEMC + ".custom");
        PluginSnapshot second = reloaded.snapshot(IntegrationIds.PLUGIN_NODEMC + ".custom2");

        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.enabled());
        assertFalse(second.enabled());
        assertNotEquals(first.capabilities().get(0).id(), second.capabilities().get(0).id());
        String readme = Files.readString(first.source().resolve("README.md"));
        assertTrue(readme.contains("NodeMC Scoreboard Lua Adapter"));
        assertTrue(readme.contains("NodeMC 计分板 Lua Adapter"));
        String script = Files.readString(first.source().resolve("main.lua"));
        assertTrue(script.contains(first.capabilities().get(0).id()));
        assertFalse(script.contains("id = \"" + IntegrationIds.NODEMC_BATTLE_MAP + "\""));
        reloaded.shutdown();
    }

    @Test
    void exampleDocumentsEveryHostApiAndEnablesAsThreeNoOpAdapters() throws Exception {
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));
        PluginSnapshot example = manager.snapshot(IntegrationIds.PLUGIN_EXAMPLE);

        assertNotNull(example);
        assertFalse(example.enabled());
        assertEquals(6, example.settingDefinitions().size());
        assertEquals(Set.of("boolean", "integer", "number", "string", "enum", "color"),
                example.settingDefinitions().stream().map(PluginManifest.SettingDefinition::type)
                        .collect(java.util.stream.Collectors.toSet()));
        String script;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "teamviewer/plugins/example/main.lua")) {
            assertNotNull(stream);
            script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String token : List.of(
                "environment.loader_id", "environment.minecraft_version", "environment.mod_version",
                "services.get", "mods.is_loaded", "snapshots.world", "snapshots.players",
                "snapshots.waypoints", "snapshots.scoreboard", "snapshots.tab_players",
                "java.type", "java.method",
                "java.field", "java[\"new\"]", "java.proxy", "tv.log.info", "tv.log.warn",
                "tv.log.error", "tv.register_remote_player_projection",
                "tv.register_shared_waypoint_adapter", "tv.register_battle_map_source",
                "tv.register_player_relation_classifier",
                "tv.notify", "tv.copy_json_to_clipboard", "tv.set_runtime_state",
                "tv.register_unavailable_capability", "tv.use_native_capability", "tv.on_enable",
                "tv.on_disable", "tv.on_settings_changed", "tv.on_system_chat",
                "tv.on_play_session_started", "tv.on_play_session_ended", "probe")) {
            assertTrue(script.contains(token), "example is missing " + token);
        }

        assertTrue(manager.setEnabled(IntegrationIds.PLUGIN_EXAMPLE, true));
        example = manager.snapshot(IntegrationIds.PLUGIN_EXAMPLE);
        assertEquals(PluginRuntimeStatus.ACTIVE, example.runtimeStatus());
        assertTrue(example.capabilities().stream().allMatch(value ->
                value.status() == IntegrationSupportStatus.AVAILABLE
                        && value.implementationSource() == IntegrationImplementationSource.LUA));
        assertNotNull(registry.activeBattleMapSource(IntegrationIds.EXAMPLE_BATTLE_MAP));
        assertTrue(registry.activeBattleMapSource(IntegrationIds.EXAMPLE_BATTLE_MAP).capture().isEmpty());
        assertTrue(registry.activeSharedWaypointAdapters().stream()
                .filter(value -> IntegrationIds.EXAMPLE_SHARED_WAYPOINT.equals(value.id()))
                .findFirst().orElseThrow().listLocalWaypoints().isEmpty());
        registry.activeRemotePlayerProjections().stream()
                .filter(value -> IntegrationIds.EXAMPLE_REMOTE_PLAYER.equals(value.id()))
                .findFirst().orElseThrow().sync(Map.of(), true);
        manager.shutdown();
    }

    @Test
    void dynamicProbeCanRecoverWithoutReloadingPlugin() throws Exception {
        writePlugin("custom.probe", List.of(), """
                tv.register_battle_map_source({
                  id="custom-probe-map",
                  probe=function()
                    if services.get("test.dynamic") == nil then
                      return {status="ENTRYPOINT_NOT_READY", detail="waiting"}
                    end
                    return {status="AVAILABLE", detail="ready"}
                  end,
                  capture=function() return nil end
                })
                """, "custom-probe-map");
        AtomicReference<Object> service = new AtomicReference<>();
        PluginHostAccess host = new PluginHostAccess(null, null, null, null,
                Map.of("test.dynamic", service::get));
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")), host);

        assertEquals(IntegrationSupportStatus.ENTRYPOINT_NOT_READY,
                manager.snapshot("custom.probe").capabilities().get(0).status());
        assertNull(registry.activeBattleMapSource("custom-probe-map"));
        service.set(new Object());
        assertEquals(IntegrationSupportStatus.AVAILABLE,
                manager.snapshot("custom.probe").capabilities().get(0).status());
        assertNotNull(registry.activeBattleMapSource("custom-probe-map"));
        manager.shutdown();
    }

    @Test
    void threeConsecutiveCallbackFailuresSuspendAndDetachPlugin() throws Exception {
        writePlugin("custom.suspends", List.of(), """
                tv.register_battle_map_source({
                  id="custom-suspending-map",
                  capture=function() error("intentional callback failure") end
                })
                """, "custom-suspending-map");
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));
        BattleMapSource source = registry.activeBattleMapSource("custom-suspending-map");
        assertNotNull(source);

        assertTrue(source.capture().isEmpty());
        assertTrue(source.capture().isEmpty());
        assertTrue(source.capture().isEmpty());

        assertEquals(PluginRuntimeStatus.SUSPENDED, manager.snapshot("custom.suspends").runtimeStatus());
        assertNull(registry.activeBattleMapSource("custom-suspending-map"));
        manager.shutdown();
    }

    @Test
    void systemChatCallbackFailuresAreIsolatedAndSuspendThePlugin() throws Exception {
        writePlugin("custom.chat-suspends", List.of(), """
                tv.register_battle_map_source({id="custom-chat-suspends-map", capture=function() return nil end})
                tv.on_system_chat(function(message) error("intentional chat callback failure") end)
                """, "custom-chat-suspends-map");
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));

        SystemChatMessageSnapshot message = new SystemChatMessageSnapshot("test", false);
        assertFalse(manager.onSystemChatMessage(message));
        assertFalse(manager.onSystemChatMessage(message));
        assertFalse(manager.onSystemChatMessage(message));

        assertEquals(PluginRuntimeStatus.SUSPENDED,
                manager.snapshot("custom.chat-suspends").runtimeStatus());
        assertFalse(manager.onSystemChatMessage(message));
        manager.shutdown();
    }

    @Test
    void successfulProbeDoesNotResetAnotherCallbacksFailureCount() throws Exception {
        writePlugin("custom.callback-isolation", List.of(), """
                tv.register_battle_map_source({
                  id="custom-isolated-map",
                  probe=function() return {status="AVAILABLE", detail="ready"} end,
                  capture=function() error("isolated capture failure") end
                })
                """, "custom-isolated-map");
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));
        BattleMapSource source = registry.activeBattleMapSource("custom-isolated-map");

        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(source.capture().isEmpty());
            manager.snapshot("custom.callback-isolation");
        }
        assertEquals(PluginRuntimeStatus.SUSPENDED,
                manager.snapshot("custom.callback-isolation").runtimeStatus());
        assertNull(registry.activeBattleMapSource("custom-isolated-map"));
        manager.shutdown();
    }

    @Test
    void missingDeclaredResourceKeepsManifestCapabilitiesVisibleAsFailed() throws Exception {
        Path plugin = temporary.resolve("team-view-relay/plugins/custom.missing-resource");
        Files.createDirectories(plugin);
        Files.writeString(plugin.resolve("plugin.json"), """
                {
                  "schemaVersion":1,"apiVersion":"1","id":"custom.missing-resource",
                  "name":"Missing resource","version":"1.0.0","entry":"missing.lua",
                  "defaultEnabled":true,"hotToggle":"managed",
                  "provides":[{"id":"missing-resource-map","role":"battle-map-source","name":"Missing"}]
                }
                """);

        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        PluginSnapshot snapshot = manager.snapshot("custom.missing-resource");
        assertNotNull(snapshot);
        assertEquals(PluginRuntimeStatus.LOAD_FAILED, snapshot.runtimeStatus());
        assertEquals(IntegrationSupportStatus.FAILED, snapshot.capabilities().get(0).status());
        assertTrue(snapshot.detail().contains("missing.lua"), snapshot.detail());
        manager.shutdown();
    }

    @Test
    void copiedMultiEntrypointBuiltinIncludesAndRewritesEveryResource() throws Exception {
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        Path copied = manager.copyBuiltin(IntegrationIds.PLUGIN_JOURNEYMAP);
        assertNotNull(copied);
        for (String file : List.of("main.lua", "fabric-api-v1.lua", "fabric-1.21.8.lua", "fabric-26.1.2.lua",
                "unsupported.lua", "README.md")) {
            assertTrue(Files.isRegularFile(copied.resolve(file)), "missing copied " + file);
        }
        String manifest = Files.readString(copied.resolve("plugin.json"));
        assertTrue(manifest.contains("teamviewer.journeymap.custom"));
        for (String file : List.of("main.lua", "fabric-api-v1.lua", "fabric-1.21.8.lua",
                "fabric-26.1.2.lua", "unsupported.lua")) {
            String script = Files.readString(copied.resolve(file));
            assertFalse(script.contains("\"journeymap-players\""), file);
            assertTrue(script.contains("journeymap-players.custom"), file);
        }
        manager.shutdown();

        IntegrationPluginManager reloaded = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        PluginSnapshot snapshot = reloaded.snapshot("teamviewer.journeymap.custom");
        assertNotNull(snapshot);
        assertFalse(snapshot.enabled());
        assertEquals(3, snapshot.capabilities().size());
        assertTrue(reloaded.setEnabled("teamviewer.journeymap.custom", true));
        assertEquals(PluginRuntimeStatus.ACTIVE,
                reloaded.snapshot("teamviewer.journeymap.custom").runtimeStatus());
        assertTrue(reloaded.snapshot("teamviewer.journeymap.custom").capabilities().stream()
                .allMatch(value -> value.implementationSource() == IntegrationImplementationSource.LUA));
        Path inactiveEntrypoint = copied.resolve("fabric-26.1.2.lua");
        Files.writeString(inactiveEntrypoint, Files.readString(inactiveEntrypoint) + "\n-- fingerprint change\n");
        assertTrue(reloaded.rescan());
        assertEquals(PluginRuntimeStatus.PENDING_RESTART,
                reloaded.snapshot("teamviewer.journeymap.custom").runtimeStatus());
        reloaded.shutdown();
    }

    @Test
    void nodeMcLuaAdapterParsesStandardizedScoreboardRecord() {
        ScoreboardSnapshot scoreboard = new ScoreboardSnapshot("minecraft:overworld", 1234L, List.of(
                scoreboardLine("-1 0 1", ""), scoreboardLine("◼◼◼", "#FF0000"),
                scoreboardLine("◼┼◼", "green"), scoreboardLine("◼◼◼", "#0000FF")));
        PluginHostAccess host = new PluginHostAccess(null, null, null, () -> scoreboard);
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")), host);

        BattleMapSourceSnapshot parsed = registry.activeBattleMapSource(IntegrationIds.NODEMC_BATTLE_MAP)
                .capture().orElseThrow();
        assertEquals(3, parsed.mapSize());
        assertEquals(1, parsed.anchorRow());
        assertEquals(1, parsed.anchorColumn());
        assertEquals(8, parsed.cells().size());
        assertEquals(1234L, parsed.observedAt());
        assertTrue(parsed.cells().stream().anyMatch(cell -> cell.chunkX() == -1 && cell.chunkZ() == -1
                && "#FF0000".equals(cell.colorRaw())));
        manager.shutdown();
    }

    @Test
    void javaAndLuaBattleMapSourcesUseTheSameObservationProtocolBridge() throws Exception {
        String capabilityId = "custom-protocol-battle-map";
        String pluginId = "custom.protocol-lua";
        writePlugin(pluginId, List.of(), """
                tv.register_battle_map_source({
                  id = "custom-protocol-battle-map",
                  capture = function()
                    return {
                      dimension = "minecraft:overworld",
                      observedAt = 123456,
                      coordinateSpace = "absolute_chunk",
                      mapSize = 0,
                      anchorRow = 0,
                      anchorColumn = 0,
                      cells = {
                        {x = 12, z = -4, symbol = "A", color = "#112233"},
                        {x = 13, z = -4, symbol = "B", color = "#445566"}
                      }
                    }
                  end
                })
                """, capabilityId);

        IntegrationRegistry luaRegistry = completeRegistry();
        Config luaConfig = battleMapConfig(temporary.resolve("lua-config.json"), capabilityId);
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), luaRegistry, luaConfig);
        assertEquals(IntegrationImplementationSource.LUA,
                manager.snapshot(pluginId).capabilities().get(0).implementationSource());

        BattleMapSourceSnapshot luaSnapshot = luaRegistry.activeBattleMapSource(capabilityId)
                .capture().orElseThrow();
        IntegrationRegistry javaRegistry = completeRegistry();
        javaRegistry.registerNative(new IntegrationCapability(
                capabilityId, IntegrationRole.BATTLE_MAP_SOURCE.id(),
                IntegrationSupportStatus.AVAILABLE, "", pluginId,
                IntegrationImplementationSource.JAVA_NATIVE, PluginRuntimeStatus.ACTIVE),
                new BattleMapSource() {
                    @Override public String id() { return capabilityId; }
                    @Override public Optional<BattleMapSourceSnapshot> capture() {
                        return Optional.of(luaSnapshot);
                    }
                });
        javaRegistry.setPluginRuntime(pluginId, PluginRuntimeStatus.ACTIVE, "");

        ClientWorldSnapshot world = testWorld();
        RecordingBattleNetwork luaNetwork = new RecordingBattleNetwork(new TestRuntime(temporary));
        RecordingBattleNetwork javaNetwork = new RecordingBattleNetwork(new TestRuntime(temporary));
        new BattleMapCoordinator(luaConfig, luaNetwork, luaRegistry).tick(true, world);
        new BattleMapCoordinator(
                battleMapConfig(temporary.resolve("java-config.json"), capabilityId),
                javaNetwork, javaRegistry).tick(true, world);

        assertNotNull(luaNetwork.observation);
        assertNotNull(javaNetwork.observation);
        assertEquals(withoutParseTime(luaNetwork.observation), withoutParseTime(javaNetwork.observation));
        assertEquals(Set.of(
                        "mode", "dimension", "mapSize", "anchorRow", "anchorCol",
                        "snapshotObservedAt", "parsedAt", "candidates", "cells"),
                luaNetwork.observation.keySet());
        manager.shutdown();
    }

    @Test
    void fallbackEntrypointsKeepUnimplementedPlatformMatrixVisible() {
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "neoforge", "1.21.8"), completeRegistry(),
                Config.load(temporary.resolve("config.json")));

        for (String pluginId : List.of(
                IntegrationIds.PLUGIN_JOURNEYMAP, IntegrationIds.PLUGIN_XAERO,
                IntegrationIds.PLUGIN_SIMMC)) {
            PluginSnapshot snapshot = manager.snapshot(pluginId);
            assertEquals(PluginRuntimeStatus.ACTIVE, snapshot.runtimeStatus());
            assertTrue(snapshot.capabilities().stream().allMatch(value ->
                    value.status() == IntegrationSupportStatus.NOT_IMPLEMENTED
                            && value.implementationSource() == IntegrationImplementationSource.PLACEHOLDER
                            && !value.detail().isBlank()), pluginId + ": " + snapshot.capabilities());
        }
        assertEquals(IntegrationSupportStatus.AVAILABLE,
                manager.snapshot(IntegrationIds.PLUGIN_NODEMC).capabilities().get(0).status());
        manager.shutdown();
    }

    @Test
    void unknownFabricVersionUsesExplicitUnsupportedFallback() {
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "9.9.9"), completeRegistry(),
                Config.load(temporary.resolve("config.json")));

        for (String pluginId : List.of(
                IntegrationIds.PLUGIN_JOURNEYMAP, IntegrationIds.PLUGIN_XAERO,
                IntegrationIds.PLUGIN_SIMMC)) {
            assertTrue(manager.snapshot(pluginId).capabilities().stream().allMatch(value ->
                    value.status() == IntegrationSupportStatus.UNSUPPORTED_VERSION
                            && !value.detail().isBlank()), pluginId);
        }
        manager.shutdown();
    }

    @Test
    void widenedFabricEntrypointsLoadOnlyVerifiedAdapters() {
        for (String version : List.of("1.21.6", "1.21.7", "26.1", "26.1.1", "26.1.2", "26.2")) {
            IntegrationPluginManager manager = new IntegrationPluginManager(
                    new EnvironmentRuntime(temporary, "fabric", version), completeRegistry(),
                    Config.load(temporary.resolve("config-" + version + ".json")));

            for (String pluginId : List.of(
                    IntegrationIds.PLUGIN_JOURNEYMAP, IntegrationIds.PLUGIN_XAERO,
                    IntegrationIds.PLUGIN_NODEMC)) {
                PluginSnapshot snapshot = manager.snapshot(pluginId);
                assertEquals(PluginRuntimeStatus.ACTIVE, snapshot.runtimeStatus(),
                        version + " " + pluginId + ": " + snapshot.detail());
                assertTrue(snapshot.capabilities().stream().allMatch(value ->
                        value.implementationSource() == IntegrationImplementationSource.LUA),
                        version + " " + pluginId);
            }
            PluginSnapshot simmc = manager.snapshot(IntegrationIds.PLUGIN_SIMMC);
            assertEquals(PluginRuntimeStatus.ACTIVE, simmc.runtimeStatus(), version);
            assertTrue(simmc.capabilities().stream().allMatch(value ->
                    value.status() == IntegrationSupportStatus.UNSUPPORTED_VERSION
                            && !value.detail().isBlank()), version);
            manager.shutdown();
        }
    }

    @Test
    void simMcLuaAdapterReflectsRegionsIntoAbsoluteCells() {
        com.simmc.mod.region.RegionManagerImpl managerObject = new com.simmc.mod.region.RegionManagerImpl();
        managerObject.put(new com.simmc.mod.region.TestChunkPos(12, -4),
                new com.simmc.mod.region.Region(0x12ABEF, true));
        managerObject.put(new com.simmc.mod.region.TestChunkPos(13, -4),
                new com.simmc.mod.region.Region(0x010203, false));
        managerObject.put(new net.minecraft.class_1923(-27, 31),
                new com.simmc.mod.region.Region(0xFEDCBA, false));
        com.simmc.mod.region.RegionManager.regionManager = managerObject;
        PluginHostAccess host = new PluginHostAccess(
                () -> new TestWorld("minecraft:overworld"), null, null, null);
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager pluginManager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "1.21.8", Set.of("smcmod")),
                registry, Config.load(temporary.resolve("config.json")), host);

        PluginSnapshot simmc = pluginManager.snapshot(IntegrationIds.PLUGIN_SIMMC);
        assertEquals(IntegrationSupportStatus.AVAILABLE, simmc.capabilities().get(0).status(), simmc.detail());
        BattleMapSourceSnapshot snapshot = registry.activeBattleMapSource(IntegrationIds.SIMMC_BATTLE_MAP)
                .capture().orElseThrow();
        assertEquals(BattleMapSourceSnapshot.CoordinateSpace.ABSOLUTE_CHUNK, snapshot.coordinateSpace());
        assertEquals(3, snapshot.cells().size());
        assertTrue(snapshot.cells().stream().anyMatch(cell -> cell.chunkX() == 12 && cell.chunkZ() == -4
                && "#12ABEF".equals(cell.colorRaw()) && "╫".equals(cell.symbol())));
        assertTrue(snapshot.cells().stream().anyMatch(cell -> cell.chunkX() == -27 && cell.chunkZ() == 31
                && "#FEDCBA".equals(cell.colorRaw()) && cell.symbol().isEmpty()));
        pluginManager.shutdown();
        com.simmc.mod.region.RegionManager.regionManager = null;
    }

    @Test
    void journeyMapLuaAdapterRecoversServiceAndConvertsBothDirections() {
        AtomicReference<Object> service = new AtomicReference<>();
        UUID localId = UUID.randomUUID();
        PluginHostAccess host = new PluginHostAccess(
                () -> new TestJourneyWorld(localId, "minecraft:overworld"), null, null, null,
                Map.of("journeymap.client_api", service::get));
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager pluginManager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "1.21.8", Set.of("journeymap")),
                registry, Config.load(temporary.resolve("config.json")), host);

        PluginSnapshot pendingJourneyMap = pluginManager.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP);
        assertEquals(List.of("show_remote_players"), pendingJourneyMap.visibleSettingDefinitions().stream()
                .map(PluginManifest.SettingDefinition::key).toList());
        assertTrue(pendingJourneyMap.capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.ENTRYPOINT_NOT_READY));
        JourneyMapApiStub api = new JourneyMapApiStub();
        service.set(api);
        assertTrue(pluginManager.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP).capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.AVAILABLE));

        UUID remoteId = UUID.randomUUID();
        Map<UUID, RemotePlayerInfo> players = Map.of(remoteId,
                new RemotePlayerInfo(remoteId, new Position3D(10.8, 70.2, -3.1),
                        "minecraft:overworld", "Remote"));
        registry.activeRemotePlayerProjections().stream()
                .filter(value -> value.id().startsWith("journeymap-"))
                .forEach(value -> value.sync(players, true));
        assertEquals(1, api.waypoints().size(),
                "the merged API family must project each player to one native waypoint");

        SharedWaypointMapAdapter adapter = registry.activeSharedWaypointAdapters().stream()
                .filter(value -> IntegrationIds.JOURNEYMAP_WAYPOINTS.equals(value.id()))
                .findFirst().orElseThrow();
        adapter.upsertRemoteWaypoint(new MapWaypointCommand(
                "relay-waypoint", "Relay", "R", 4, 65, 8,
                "minecraft:overworld", 0x55FF55));
        journeymap.api.v2.common.waypoint.Waypoint local =
                new journeymap.api.v2.common.waypoint.Waypoint(
                        "other-mod", new net.minecraft.util.math.BlockPos(1, 2, 3),
                        "Local", "minecraft:overworld");
        api.addWaypoint("other-mod", local);
        List<NativeMapWaypointSnapshot> localValues = adapter.listLocalWaypoints();
        assertEquals(1, localValues.size());
        assertEquals("Local", localValues.get(0).name());
        assertEquals(1, localValues.get(0).x());

        pluginManager.shutdown();
        assertEquals(1, api.waypoints().size(), "disable must leave the user's local waypoint alone");
    }

    @Test
    void journeyMapEnumServiceRemainsCallableUserdata() {
        EnumJourneyMapApiStub api = EnumJourneyMapApiStub.INSTANCE;
        api.reset();
        api.addWaypoint("other-mod", new journeymap.api.v2.common.waypoint.Waypoint(
                "other-mod", new net.minecraft.util.math.BlockPos(2, 70, 5),
                "Enum local", "minecraft:overworld"));
        PluginHostAccess host = new PluginHostAccess(
                () -> new TestJourneyWorld(UUID.randomUUID(), "minecraft:overworld"), null, null, null,
                Map.of("journeymap.client_api", () -> api));
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager pluginManager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "1.21.8", Set.of("journeymap")),
                registry, Config.load(temporary.resolve("enum-config.json")), host);

        SharedWaypointMapAdapter adapter = registry.activeSharedWaypointAdapters().stream()
                .filter(value -> IntegrationIds.JOURNEYMAP_WAYPOINTS.equals(value.id()))
                .findFirst().orElseThrow();
        assertEquals("Enum local", adapter.listLocalWaypoints().get(0).name(),
                "services.get() must not stringify enum singleton services");
        pluginManager.shutdown();
        api.reset();
    }

    @Test
    void journeyMapV1UsesOneDisplayableWaypointAndCleansUp() {
        JourneyMapV1ApiStub api = new JourneyMapV1ApiStub();
        UUID localId = UUID.randomUUID();
        PluginHostAccess host = new PluginHostAccess(
                () -> new TestJourneyWorld(localId, "minecraft:overworld"), null, null, null,
                Map.of("journeymap.client_api", () -> api));
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "1.20.1", Set.of("journeymap")),
                registry, Config.load(temporary.resolve("v1-config.json")), host);

        UUID remoteId = UUID.randomUUID();
        registry.activeRemotePlayerProjections().stream()
                .filter(value -> value.id().startsWith("journeymap-"))
                .forEach(value -> value.sync(Map.of(remoteId,
                        new RemotePlayerInfo(remoteId, new Position3D(8, 70, -4),
                                "minecraft:overworld", "V1 Remote")), true));
        assertEquals(1, api.waypoints().size());
        assertEquals(List.of("show_remote_players"), manager.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP)
                .visibleSettingDefinitions().stream().map(PluginManifest.SettingDefinition::key).toList());
        manager.shutdown();
        assertTrue(api.waypoints().isEmpty());
    }

    @Test
    void journeyMap262UsesRenamedWaypointFactory() {
        JourneyMapApiStub api = new JourneyMapApiStub();
        UUID localId = UUID.randomUUID();
        PluginHostAccess host = new PluginHostAccess(
                () -> new TestJourneyWorld(localId, "minecraft:overworld"), null, null, null,
                Map.of("journeymap.client_api", () -> api));
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager pluginManager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "26.2", Set.of("journeymap")),
                registry, Config.load(temporary.resolve("config.json")), host);

        UUID remoteId = UUID.randomUUID();
        registry.activeRemotePlayerProjections().stream()
                .filter(value -> value.id().startsWith("journeymap-"))
                .forEach(value -> value.sync(Map.of(remoteId,
                        new RemotePlayerInfo(remoteId, new Position3D(4.5, 70, -8.5),
                                "minecraft:overworld", "26.2 Remote")), true));

        assertEquals(2, api.waypoints().size());
        PluginSnapshot snapshot = pluginManager.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP);
        assertEquals(Set.of("show_map_markers", "show_beacons"), snapshot.visibleSettingDefinitions().stream()
                .map(PluginManifest.SettingDefinition::key).collect(java.util.stream.Collectors.toSet()));
        assertTrue(snapshot.capabilities().stream()
                .allMatch(value -> value.status() == IntegrationSupportStatus.AVAILABLE));
        pluginManager.shutdown();
    }

    @Test
    void journeyMapRetainsSuppressedDeletesForProjectionRetry() {
        JourneyMapApiStub api = new JourneyMapApiStub();
        UUID localId = UUID.randomUUID();
        PluginHostAccess host = new PluginHostAccess(
                () -> new TestJourneyWorld(localId, "minecraft:overworld"), null, null, null,
                Map.of("journeymap.client_api", () -> api));
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "26.1.2", Set.of("journeymap")),
                registry, Config.load(temporary.resolve("retry-config.json")), host);
        UUID remoteId = UUID.randomUUID();
        Map<UUID, RemotePlayerInfo> players = Map.of(remoteId,
                new RemotePlayerInfo(remoteId, new Position3D(4, 70, 8),
                        "minecraft:overworld", "Retry Remote"));
        List<RemotePlayerProjection> projections = registry.activeRemotePlayerProjections().stream()
                .filter(value -> value.id().startsWith("journeymap-"))
                .toList();
        projections.forEach(value -> value.sync(players, true));
        assertEquals(2, api.waypoints().size());

        api.suppressNextRemovals(2);
        projections.forEach(RemotePlayerProjection::clear);
        assertEquals(2, api.waypoints().size());
        assertTrue(projections.stream().anyMatch(RemotePlayerProjection::needsReconcile));

        projections.forEach(RemotePlayerProjection::clear);
        assertTrue(api.waypoints().isEmpty());
        assertFalse(projections.stream().anyMatch(RemotePlayerProjection::needsReconcile));
        manager.shutdown();
    }

    @Test
    void exactFabricMapPluginRoutesMatchOfficialArtifactMatrix() {
        List<String> releases = List.of(
                "1.18", "1.18.1", "1.18.2", "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
                "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
                "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6",
                "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
                "26.1", "26.1.1", "26.1.2", "26.2");
        Set<String> journeyUnsupported = Set.of("1.18", "1.18.1", "1.21.2");
        Set<String> xaeroUnsupported = Set.of("1.18", "1.18.1", "1.19", "1.21.2");

        for (String release : releases) {
            IntegrationPluginManager manager = new IntegrationPluginManager(
                    new EnvironmentRuntime(temporary, "fabric", release), completeRegistry(),
                    Config.load(temporary.resolve("matrix-" + release + ".json")));
            IntegrationSupportStatus journeyExpected = journeyUnsupported.contains(release)
                    ? IntegrationSupportStatus.UNSUPPORTED_VERSION : IntegrationSupportStatus.MOD_NOT_INSTALLED;
            IntegrationSupportStatus xaeroExpected = xaeroUnsupported.contains(release)
                    ? IntegrationSupportStatus.UNSUPPORTED_VERSION : IntegrationSupportStatus.MOD_NOT_INSTALLED;
            assertTrue(manager.snapshot(IntegrationIds.PLUGIN_JOURNEYMAP).capabilities().stream()
                    .allMatch(value -> value.status() == journeyExpected), "JourneyMap " + release);
            assertTrue(manager.snapshot(IntegrationIds.PLUGIN_XAERO).capabilities().stream()
                    .allMatch(value -> value.status() == xaeroExpected), "Xaero " + release);
            manager.shutdown();
        }
    }

    @Test
    void xaeroLuaAdapterRegistersTrackerAndOwnsOnlyRelayWaypoints() {
        xaero.map.WorldMap.playerTrackerSystemManager = new xaero.map.PlayerTrackerSystemManager();
        xaero.common.XaeroMinimapSession.reset();
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager pluginManager = new IntegrationPluginManager(
                new EnvironmentRuntime(temporary, "fabric", "1.21.8",
                        Set.of("xaeroworldmap", "xaerominimap")),
                registry, Config.load(temporary.resolve("config.json")));

        PluginSnapshot xaeroPlugin = pluginManager.snapshot(IntegrationIds.PLUGIN_XAERO);
        assertTrue(xaeroPlugin.capabilities().stream().allMatch(value ->
                value.status() == IntegrationSupportStatus.AVAILABLE), xaeroPlugin.capabilities().toString());
        UUID remoteId = UUID.randomUUID();
        RemotePlayerInfo player = new RemotePlayerInfo(remoteId, new Position3D(8.5, 64, -2.25),
                "minecraft:overworld", "Xaero Remote");
        registry.activeRemotePlayerProjections().stream()
                .filter(value -> IntegrationIds.XAERO_WORLDMAP.equals(value.id()))
                .findFirst().orElseThrow().sync(Map.of(remoteId, player), true);
        xaero.map.radar.tracker.system.IPlayerTrackerSystem tracker =
                xaero.map.WorldMap.playerTrackerSystemManager.system();
        assertNotNull(tracker);
        Object tracked = tracker.getTrackedPlayerIterator().next();
        assertEquals(remoteId, tracker.getReader().getId(tracked));
        assertEquals(8.5, tracker.getReader().getX(tracked));

        SharedWaypointMapAdapter adapter = registry.activeSharedWaypointAdapters().stream()
                .filter(value -> IntegrationIds.XAERO_MINIMAP.equals(value.id()))
                .findFirst().orElseThrow();
        xaero.common.minimap.waypoints.Waypoint local = new xaero.common.minimap.waypoints.Waypoint(
                1, 70, 2, "Local Xaero", "L", 0x112233);
        xaero.common.XaeroMinimapSession.waypointSet().add(local);
        adapter.upsertRemoteWaypoint(new MapWaypointCommand(
                "relay-xaero", "Relay Xaero", "R", 3, 71, 4,
                "minecraft:overworld", 0x55FF55));
        assertEquals(2, xaero.common.XaeroMinimapSession.waypointSet().getWaypoints().size());
        List<NativeMapWaypointSnapshot> listed = adapter.listLocalWaypoints();
        assertEquals(1, listed.size());
        assertEquals("Local Xaero", listed.get(0).name());

        pluginManager.shutdown();
        assertEquals(List.of(local), xaero.common.XaeroMinimapSession.waypointSet().getWaypoints());
        assertFalse(tracker.getTrackedPlayerIterator().hasNext());
    }

    @Test
    void managedDirectoryUninstallDetachesImmediatelyAndCanBeRestoredWithoutOverwrite() throws Exception {
        writePlugin("custom.managed", List.of(), """
                tv.register_battle_map_source({id="custom-managed-map", capture=function() return nil end})
                """, "custom-managed-map");
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));

        assertNotNull(registry.activeBattleMapSource("custom-managed-map"));
        PluginFileOperationResult uninstall = manager.uninstall("custom.managed");
        assertTrue(uninstall.succeeded(), uninstall.detail());
        assertFalse(Files.exists(temporary.resolve("team-view-relay/plugins/custom.managed")));
        assertNull(registry.activeBattleMapSource("custom-managed-map"));
        PluginSnapshot pending = manager.snapshot("custom.managed");
        assertTrue(pending.pendingRemoval());
        assertEquals(PluginRuntimeStatus.PENDING_RESTART, pending.runtimeStatus());
        assertEquals(IntegrationSupportStatus.AVAILABLE, pending.capabilities().get(0).status());

        DisabledPluginSnapshot disabled = assertSingleDisabled(manager, false);
        Path target = temporary.resolve("team-view-relay/plugins/custom.managed");
        Files.createDirectories(target);
        PluginFileOperationResult conflict = manager.restore(disabled.storageId());
        assertEquals(PluginFileOperationResult.Code.TARGET_EXISTS, conflict.code());
        assertEquals(target, conflict.path());
        Files.delete(target);

        PluginFileOperationResult restored = manager.restore(disabled.storageId());
        assertTrue(restored.succeeded(), restored.detail());
        assertTrue(Files.isDirectory(target));
        assertTrue(manager.disabledSnapshots().isEmpty());
        manager.shutdown();

        IntegrationPluginManager reloaded = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        assertFalse(reloaded.snapshot("custom.managed").enabled());
        assertEquals(PluginRuntimeStatus.DISABLED, reloaded.snapshot("custom.managed").runtimeStatus());
        reloaded.shutdown();
    }

    @Test
    void restartModeArchiveKeepsRunningUntilRestartAndRoundTripsThroughDisabledStorage() throws Exception {
        writeArchivePlugin("custom.archive", "custom-archive-map", "restart");
        IntegrationRegistry registry = completeRegistry();
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), registry, Config.load(temporary.resolve("config.json")));

        assertNotNull(registry.activeBattleMapSource("custom-archive-map"));
        PluginFileOperationResult uninstall = manager.uninstall("custom.archive");
        assertTrue(uninstall.succeeded(), uninstall.detail());
        assertNotNull(registry.activeBattleMapSource("custom-archive-map"));
        assertEquals(PluginRuntimeStatus.PENDING_RESTART, manager.snapshot("custom.archive").runtimeStatus());
        DisabledPluginSnapshot disabled = assertSingleDisabled(manager, true);
        assertEquals("custom.archive.tvr-plugin", disabled.originalFileName());
        assertTrue(Files.isRegularFile(disabled.storagePath().resolve("payload.tvr-plugin")));

        PluginFileOperationResult restored = manager.restore(disabled.storageId());
        assertTrue(restored.succeeded(), restored.detail());
        assertTrue(Files.isRegularFile(
                temporary.resolve("team-view-relay/plugins/custom.archive.tvr-plugin")));
        manager.shutdown();
    }

    @Test
    void builtinsCannotBeUninstalledAndDisabledEntryDeletionIsExact() throws Exception {
        IntegrationPluginManager manager = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config.json")));
        assertEquals(PluginFileOperationResult.Code.BUILTIN_READ_ONLY,
                manager.uninstall(IntegrationIds.PLUGIN_NODEMC).code());
        assertEquals(PluginFileOperationResult.Code.INVALID_DISABLED_ENTRY,
                manager.deleteDisabled("../plugins").code());
        assertNotNull(manager.snapshot(IntegrationIds.PLUGIN_NODEMC));
        manager.shutdown();

        writePlugin("custom.delete", List.of(), """
                tv.register_battle_map_source({id="custom-delete-map", capture=function() return nil end})
                """, "custom-delete-map");
        IntegrationPluginManager withCustom = new IntegrationPluginManager(
                new TestRuntime(temporary), completeRegistry(), Config.load(temporary.resolve("config-2.json")));
        assertTrue(withCustom.uninstall("custom.delete").succeeded());
        DisabledPluginSnapshot disabled = assertSingleDisabled(withCustom, false);
        Path storage = disabled.storagePath();
        assertTrue(withCustom.deleteDisabled(disabled.storageId()).succeeded());
        assertFalse(Files.exists(storage));
        assertTrue(withCustom.disabledSnapshots().isEmpty());
        withCustom.shutdown();
    }

    private static DisabledPluginSnapshot assertSingleDisabled(
            IntegrationPluginManager manager, boolean archive) {
        List<DisabledPluginSnapshot> disabled = manager.disabledSnapshots();
        assertEquals(1, disabled.size());
        assertEquals(archive, disabled.get(0).archive());
        assertTrue(Files.isRegularFile(disabled.get(0).storagePath().resolve("disabled-plugin.json")));
        return disabled.get(0);
    }

    private static ScoreboardSnapshot.Line scoreboardLine(String text, String color) {
        return new ScoreboardSnapshot.Line(text, List.of(new ScoreboardSnapshot.Run(text, color)));
    }

    private static Config battleMapConfig(Path path, String sourceId) {
        Config config = Config.load(path);
        config.setBattleMapSyncEnabled(true);
        config.setBattleMapSourceId(sourceId);
        config.setBattleMapUpdateIntervalTicks(1);
        return config;
    }

    private static Map<String, Object> withoutParseTime(Map<String, Object> observation) {
        Map<String, Object> normalized = new HashMap<>(observation);
        normalized.remove("parsedAt");
        return normalized;
    }

    private static ClientWorldSnapshot testWorld() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Position3D position = new Position3D(32.0, 64.0, -16.0);
        return new ClientWorldSnapshot(
                playerId, "Local", true, "minecraft:overworld", -64,
                position, position, new Position3D(0, 0, 1),
                new Position3D(0, 1, 0), List.of(), List.of());
    }

    private static final class RecordingBattleNetwork extends NetworkManager {
        private Map<String, Object> observation;

        private RecordingBattleNetwork(RuntimeGateway runtime) {
            super(new HashMap<>(), runtime, noTransport());
        }

        @Override public boolean isConnected() { return true; }

        @Override
        public void sendBattleMapObservation(UUID submitPlayerId, Map<String, Object> value) {
            observation = Map.copyOf(value);
        }

        private static TransportProcess noTransport() {
            return (uri, options, listener) -> null;
        }
    }

    private void writeArchivePlugin(String id, String capabilityId, String hotToggle) throws Exception {
        Path plugins = temporary.resolve("team-view-relay/plugins");
        Files.createDirectories(plugins);
        Path archive = plugins.resolve(id + ".tvr-plugin");
        String manifest = """
                {
                  "schemaVersion": 1,
                  "apiVersion": "1",
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "entry": "main.lua",
                  "defaultEnabled": true,
                  "hotToggle": "%s",
                  "provides": [
                    {"id":"%s", "role":"battle-map-source", "name":"Custom"}
                  ]
                }
                """.formatted(id, id, hotToggle, capabilityId);
        String script = "tv.register_battle_map_source({id=\"" + capabilityId
                + "\", capture=function() return nil end})";
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            writeZipEntry(output, "plugin.json", manifest);
            writeZipEntry(output, "main.lua", script);
        }
    }

    private static void writeZipEntry(ZipOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private void writePlugin(String id, List<String> dependencies, String script, String capabilityId) throws Exception {
        writePlugin(id, dependencies, script, capabilityId, "managed");
    }

    private void writePlugin(
            String id, List<String> dependencies, String script, String capabilityId, String hotToggle) throws Exception {
        Path plugin = temporary.resolve("team-view-relay/plugins").resolve(id);
        Files.createDirectories(plugin);
        String dependencyJson = dependencies.stream().map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        Files.writeString(plugin.resolve("plugin.json"), """
                {
                  "schemaVersion": 1,
                  "apiVersion": "1",
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "entry": "main.lua",
                  "defaultEnabled": true,
                  "hotToggle": "%s",
                  "dependencies": [%s],
                  "provides": [
                    {"id":"%s", "role":"battle-map-source", "name":"Custom"}
                  ]
                }
                """.formatted(id, id, hotToggle, dependencyJson, capabilityId));
        Files.writeString(plugin.resolve("main.lua"), script);
    }

    private static IntegrationRegistry completeRegistry() {
        IntegrationRegistry registry = new IntegrationRegistry();
        register(registry, IntegrationIds.JOURNEYMAP_PLAYERS, IntegrationRole.REMOTE_PLAYER, new Remote(IntegrationIds.JOURNEYMAP_PLAYERS));
        register(registry, IntegrationIds.JOURNEYMAP_BEACONS, IntegrationRole.REMOTE_PLAYER, new Remote(IntegrationIds.JOURNEYMAP_BEACONS));
        register(registry, IntegrationIds.JOURNEYMAP_WAYPOINTS, IntegrationRole.SHARED_WAYPOINT, new Waypoints(IntegrationIds.JOURNEYMAP_WAYPOINTS));
        register(registry, IntegrationIds.XAERO_WORLDMAP, IntegrationRole.REMOTE_PLAYER, new Remote(IntegrationIds.XAERO_WORLDMAP));
        register(registry, IntegrationIds.XAERO_MINIMAP, IntegrationRole.SHARED_WAYPOINT, new Waypoints(IntegrationIds.XAERO_MINIMAP));
        register(registry, IntegrationIds.NODEMC_BATTLE_MAP, IntegrationRole.BATTLE_MAP_SOURCE, new Battle(IntegrationIds.NODEMC_BATTLE_MAP));
        register(registry, IntegrationIds.SIMMC_BATTLE_MAP, IntegrationRole.BATTLE_MAP_SOURCE, new Battle(IntegrationIds.SIMMC_BATTLE_MAP));
        return registry;
    }

    private static void register(IntegrationRegistry registry, String id, IntegrationRole role, Object implementation) {
        registry.registerNative(new IntegrationCapability(id, role.id(), IntegrationSupportStatus.AVAILABLE, "",
                IntegrationIds.pluginIdForCapability(id), IntegrationImplementationSource.JAVA_NATIVE,
                PluginRuntimeStatus.DISABLED), implementation);
    }

    private record Remote(String id) implements RemotePlayerProjection {
        @Override public boolean isAvailable() { return true; }
        @Override public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) { }
    }

    private record Waypoints(String id) implements SharedWaypointMapAdapter {
        @Override public boolean isAvailable() { return true; }
        @Override public List<NativeMapWaypointSnapshot> listLocalWaypoints() { return List.of(); }
        @Override public void upsertRemoteWaypoint(MapWaypointCommand command) { }
        @Override public void deleteRemoteWaypoint(String waypointId) { }
        @Override public void clearRemoteWaypoints() { }
    }

    private record Battle(String id) implements BattleMapSource {
        @Override public Optional<BattleMapSourceSnapshot> capture() { return Optional.empty(); }
    }

    private record TestRuntime(Path root) implements RuntimeGateway {
        @Override public String getCurrentDimensionId() { return "minecraft:overworld"; }
        @Override public UUID getLocalPlayerId() { return UUID.randomUUID(); }
        @Override public String getClientProgramVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.21.8"; }
        @Override public String getLoaderId() { return "fabric"; }
        @Override public String getClientProtocolVersion() { return "test"; }
        @Override public String getClientMinCompatibleProtocolVersion() { return "test"; }
        @Override public String getServerProtocolFallbackVersion() { return "test"; }
        @Override public String getProgramVersionUnknown() { return "unknown"; }
        @Override public Path getLogsDirectory() { return root.resolve("logs"); }
        @Override public Path getConfigDirectory() { return root; }
        @Override public Class<?> resolvePluginClass(String binaryName) throws ClassNotFoundException {
            return switch (binaryName) {
                case "optional.mod.HiddenApi" -> HiddenOptionalApi.class;
                case "optional.mod.HiddenCallback" -> HiddenOptionalCallback.class;
                default -> RuntimeGateway.super.resolvePluginClass(binaryName);
            };
        }
    }

    private record ClipboardRuntime(Path root, AtomicReference<String> clipboard) implements RuntimeGateway {
        @Override public String getCurrentDimensionId() { return "minecraft:overworld"; }
        @Override public UUID getLocalPlayerId() { return UUID.randomUUID(); }
        @Override public String getClientProgramVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.21.8"; }
        @Override public String getLoaderId() { return "fabric"; }
        @Override public Path getLogsDirectory() { return root.resolve("logs"); }
        @Override public Path getConfigDirectory() { return root; }
        @Override public boolean copyTextToClipboard(String text) {
            clipboard.set(text);
            return true;
        }
    }

    public static final class HiddenOptionalApi {
        public static final int CODE = 42;
        private final String value;
        public HiddenOptionalApi(String value) { this.value = value; }
        public static int staticValue() { return CODE; }
        public String value() { return value; }
    }

    public interface HiddenOptionalCallback {
        String apply(String value);
    }

    private record TestWorld(String dimension) { }
    private record TestJourneyWorld(UUID localPlayerId, String dimension) { }

    private record EnvironmentRuntime(
            Path root, String loader, String minecraftVersion, Set<String> loadedMods)
            implements RuntimeGateway {
        private EnvironmentRuntime(Path root, String loader, String minecraftVersion) {
            this(root, loader, minecraftVersion, Set.of());
        }
        @Override public String getCurrentDimensionId() { return "minecraft:overworld"; }
        @Override public UUID getLocalPlayerId() { return UUID.randomUUID(); }
        @Override public String getClientProgramVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return minecraftVersion; }
        @Override public String getLoaderId() { return loader; }
        @Override public boolean isModLoaded(String modId) { return loadedMods.contains(modId); }
        @Override public String getModVersion(String modId) {
            return loadedMods.contains(modId) ? "test-version" : "unknown";
        }
        @Override public Object getPluginService(String serviceId) {
            return MinecraftClientObjects.SERVICE_ID.equals(serviceId) ? TEST_CLIENT_OBJECTS : null;
        }
        @Override public String getClientProtocolVersion() { return "test"; }
        @Override public String getClientMinCompatibleProtocolVersion() { return "test"; }
        @Override public String getServerProtocolFallbackVersion() { return "test"; }
        @Override public String getProgramVersionUnknown() { return "unknown"; }
        @Override public Path getLogsDirectory() { return root.resolve("logs"); }
        @Override public Path getConfigDirectory() { return root; }
    }
}
