package niubi.professor_chen.wurstPlugin.hook_wurst;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;
import niubi.professor_chen.wurstPlugin.config.MultiPlayerESPConfig;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SearchTags({"player esp", "PlayerTracers", "player tracers", "multi"})
public final class MultiPlayerEspHack extends Hack implements UpdateListener,
        CameraTransformViewBobbingListener, RenderListener {
    private final EspStyleSetting style =
            new EspStyleSetting(EspStyleSetting.EspStyle.LINES_AND_BOXES);

    private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
            "\u00a7lAccurate\u00a7r mode shows the exact hitbox of each player.\n"
                    + "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");

    private final EntityFilterList entityFilters = new EntityFilterList(
            new FilterSleepingSetting("Won't show sleeping players.", false),
            new FilterInvisibleSetting("Won't show invisible players.", false));

    private final CheckboxSetting showMobs = new CheckboxSetting("Show mobs",
            "Also show mobs (zombies, skeletons, etc.)", false);

    private final EntityFilterList mobFilters =
            new EntityFilterList(FilterHostileSetting.genericVision(false),
                    FilterNeutralSetting
                            .genericVision(AttackDetectingEntityFilter.Mode.OFF),
                    FilterPassiveSetting.genericVision(false),
                    FilterPassiveWaterSetting.genericVision(false),
                    FilterBatsSetting.genericVision(false),
                    FilterSlimesSetting.genericVision(false),
                    FilterPetsSetting.genericVision(false),
                    FilterVillagersSetting.genericVision(false),
                    FilterZombieVillagersSetting.genericVision(false),
                    FilterGolemsSetting.genericVision(false),
                    FilterPiglinsSetting
                            .genericVision(AttackDetectingEntityFilter.Mode.OFF),
                    FilterZombiePiglinsSetting
                            .genericVision(AttackDetectingEntityFilter.Mode.OFF),
                    FilterEndermenSetting
                            .genericVision(AttackDetectingEntityFilter.Mode.OFF),
                    FilterShulkersSetting.genericVision(false),
                    FilterAllaysSetting.genericVision(false),
                    // FilterInvisibleSetting.genericVision(false),
                    FilterNamedSetting.genericVision(false),
                    FilterArmorStandsSetting.genericVision(true));

    // 使用独立的配置管理类
    private final MultiPlayerESPConfig config = MultiPlayerESPConfig.getInstance();
    
    // 创建本地设置项以在Wurst界面中显示
    private final CheckboxSetting networkSyncSetting = new CheckboxSetting(
            "Network sync", "Shows players from other clients on the same server.\n"
            + "Requires the PlayerESP server to be running.",
            config.isNetworkSync());
    
    private final TextFieldSetting serverIPSetting = new TextFieldSetting("Server IP",
            "The IP address of the PlayerESP server.", config.getServerIP());

    private final TextFieldSetting serverPortSetting = new TextFieldSetting(
            "Server Port", "The port of the PlayerESP server.", config.getServerPort());

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final WurstClient wurst = WurstClient.INSTANCE;
    
    private final ArrayList<PlayerEntity> players = new ArrayList<>();
    private final ArrayList<LivingEntity> mobs = new ArrayList<>();
    private PlayerESPNetworkManager networkManager;

    private long lastDebugTime = 0;
    private static final long DEBUG_INTERVAL = 1000; // 1 second

    public MultiPlayerEspHack() {
        super("MultiPlayerESP");
        setCategory(Category.RENDER);
        addSetting(style);
        addSetting(boxSize);
        addSetting(showMobs);
        addSetting(networkSyncSetting);
        addSetting(serverIPSetting);
        addSetting(serverPortSetting);
        entityFilters.forEach(this::addSetting);
        mobFilters.forEach(this::addSetting);
    }

    // 提供公共方法访问配置
    public MultiPlayerESPConfig getConfig() {
        return config;
    }

    @Override
    public void onEnable() {
        networkSyncSetting.setChecked(config.isNetworkSync());
        serverIPSetting.setValue(config.getServerIP());
        serverPortSetting.setValue(config.getServerPort());
        
        // 启用Hack时连接到服务器
        if (config.isNetworkSync() && networkManager == null) {
            networkManager = new PlayerESPNetworkManager(config.getServerIP(),
                    config.getServerPort());
            networkManager.connect();
        }

        wurst.getEventManager().add(UpdateListener.class, this);
        wurst.getEventManager().add(CameraTransformViewBobbingListener.class, this);
        wurst.getEventManager().add(RenderListener.class, this);
        
        lastDebugTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        // 移除所有事件监听
        wurst.getEventManager().remove(UpdateListener.class, this);
        wurst.getEventManager().remove(CameraTransformViewBobbingListener.class, this);
        wurst.getEventManager().remove(RenderListener.class, this);
        
        // 清理网络管理器
        if (networkManager != null) {
            networkManager.shutdown();
            networkManager = null;
        }
        
        // 清理玩家和生物列表
        players.clear();
        mobs.clear();
    }

    @Override
    public void onUpdate() {
        // 更新配置
        if (networkSyncSetting.isChecked() != config.isNetworkSync() ||
            !serverIPSetting.getValue().equals(config.getServerIP()) ||
            !serverPortSetting.getValue().equals(config.getServerPort())) {
            
            // 更新配置文件
            config.setNetworkSync(networkSyncSetting.isChecked());
            config.setServerIP(serverIPSetting.getValue());
            config.setServerPort(serverPortSetting.getValue());
            
            // 如果启用了网络同步且设置发生了变化，重新连接
            if (networkSyncSetting.isChecked() && networkManager != null) {
                networkManager.shutdown();
                networkManager = new PlayerESPNetworkManager(serverIPSetting.getValue(),
                        serverPortSetting.getValue());
                networkManager.connect();
            } else if (!networkSyncSetting.isChecked() && networkManager != null) {
                networkManager.shutdown();
                networkManager = null;
            }
        }
        
        // 每秒发送一次调试信息
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDebugTime > DEBUG_INTERVAL) {
            if (config.isNetworkSync() && networkManager != null
                    && networkManager.isConnected()) {
                ChatUtils.message(
                        "[MultiPlayerESP] Connected to server. Tracking "
                                + networkManager.getRemotePlayers().size() + " remote players and "
                                + networkManager.getRemoteEntities().size() + " entities.");
            }
            lastDebugTime = currentTime;
        }

        Stream<AbstractClientPlayerEntity> stream = StreamSupport
                .stream(mc.world.getPlayers().spliterator(), false)
                .filter(e -> !e.isRemoved() && e.getPos().isInRange(mc.player.getPos(), 128))
                .filter(e -> e != mc.player)
                .filter(e -> !(e instanceof FakePlayerEntity))
                .filter(e -> e.getVehicle() == null);

        players.clear();
        entityFilters.applyTo(stream).collect(Collectors.toCollection(() -> players));

        if (!showMobs.isChecked()) {
            mobs.clear();
            return;
        }

        Stream<LivingEntity> stream2 = StreamSupport
                .stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> !e.isRemoved()).filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .filter(e -> e.getPos().isInRange(mc.player.getPos(), 128))
                .filter(e -> e != mc.player)
                .filter(e -> !(e instanceof FakePlayerEntity));

        mobs.clear();
        mobFilters.applyTo(stream2).collect(Collectors.toCollection(() -> mobs));
    }

    @Override
    public void onCameraTransformViewBobbing(
            CameraTransformViewBobbingEvent event) {
        if (style.hasLines())
            event.cancel();
    }

    @Override
    public void onRender(MatrixStack matrixStack, float partialTicks) {
        if (style.hasBoxes()) {
            double extraSize = boxSize.getExtraSize() / 2;

            ArrayList<RenderUtils.ColoredBox> boxes = new ArrayList<>();
            
            // 只添加来自网络的玩家框
            if (config.isNetworkSync() && networkManager != null
                    && networkManager.isConnected()) {
                for (PlayerESPNetworkManager.RemotePlayer remotePlayer : networkManager
                        .getRemotePlayers().values()) {
                    // 检查是否在同一维度
                    if (mc.world.getRegistryKey().getValue().toString()
                            .equals(remotePlayer.dimension)) {
                        // 使用接收到的碰撞箱尺寸创建Box
                        double halfWidth = remotePlayer.width / 2.0;
                        Box box = new Box(remotePlayer.x - halfWidth, remotePlayer.y,
                                remotePlayer.z - halfWidth, remotePlayer.x + halfWidth,
                                remotePlayer.y + remotePlayer.height, remotePlayer.z + halfWidth)
                                .offset(0, extraSize, 0).expand(extraSize);

                        boxes.add(new RenderUtils.ColoredBox(box, 0x80FFFFFF)); // 白色，半透明
                    }
                }

                // 添加来自网络的实体框 (仅当showMobs为true时)
                if (showMobs.isChecked()) {
                    for (PlayerESPNetworkManager.RemoteEntity remoteEntity : networkManager
                            .getRemoteEntities().values()) {
                        // 检查是否在同一维度
                        if (mc.world.getRegistryKey().getValue().toString()
                                .equals(remoteEntity.dimension)) {
                            // 使用接收到的碰撞箱尺寸创建Box
                            double halfWidth = remoteEntity.width / 2.0;
                            Box box = new Box(remoteEntity.x - halfWidth, remoteEntity.y,
                                    remoteEntity.z - halfWidth, remoteEntity.x + halfWidth,
                                    remoteEntity.y + remoteEntity.height, remoteEntity.z + halfWidth)
                                    .offset(0, extraSize, 0).expand(extraSize);

                            boxes.add(new RenderUtils.ColoredBox(box, 0x80FFFF00)); // 黄色，半透明
                        }
                    }
                }
            }

            RenderUtils.drawOutlinedBoxes(matrixStack, boxes, false);
        }

        if (style.hasLines()) {
            ArrayList<RenderUtils.ColoredPoint> ends = new ArrayList<>();
            
            // 只添加来自网络的玩家追踪线
            if (config.isNetworkSync() && networkManager != null
                    && networkManager.isConnected()) {
                for (PlayerESPNetworkManager.RemotePlayer remotePlayer : networkManager
                        .getRemotePlayers().values()) {
                    // 检查是否在同一维度
                    if (mc.world.getRegistryKey().getValue().toString()
                            .equals(remotePlayer.dimension)) {
                        Vec3d point = new Vec3d(remotePlayer.x,
                                remotePlayer.y + remotePlayer.height * 0.5, remotePlayer.z);
                        ends.add(new RenderUtils.ColoredPoint(point, 0x80FFFFFF)); // 白色，半透明
                    }
                }

                // 添加来自网络的实体追踪线 (仅当showMobs为true时)
                if (showMobs.isChecked()) {
                    for (PlayerESPNetworkManager.RemoteEntity remoteEntity : networkManager
                            .getRemoteEntities().values()) {
                        // 检查是否在同一维度
                        if (mc.world.getRegistryKey().getValue().toString()
                                .equals(remoteEntity.dimension)) {
                            Vec3d point = new Vec3d(remoteEntity.x,
                                    remoteEntity.y + remoteEntity.height * 0.5, remoteEntity.z);
                            ends.add(new RenderUtils.ColoredPoint(point, 0x80FFFF00)); // 黄色，半透明
                        }
                    }
                }
            }

            RenderUtils.drawTracers(matrixStack, partialTicks, ends, false);
        }
    }


    private int getColor(PlayerEntity e) {
        if (e.isSneaking())
            return 0x80FF0000; // 红色，半透明

        return 0x8000FF00; // 绿色，半透明
    }

    private int getMobColor(LivingEntity e) {
        if (e.isSneaking())
            return 0x80FF0000; // 红色，半透明

        return 0x8000FF00; // 绿色，半透明
    }
}