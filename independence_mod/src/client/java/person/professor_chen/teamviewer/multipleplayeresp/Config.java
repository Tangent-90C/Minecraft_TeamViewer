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
    
    private String serverIP = "localhost";
    private int serverPort = 8080;
    private int renderDistance = 64;
    private boolean showLines = true;
    private boolean showBoxes = true;
    private int boxColor = 0x80FF0000; // 50%不透明红色
    private int lineColor = 0xFFFF0000; // 不透明红色
    
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
    public String getServerIP() {
        return serverIP;
    }
    
    public void setServerIP(String serverIP) {
        this.serverIP = serverIP;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
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
}