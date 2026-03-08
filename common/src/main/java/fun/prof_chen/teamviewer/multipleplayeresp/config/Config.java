package fun.prof_chen.teamviewer.multipleplayeresp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fun.prof_chen.teamviewer.multipleplayeresp.bridge.abstraction.SharedWaypointBridgeConfig;
import fun.prof_chen.teamviewer.multipleplayeresp.network.abstraction.PlayerEspConfigGateway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config implements SharedWaypointBridgeConfig, PlayerEspConfigGateway {
    public static final String TRACER_START_CROSSHAIR = "crosshair";
    public static final String TRACER_START_TOP = "top";
    public static final String WAYPOINT_UI_BEACON = "beacon";
    public static final String WAYPOINT_UI_RING = "ring";
    public static final String WAYPOINT_UI_PIN = "pin";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_FRIENDLY_TEAM_COLOR = 0xFF3B82F6;
    private static final int DEFAULT_NEUTRAL_TEAM_COLOR = 0xFFEAB308;
    private static final int DEFAULT_ENEMY_TEAM_COLOR = 0xFFEF4444;

    private transient Path storagePath;

    private String serverURL = "ws://localhost:8080/playeresp";
    private String roomCode = "default";
    private int renderDistance = 128000;
    private boolean showLines = false;
    private boolean showBoxes = false;
    private int boxColor = 0x80FF0000;
    private int lineColor = 0xFFFF0000;
    private int friendlyTeamColor = DEFAULT_FRIENDLY_TEAM_COLOR;
    private int neutralTeamColor = DEFAULT_NEUTRAL_TEAM_COLOR;
    private int enemyTeamColor = DEFAULT_ENEMY_TEAM_COLOR;
    private String tracerStartMode = TRACER_START_CROSSHAIR;
    private double tracerTopOffset = 0.42;
    private boolean enableCompression = true;
    private int updateInterval = 5;
    private boolean enablePlayerESP = true;
    private boolean uploadEntities = true;
    private boolean uploadSharedWaypoints = true;
    private boolean showSharedWaypoints = true;
    private boolean showOwnSharedWaypointsOnMinimap = true;
    private boolean xrayMarkersAndBoxes = true;
    private boolean enableMiddleDoubleClickMark = true;
    private boolean enableMiddleClickCancelWaypoint = true;
    private boolean autoCancelWaypointOnEntityDeath = true;
    private int waypointTimeoutSeconds = 60;
    private boolean enableLongTermWaypoint = true;
    private int longTermWaypointTimeoutSeconds = 1800;
    private int maxQuickMarkCount = 3;
    private String waypointUiStyle = WAYPOINT_UI_BEACON;
    private double waypointBeaconBeamWidth = 0.32D;
    private double waypointBeaconBeamHeight = 7.6D;
    private double tampermonkeyBeamWidth = 0.34D;
    private double tampermonkeyBeamHeight = 384.0D;
    private boolean useSystemProxy = false;
    private boolean preferLocalDataForEsp = true;

    public static Config load(Path configPath) {
        if (!Files.exists(configPath)) {
            return createWithStoragePath(configPath);
        }

        try {
            String content = Files.readString(configPath);
            Config config = GSON.fromJson(content, Config.class);
            if (config == null) {
                return createWithStoragePath(configPath);
            }
            config.storagePath = configPath;
            return config;
        } catch (Exception e) {
            System.err.println("Failed to load MultiPlayer ESP config: " + e.getMessage());
            return createWithStoragePath(configPath);
        }
    }

    private static Config createWithStoragePath(Path configPath) {
        Config config = new Config();
        config.storagePath = configPath;
        return config;
    }

    public void save() {
        if (storagePath == null) {
            System.err.println("Failed to save MultiPlayer ESP config: storage path is not configured");
            return;
        }

        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = GSON.toJson(this);
            Files.writeString(storagePath, content);
        } catch (IOException e) {
            System.err.println("Failed to save MultiPlayer ESP config: " + e.getMessage());
        }
    }

    public void setStoragePath(Path storagePath) {
        this.storagePath = storagePath;
    }

    @Override
    public String getServerURL() {
        return serverURL;
    }

    @Override
    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    @Override
    public String getRoomCode() {
        if (roomCode == null) {
            return "default";
        }
        String normalized = roomCode.trim();
        if (normalized.isEmpty()) {
            return "default";
        }
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }

    @Override
    public void setRoomCode(String roomCode) {
        if (roomCode == null) {
            this.roomCode = "default";
            return;
        }
        String normalized = roomCode.trim();
        if (normalized.isEmpty()) {
            this.roomCode = "default";
            return;
        }
        this.roomCode = normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = renderDistance;
    }

    public boolean isShowLines() {
        return showLines;
    }

    public void setShowLines(boolean showLines) {
        this.showLines = showLines;
    }

    public boolean isShowBoxes() {
        return showBoxes;
    }

    public void setShowBoxes(boolean showBoxes) {
        this.showBoxes = showBoxes;
    }

    public int getBoxColor() {
        return boxColor;
    }

    public void setBoxColor(int boxColor) {
        this.boxColor = boxColor;
    }

    public int getLineColor() {
        return lineColor;
    }

    public void setLineColor(int lineColor) {
        this.lineColor = lineColor;
    }

    public int getFriendlyTeamColor() {
        if (friendlyTeamColor == 0) {
            return DEFAULT_FRIENDLY_TEAM_COLOR;
        }
        return friendlyTeamColor;
    }

    public void setFriendlyTeamColor(int friendlyTeamColor) {
        this.friendlyTeamColor = friendlyTeamColor;
    }

    public int getNeutralTeamColor() {
        if (neutralTeamColor == 0) {
            return DEFAULT_NEUTRAL_TEAM_COLOR;
        }
        return neutralTeamColor;
    }

    public void setNeutralTeamColor(int neutralTeamColor) {
        this.neutralTeamColor = neutralTeamColor;
    }

    public int getEnemyTeamColor() {
        if (enemyTeamColor == 0) {
            return DEFAULT_ENEMY_TEAM_COLOR;
        }
        return enemyTeamColor;
    }

    public void setEnemyTeamColor(int enemyTeamColor) {
        this.enemyTeamColor = enemyTeamColor;
    }

    public String getTracerStartMode() {
        if (TRACER_START_TOP.equalsIgnoreCase(tracerStartMode)) {
            return TRACER_START_TOP;
        }
        return TRACER_START_CROSSHAIR;
    }

    public void setTracerStartMode(String tracerStartMode) {
        if (TRACER_START_TOP.equalsIgnoreCase(tracerStartMode)) {
            this.tracerStartMode = TRACER_START_TOP;
        } else {
            this.tracerStartMode = TRACER_START_CROSSHAIR;
        }
    }

    public boolean isTracerStartTop() {
        return TRACER_START_TOP.equals(getTracerStartMode());
    }

    public double getTracerTopOffset() {
        if (Double.isNaN(tracerTopOffset) || Double.isInfinite(tracerTopOffset)) {
            return 0.42;
        }
        if (tracerTopOffset < 0.0) {
            return 0.0;
        }
        return Math.min(tracerTopOffset, 1.5);
    }

    public void setTracerTopOffset(double tracerTopOffset) {
        if (Double.isNaN(tracerTopOffset) || Double.isInfinite(tracerTopOffset)) {
            this.tracerTopOffset = 0.42;
            return;
        }
        if (tracerTopOffset < 0.0) {
            this.tracerTopOffset = 0.0;
            return;
        }
        this.tracerTopOffset = Math.min(tracerTopOffset, 1.5);
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public void setEnableCompression(boolean enableCompression) {
        this.enableCompression = enableCompression;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(int updateInterval) {
        if (updateInterval < 1) {
            this.updateInterval = 1;
        } else {
            this.updateInterval = Math.min(updateInterval, 1000);
        }
    }

    public boolean isEnablePlayerESP() {
        return enablePlayerESP;
    }

    public void setEnablePlayerESP(boolean enablePlayerESP) {
        this.enablePlayerESP = enablePlayerESP;
    }

    public boolean isUploadEntities() {
        return uploadEntities;
    }

    public void setUploadEntities(boolean uploadEntities) {
        this.uploadEntities = uploadEntities;
    }

    public boolean isUploadSharedWaypoints() {
        return uploadSharedWaypoints;
    }

    public void setUploadSharedWaypoints(boolean uploadSharedWaypoints) {
        this.uploadSharedWaypoints = uploadSharedWaypoints;
    }

    public boolean isShowSharedWaypoints() {
        return showSharedWaypoints;
    }

    public void setShowSharedWaypoints(boolean showSharedWaypoints) {
        this.showSharedWaypoints = showSharedWaypoints;
    }

    public boolean isShowOwnSharedWaypointsOnMinimap() {
        return showOwnSharedWaypointsOnMinimap;
    }

    public void setShowOwnSharedWaypointsOnMinimap(boolean showOwnSharedWaypointsOnMinimap) {
        this.showOwnSharedWaypointsOnMinimap = showOwnSharedWaypointsOnMinimap;
    }

    public boolean isXrayMarkersAndBoxes() {
        return xrayMarkersAndBoxes;
    }

    public void setXrayMarkersAndBoxes(boolean xrayMarkersAndBoxes) {
        this.xrayMarkersAndBoxes = xrayMarkersAndBoxes;
    }

    public boolean isEnableMiddleDoubleClickMark() {
        return enableMiddleDoubleClickMark;
    }

    public void setEnableMiddleDoubleClickMark(boolean enableMiddleDoubleClickMark) {
        this.enableMiddleDoubleClickMark = enableMiddleDoubleClickMark;
    }

    public boolean isEnableMiddleClickCancelWaypoint() {
        return enableMiddleClickCancelWaypoint;
    }

    public void setEnableMiddleClickCancelWaypoint(boolean enableMiddleClickCancelWaypoint) {
        this.enableMiddleClickCancelWaypoint = enableMiddleClickCancelWaypoint;
    }

    public boolean isAutoCancelWaypointOnEntityDeath() {
        return autoCancelWaypointOnEntityDeath;
    }

    public void setAutoCancelWaypointOnEntityDeath(boolean autoCancelWaypointOnEntityDeath) {
        this.autoCancelWaypointOnEntityDeath = autoCancelWaypointOnEntityDeath;
    }

    public int getWaypointTimeoutSeconds() {
        if (waypointTimeoutSeconds < 10) {
            return 10;
        }
        return Math.min(waypointTimeoutSeconds, 3600);
    }

    public void setWaypointTimeoutSeconds(int waypointTimeoutSeconds) {
        if (waypointTimeoutSeconds < 10) {
            this.waypointTimeoutSeconds = 10;
            return;
        }
        this.waypointTimeoutSeconds = Math.min(waypointTimeoutSeconds, 3600);
    }

    public boolean isEnableLongTermWaypoint() {
        return enableLongTermWaypoint;
    }

    public void setEnableLongTermWaypoint(boolean enableLongTermWaypoint) {
        this.enableLongTermWaypoint = enableLongTermWaypoint;
    }

    public int getLongTermWaypointTimeoutSeconds() {
        if (longTermWaypointTimeoutSeconds < 30) {
            return 30;
        }
        return Math.min(longTermWaypointTimeoutSeconds, 86400);
    }

    public void setLongTermWaypointTimeoutSeconds(int longTermWaypointTimeoutSeconds) {
        if (longTermWaypointTimeoutSeconds < 30) {
            this.longTermWaypointTimeoutSeconds = 30;
            return;
        }
        this.longTermWaypointTimeoutSeconds = Math.min(longTermWaypointTimeoutSeconds, 86400);
    }

    public int getMaxQuickMarkCount() {
        if (maxQuickMarkCount < 1) {
            return 1;
        }
        return Math.min(maxQuickMarkCount, 20);
    }

    public void setMaxQuickMarkCount(int maxQuickMarkCount) {
        if (maxQuickMarkCount < 1) {
            this.maxQuickMarkCount = 1;
            return;
        }
        this.maxQuickMarkCount = Math.min(maxQuickMarkCount, 20);
    }

    public String getWaypointUiStyle() {
        if (WAYPOINT_UI_RING.equalsIgnoreCase(waypointUiStyle)) {
            return WAYPOINT_UI_RING;
        }
        if (WAYPOINT_UI_PIN.equalsIgnoreCase(waypointUiStyle)) {
            return WAYPOINT_UI_PIN;
        }
        return WAYPOINT_UI_BEACON;
    }

    public void setWaypointUiStyle(String waypointUiStyle) {
        if (WAYPOINT_UI_RING.equalsIgnoreCase(waypointUiStyle)) {
            this.waypointUiStyle = WAYPOINT_UI_RING;
            return;
        }
        if (WAYPOINT_UI_PIN.equalsIgnoreCase(waypointUiStyle)) {
            this.waypointUiStyle = WAYPOINT_UI_PIN;
            return;
        }
        this.waypointUiStyle = WAYPOINT_UI_BEACON;
    }

    public double getWaypointBeaconBeamWidth() {
        if (Double.isNaN(waypointBeaconBeamWidth) || Double.isInfinite(waypointBeaconBeamWidth)) {
            return 0.32D;
        }
        if (waypointBeaconBeamWidth < 0.05D) {
            return 0.05D;
        }
        return Math.min(waypointBeaconBeamWidth, 4.0D);
    }

    public void setWaypointBeaconBeamWidth(double waypointBeaconBeamWidth) {
        if (Double.isNaN(waypointBeaconBeamWidth) || Double.isInfinite(waypointBeaconBeamWidth)) {
            this.waypointBeaconBeamWidth = 0.32D;
            return;
        }
        if (waypointBeaconBeamWidth < 0.05D) {
            this.waypointBeaconBeamWidth = 0.05D;
            return;
        }
        this.waypointBeaconBeamWidth = Math.min(waypointBeaconBeamWidth, 4.0D);
    }

    public double getWaypointBeaconBeamHeight() {
        if (Double.isNaN(waypointBeaconBeamHeight) || Double.isInfinite(waypointBeaconBeamHeight)) {
            return 7.6D;
        }
        if (waypointBeaconBeamHeight < 0.5D) {
            return 0.5D;
        }
        return Math.min(waypointBeaconBeamHeight, 256.0D);
    }

    public void setWaypointBeaconBeamHeight(double waypointBeaconBeamHeight) {
        if (Double.isNaN(waypointBeaconBeamHeight) || Double.isInfinite(waypointBeaconBeamHeight)) {
            this.waypointBeaconBeamHeight = 7.6D;
            return;
        }
        if (waypointBeaconBeamHeight < 0.5D) {
            this.waypointBeaconBeamHeight = 0.5D;
            return;
        }
        this.waypointBeaconBeamHeight = Math.min(waypointBeaconBeamHeight, 256.0D);
    }

    public double getTampermonkeyBeamWidth() {
        if (Double.isNaN(tampermonkeyBeamWidth) || Double.isInfinite(tampermonkeyBeamWidth)) {
            return 0.34D;
        }
        if (tampermonkeyBeamWidth < 0.05D) {
            return 0.05D;
        }
        return Math.min(tampermonkeyBeamWidth, 8.0D);
    }

    public void setTampermonkeyBeamWidth(double tampermonkeyBeamWidth) {
        if (Double.isNaN(tampermonkeyBeamWidth) || Double.isInfinite(tampermonkeyBeamWidth)) {
            this.tampermonkeyBeamWidth = 0.34D;
            return;
        }
        if (tampermonkeyBeamWidth < 0.05D) {
            this.tampermonkeyBeamWidth = 0.05D;
            return;
        }
        this.tampermonkeyBeamWidth = Math.min(tampermonkeyBeamWidth, 8.0D);
    }

    public double getTampermonkeyBeamHeight() {
        if (Double.isNaN(tampermonkeyBeamHeight) || Double.isInfinite(tampermonkeyBeamHeight)) {
            return 384.0D;
        }
        if (tampermonkeyBeamHeight < 1.0D) {
            return 1.0D;
        }
        return Math.min(tampermonkeyBeamHeight, 1024.0D);
    }

    public void setTampermonkeyBeamHeight(double tampermonkeyBeamHeight) {
        if (Double.isNaN(tampermonkeyBeamHeight) || Double.isInfinite(tampermonkeyBeamHeight)) {
            this.tampermonkeyBeamHeight = 384.0D;
            return;
        }
        if (tampermonkeyBeamHeight < 1.0D) {
            this.tampermonkeyBeamHeight = 1.0D;
            return;
        }
        this.tampermonkeyBeamHeight = Math.min(tampermonkeyBeamHeight, 1024.0D);
    }

    @Override
    public boolean isUseSystemProxy() {
        return useSystemProxy;
    }

    @Override
    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }

    @Override
    public int getUpdateIntervalTicks() {
        return getUpdateInterval();
    }

    public boolean isPreferLocalDataForEsp() {
        return preferLocalDataForEsp;
    }

    public void setPreferLocalDataForEsp(boolean preferLocalDataForEsp) {
        this.preferLocalDataForEsp = preferLocalDataForEsp;
    }
}
