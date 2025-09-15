package niubi.professor_chen.wurstPlugin.hook_wurst;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackList;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;
import niubi.professor_chen.wurstPlugin.network.PlayerESPNetworkManager;
import niubi.professor_chen.wurstPlugin.config.MultiPlayerESPConfig;
import niubi.professor_chen.wurstPlugin.mixin.WurstClientMixin;

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
        addSetting(config.getNetworkSync());
        addSetting(config.getServerIP());
        addSetting(config.getServerPort());
        entityFilters.forEach(this::addSetting);
        mobFilters.forEach(this::addSetting);
    }

    // 提供公共方法访问配置
    public MultiPlayerESPConfig getConfig() {
        return config;
    }

    @Override
    protected void onEnable() {
        EVENTS.add(UpdateListener.class, this);
        EVENTS.add(CameraTransformViewBobbingListener.class, this);
        EVENTS.add(RenderListener.class, this);

        if (config.isNetworkSyncEnabled()) {
            networkManager = new PlayerESPNetworkManager(config.getServerIPValue(),
                    config.getServerPortValue());
            networkManager.connect();
        }
    }

    @Override
    protected void onDisable() {
        // 移除所有事件监听
        EVENTS.remove(UpdateListener.class, this);
        EVENTS.remove(CameraTransformViewBobbingListener.class, this);
        EVENTS.remove(RenderListener.class, this);
        
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
        players.clear();

        Stream<AbstractClientPlayerEntity> stream = MC.world.getPlayers()
                .parallelStream().filter(e -> !e.isRemoved() && e.getHealth() > 0)
                .filter(e -> e != MC.player)
                .filter(e -> !(e instanceof FakePlayerEntity))
                .filter(e -> Math.abs(e.getY() - MC.player.getY()) <= 1e6);

        stream = entityFilters.applyTo(stream);

        players.addAll(stream.collect(Collectors.toList()));

        if (showMobs.isChecked()) {
            mobs.clear();

            Stream<LivingEntity> mobStream = StreamSupport
                    .stream(MC.world.getEntities().spliterator(), false)
                    .filter(LivingEntity.class::isInstance)
                    .map(e -> (LivingEntity) e)
                    .filter(e -> !(e instanceof PlayerEntity))
                    .filter(e -> !e.isRemoved() && e.getHealth() > 0);

            mobStream = mobFilters.applyTo(mobStream);

            mobs.addAll(mobStream.collect(Collectors.toList()));
        }

        if (config.isNetworkSyncEnabled() && networkManager != null
                && networkManager.isConnected()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDebugTime > DEBUG_INTERVAL) {
                lastDebugTime = currentTime;
                // 网络状态调试信息已移除
            }
        }
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

            ArrayList<RenderUtils.ColoredBox> boxes = new ArrayList<>(players.size());
            for (PlayerEntity e : players) {
                Box box = EntityUtils.getLerpedBox(e, partialTicks)
                        .offset(0, extraSize, 0).expand(extraSize);
                boxes.add(new RenderUtils.ColoredBox(box, getColor(e)));
            }

            if (showMobs.isChecked()) {
                for (LivingEntity e : mobs) {
                    Box box = EntityUtils.getLerpedBox(e, partialTicks)
                            .offset(0, extraSize, 0).expand(extraSize);
                    boxes.add(new RenderUtils.ColoredBox(box, getMobColor(e)));
                }
            }

            // 添加来自网络的玩家框
            if (config.isNetworkSyncEnabled() && networkManager != null
                    && networkManager.isConnected()) {
                for (PlayerESPNetworkManager.RemotePlayer remotePlayer : networkManager
                        .getRemotePlayers().values()) {
                    // 检查是否在同一维度
                    if (MC.world.getRegistryKey().getValue().toString()
                            .equals(remotePlayer.dimension)) {
                        Box box = new Box(remotePlayer.x - 0.3, remotePlayer.y,
                                remotePlayer.z - 0.3, remotePlayer.x + 0.3,
                                remotePlayer.y + 1.8, remotePlayer.z + 0.3)
                                .offset(0, extraSize, 0).expand(extraSize);

                        boxes.add(new RenderUtils.ColoredBox(box, 0x80FFFFFF)); // 白色，半透明
                    }
                }

                // 添加来自网络的实体框
                for (PlayerESPNetworkManager.RemoteEntity remoteEntity : networkManager
                        .getRemoteEntities().values()) {
                    // 检查是否在同一维度
                    if (MC.world.getRegistryKey().getValue().toString()
                            .equals(remoteEntity.dimension)) {
                        Box box = new Box(remoteEntity.x - 0.3, remoteEntity.y,
                                remoteEntity.z - 0.3, remoteEntity.x + 0.3,
                                remoteEntity.y + 1.8, remoteEntity.z + 0.3)
                                .offset(0, extraSize, 0).expand(extraSize);

                        boxes.add(new RenderUtils.ColoredBox(box, 0x80FFFF00)); // 黄色，半透明
                    }
                }
            }

            RenderUtils.drawOutlinedBoxes(matrixStack, boxes, false);
        }

        if (style.hasLines()) {
            ArrayList<RenderUtils.ColoredPoint> ends =
                    new ArrayList<>(players.size());
            for (PlayerEntity e : players) {
                Vec3d point =
                        EntityUtils.getLerpedBox(e, partialTicks).getCenter();
                ends.add(new RenderUtils.ColoredPoint(point, getColor(e)));
            }

            if (showMobs.isChecked()) {
                for (LivingEntity e : mobs) {
                    Vec3d point =
                            EntityUtils.getLerpedBox(e, partialTicks).getCenter();
                    ends.add(
                            new RenderUtils.ColoredPoint(point, getMobColor(e)));
                }
            }

            // 添加来自网络的玩家追踪线
            if (config.isNetworkSyncEnabled() && networkManager != null
                    && networkManager.isConnected()) {
                for (PlayerESPNetworkManager.RemotePlayer remotePlayer : networkManager
                        .getRemotePlayers().values()) {
                    // 检查是否在同一维度
                    if (MC.world.getRegistryKey().getValue().toString()
                            .equals(remotePlayer.dimension)) {
                        Vec3d point = new Vec3d(remotePlayer.x,
                                remotePlayer.y + 0.9, remotePlayer.z);
                        ends.add(new RenderUtils.ColoredPoint(point, 0x80FFFFFF)); // 白色，半透明
                    }
                }

                // 添加来自网络的实体追踪线
                for (PlayerESPNetworkManager.RemoteEntity remoteEntity : networkManager
                        .getRemoteEntities().values()) {
                    // 检查是否在同一维度
                    if (MC.world.getRegistryKey().getValue().toString()
                            .equals(remoteEntity.dimension)) {
                        Vec3d point = new Vec3d(remoteEntity.x,
                                remoteEntity.y + 0.9, remoteEntity.z);
                        ends.add(new RenderUtils.ColoredPoint(point, 0x80FFFF00)); // 黄色，半透明
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