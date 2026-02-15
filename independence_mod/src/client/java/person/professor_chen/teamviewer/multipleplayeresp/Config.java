package person.professor_chen.teamviewer.multipleplayeresp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("multipleplayeresp.json");
    
    private String serverURL = "ws://localhost:8080/playeresp";
    private int renderDistance = 64;
    private boolean showLines = true;
    private boolean showBoxes = true;
    private int boxColor = 0x80FF0000; // 50%不透明红色
    private int lineColor = 0xFFFF0000; // 不透明红色
    private boolean enableCompression = true; // 是否启用WebSocket压缩
    private int updateInterval = 20; // 上报频率间隔（tick），默认20tick约每秒1次
    private boolean enablePlayerESP = true; // 是否启用PlayerESP功能
    
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
}