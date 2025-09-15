package niubi.professor_chen.wurstPlugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MultiPlayerESPConfig {
    private static MultiPlayerESPConfig instance;
    
    private final CheckboxSetting networkSync = new CheckboxSetting(
            "Network sync", "Shows players from other clients on the same server.\n"
            + "Requires the PlayerESP server to be running.",
            false);
    
    private final CheckboxSetting enableWurstMixin = new CheckboxSetting(
            "Enable Wurst Mixin", "Whether to inject the MultiPlayerESP hack into Wurst client.\n"
            + "Requires restart to take effect.",
            true);

    private final TextFieldSetting serverIP = new TextFieldSetting("Server IP",
            "The IP address of the PlayerESP server.", "localhost");

    private final TextFieldSetting serverPort = new TextFieldSetting(
            "Server Port", "The port of the PlayerESP server.", "8765");
    
    private static final String CONFIG_FILE = "config/multiplayer_esp_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private MultiPlayerESPConfig() {
        // 私有构造函数，确保单例模式
        loadConfig();
    }
    
    public static MultiPlayerESPConfig getInstance() {
        if (instance == null) {
            instance = new MultiPlayerESPConfig();
        }
        return instance;
    }
    
    // Getter方法
    public CheckboxSetting getNetworkSync() {
        return networkSync;
    }
    
    public CheckboxSetting getEnableWurstMixin() {
        return enableWurstMixin;
    }
    
    public TextFieldSetting getServerIP() {
        return serverIP;
    }
    
    public TextFieldSetting getServerPort() {
        return serverPort;
    }
    
    // 便捷方法，用于直接获取值
    public boolean isNetworkSyncEnabled() {
        return networkSync.isChecked();
    }
    
    public boolean isWurstMixinEnabled() {
        return enableWurstMixin.isChecked();
    }
    
    public String getServerIPValue() {
        return serverIP.getValue();
    }
    
    public String getServerPortValue() {
        return serverPort.getValue();
    }
    
    // 便捷方法，用于直接设置值
    public void setNetworkSync(boolean enabled) {
        networkSync.setChecked(enabled);
        saveConfig();
    }
    
    public void setEnableWurstMixin(boolean enabled) {
        enableWurstMixin.setChecked(enabled);
        saveConfig();
    }
    
    public void setServerIP(String ip) {
        serverIP.setValue(ip);
        saveConfig();
    }
    
    public void setServerPort(String port) {
        serverPort.setValue(port);
        saveConfig();
    }
    
    private void saveConfig() {
        try {
            // 确保配置目录存在
            Path configPath = Path.of(CONFIG_FILE);
            Files.createDirectories(configPath.getParent());
            
            JsonObject json = new JsonObject();
            json.addProperty("networkSync", networkSync.isChecked());
            json.addProperty("enableWurstMixin", enableWurstMixin.isChecked());
            json.addProperty("serverIP", serverIP.getValue());
            json.addProperty("serverPort", serverPort.getValue());
            
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
                    networkSync.setChecked(json.get("networkSync").getAsBoolean());
                }
                
                if (json.has("enableWurstMixin")) {
                    enableWurstMixin.setChecked(json.get("enableWurstMixin").getAsBoolean());
                }
                
                if (json.has("serverIP")) {
                    serverIP.setValue(json.get("serverIP").getAsString());
                }
                
                if (json.has("serverPort")) {
                    serverPort.setValue(json.get("serverPort").getAsString());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load MultiPlayerESP config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}