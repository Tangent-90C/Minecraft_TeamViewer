package niubi.professor_chen.wurstPlugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MultiPlayerESPConfig {
    private static MultiPlayerESPConfig instance;
    
    private boolean networkSync = false;
    private boolean enableWurstMixin = true;
    private String serverIP = "localhost";
    private String serverPort = "8765";
    
    private static final String CONFIG_FILE = "config/multiplayer_esp_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private MultiPlayerESPConfig() {
        // 私有构造函数，确保单例模式
        // 先加载配置，再保存（如果需要）
        loadConfig();
    }
    
    public static MultiPlayerESPConfig getInstance() {
        if (instance == null) {
            instance = new MultiPlayerESPConfig();
        }
        return instance;
    }
    
    // Getter方法
    public boolean isNetworkSync() {
        return networkSync;
    }
    
    public boolean isWurstMixinEnabled() {
        return enableWurstMixin;
    }
    
    public String getServerIP() {
        return serverIP;
    }
    
    public String getServerPort() {
        return serverPort;
    }
    
    // Setter方法
    public void setNetworkSync(boolean enabled) {
        this.networkSync = enabled;
        saveConfig();
    }
    
    public void setEnableWurstMixin(boolean enabled) {
        this.enableWurstMixin = enabled;
        saveConfig();
    }
    
    public void setServerIP(String ip) {
        this.serverIP = ip;
        saveConfig();
    }
    
    public void setServerPort(String port) {
        this.serverPort = port;
        saveConfig();
    }
    
    private void saveConfig() {
        try {
            // 确保配置目录存在
            Path configPath = Path.of(CONFIG_FILE);
            Files.createDirectories(configPath.getParent());
            
            JsonObject json = new JsonObject();
            json.addProperty("networkSync", networkSync);
            json.addProperty("enableWurstMixin", enableWurstMixin);
            json.addProperty("serverIP", serverIP);
            json.addProperty("serverPort", serverPort);
            
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                writer.write(GSON.toJson(json));
            }
        } catch (IOException e) {
            System.err.println("Failed to save MultiPlayerESP config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadConfig() {
        try {
            if (!Files.exists(Path.of(CONFIG_FILE))) {
                // 如果配置文件不存在，保存默认配置
                saveConfig();
                return;
            }
            
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                
                if (json.has("networkSync")) {
                    networkSync = json.get("networkSync").getAsBoolean();
                }
                
                if (json.has("enableWurstMixin")) {
                    enableWurstMixin = json.get("enableWurstMixin").getAsBoolean();
                }
                
                if (json.has("serverIP")) {
                    serverIP = json.get("serverIP").getAsString();
                }
                
                if (json.has("serverPort")) {
                    serverPort = json.get("serverPort").getAsString();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load MultiPlayerESP config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}