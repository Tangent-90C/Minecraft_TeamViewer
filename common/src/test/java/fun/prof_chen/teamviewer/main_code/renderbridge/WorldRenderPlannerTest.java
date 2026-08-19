package fun.prof_chen.teamviewer.main_code.renderbridge;

import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.model.ClientWorldSnapshot;
import fun.prof_chen.teamviewer.main_code.client.model.PlayerRelationView;
import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.Position3D;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;
import fun.prof_chen.teamviewer.main_code.model.SharedWaypointInfo;
import fun.prof_chen.teamviewer.main_code.renderbridge.core.WorldRenderPlanner;
import fun.prof_chen.teamviewer.main_code.renderbridge.model.WorldRenderCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRenderPlannerTest {
    private static final UUID LOCAL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void plansTeamPlayerCommandsAndAllWaypointStyles() {
        Config config = new Config();
        config.setShowBoxes(true);
        config.setShowLines(true);
        config.setFriendlyTeamColor(0xFF123456);
        config.setXrayMarkersAndBoxes(false);
        WorldRenderPlanner planner = new WorldRenderPlanner(config,
                ignored -> new PlayerRelationView(PlayerRelation.FRIENDLY, config.getFriendlyTeamColor(), true),
                null);
        RemotePlayerInfo player = new RemotePlayerInfo(REMOTE, new Position3D(10, 64, 10),
                "minecraft:overworld", "remote");

        var playerFrame = planner.plan(true, world(), Map.of(REMOTE, player), Map.of());
        assertEquals(2, playerFrame.commands().size());
        assertTrue(playerFrame.commands().stream().allMatch(WorldRenderCommand::depthTest));
        assertTrue(playerFrame.commands().stream().allMatch(command -> command.color() == 0xFF123456));

        SharedWaypointInfo waypoint = new SharedWaypointInfo("waypoint", REMOTE, "remote", "mark", "!",
                20, 64, 20, "minecraft:overworld", 0xFFFF8800, 1L,
                null, null, null, null, "quick", null, null);
        config.setShowBoxes(false);
        config.setShowLines(false);
        config.setWaypointUiStyle(Config.WAYPOINT_UI_BEACON);
        assertTrue(planner.plan(true, world(), Map.of(), Map.of("waypoint", waypoint)).commands().stream()
                .anyMatch(WorldRenderCommand.VerticalBeam.class::isInstance));
        config.setWaypointUiStyle(Config.WAYPOINT_UI_RING);
        assertTrue(planner.plan(true, world(), Map.of(), Map.of("waypoint", waypoint)).commands().stream()
                .anyMatch(WorldRenderCommand.Circle.class::isInstance));
        config.setWaypointUiStyle(Config.WAYPOINT_UI_PIN);
        assertTrue(planner.plan(true, world(), Map.of(), Map.of("waypoint", waypoint)).commands().stream()
                .anyMatch(WorldRenderCommand.Line.class::isInstance));
    }

    @Test
    void distinguishesExplicitNeutralFromUnresolvedPlayers() {
        Config config = new Config();
        config.setShowBoxes(true);
        config.setBoxColor(0x80112233);
        config.setNeutralTeamColor(0xFF445566);
        RemotePlayerInfo player = new RemotePlayerInfo(REMOTE, new Position3D(10, 64, 10),
                "minecraft:overworld", "remote");

        WorldRenderPlanner unresolved = new WorldRenderPlanner(config,
                ignored -> new PlayerRelationView(PlayerRelation.NEUTRAL, config.getNeutralTeamColor(), false), null);
        assertEquals(0x80112233, unresolved.plan(true, world(), Map.of(REMOTE, player), Map.of())
                .commands().get(0).color());

        WorldRenderPlanner neutral = new WorldRenderPlanner(config,
                ignored -> new PlayerRelationView(PlayerRelation.NEUTRAL, config.getNeutralTeamColor(), true), null);
        assertEquals(0xFF445566, neutral.plan(true, world(), Map.of(REMOTE, player), Map.of())
                .commands().get(0).color());
    }

    @Test
    void lastSeenRenderingIsOptInAndIncludesUtcVectorLabel() {
        Config config = new Config();
        LastSeenPlayerInfo player = new LastSeenPlayerInfo(REMOTE, new Position3D(10, 64, 10),
                "minecraft:overworld", "remote", 1_700_000_004_000L,
                1_700_000_000_000L, 1_700_000_005_000L);
        WorldRenderPlanner planner = new WorldRenderPlanner(config, ignored -> null, null);

        assertTrue(planner.plan(true, world(), Map.of(), Map.of(REMOTE, player), Map.of())
                .commands().isEmpty());
        config.setShowLastSeenPlayers(true);
        var commands = planner.plan(true, world(), Map.of(), Map.of(REMOTE, player), Map.of()).commands();
        assertTrue(commands.stream().anyMatch(WorldRenderCommand.Box.class::isInstance));
        assertTrue(commands.stream().filter(WorldRenderCommand.Line.class::isInstance).count() > 2,
                "tracer plus vectorized name/UTC time should be present");
    }

    private static ClientWorldSnapshot world() {
        return new ClientWorldSnapshot(LOCAL, "local", true, "minecraft:overworld", -64,
                new Position3D(0, 64, 0), new Position3D(0, 65.6, 0), new Position3D(0, 0, 1),
                new Position3D(0, 1, 0), List.of(), List.of());
    }
}
