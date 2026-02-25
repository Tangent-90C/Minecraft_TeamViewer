package person.professor_chen.teamviewer.multipleplayeresp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static final String TRACER_START_CROSSHAIR = "crosshair";
    public static final String TRACER_START_TOP = "top";
    public static final String WAYPOINT_UI_BEACON = "beacon";
    public static final String WAYPOINT_UI_RING = "ring";
    public static final String WAYPOINT_UI_PIN = "pin";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("multipleplayeresp.json");
    
    private String serverURL = "ws://localhost:8080/playeresp";
    private int renderDistance = 64;
    private boolean showLines = true;
    private boolean showBoxes = true;
    private int boxColor = 0x80FF0000; // 50%不透明红色
    private int lineColor = 0xFFFF0000; // 不透明红色
    private String tracerStartMode = TRACER_START_CROSSHAIR; // 追踪线起始点模式：crosshair 或 top
    private double tracerTopOffset = 0.42; // 顶部模式上抬偏移
    private boolean enableCompression = true; // 是否启用WebSocket压缩
    private int updateInterval = 20; // 上报频率间隔（tick），默认20tick约每秒1次
    private boolean enablePlayerESP = true; // 是否启用PlayerESP功能
    private boolean uploadEntities = true; // 是否上传实体信息（网络开销较高）
    private boolean uploadSharedWaypoints = true; // 是否上报共享路标
    private boolean showSharedWaypoints = true; // 是否显示共享路标
    private boolean enableMiddleDoubleClickMark = true; // 是否启用中键双击报点
    private int waypointTimeoutSeconds = 120; // 报点超时秒数
    private String waypointUiStyle = WAYPOINT_UI_BEACON; // 报点UI样式
    private boolean useSystemProxy = true; // 连接服务器时是否使用系统代理
    
    public static Config load() {
        if (!Files.exists(CONFIG_PATH)) {
            return new Config();
        }
        
        try {
            String content = Files.readString(CONFIG_PATH);
            return GSON.fromJson(content, Config.class);
        } catch (Exception e) {
            System.err.println("Failed to load MultiPlayer ESP config: " + e.getMessage());
            return new Config();
        }
    }
    
    public void save() {
        try {
            String content = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, content);
        } catch (IOException e) {
            System.err.println("Failed to save MultiPlayer ESP config: " + e.getMessage());
        }
    }
    
    // Getters and setters
    public String getServerURL() {
        return serverURL;
    }
    
    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
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
        // 限制最小值为1，最大值为1000tick（50秒）
        if (updateInterval < 1) {
            this.updateInterval = 1;
        } else this.updateInterval = Math.min(updateInterval, 1000);
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

    public boolean isEnableMiddleDoubleClickMark() {
        return enableMiddleDoubleClickMark;
    }

    public void setEnableMiddleDoubleClickMark(boolean enableMiddleDoubleClickMark) {
        this.enableMiddleDoubleClickMark = enableMiddleDoubleClickMark;
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

    public boolean isUseSystemProxy() {
        return useSystemProxy;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }
}