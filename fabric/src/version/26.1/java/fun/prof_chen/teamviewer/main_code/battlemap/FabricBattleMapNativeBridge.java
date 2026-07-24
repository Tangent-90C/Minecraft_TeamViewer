package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.bridge.MinecraftDimensionAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minecraft 26.1 adapter for SimMC's native region snapshot. */
public final class FabricBattleMapNativeBridge implements BattleMapNativeBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricBattleMapNativeBridge.class);
    private static final String MOD_ID = "smcmod";
    private static final String CORE_SYMBOL = "╫";

    private boolean initialized;
    private Throwable initializationError;
    private Field regionManagerField;
    private Field chunkToRegionField;
    private Method regionColorMethod;
    private Method regionIsCoreMethod;

    @Override
    public boolean isAvailable() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        initializeReflection();
        return initializationError == null;
    }

    @Override
    public String unavailableReason() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return "smcmod_not_loaded";
        }
        initializeReflection();
        return initializationError == null ? null : initializationError.getClass().getSimpleName();
    }

    @Override
    public Optional<NativeBattleMapSnapshot> capture() {
        Minecraft client = Minecraft.getInstance();
        if (!isAvailable() || client.player == null || client.level == null) {
            return Optional.empty();
        }
        try {
            Object manager = regionManagerField.get(null);
            if (manager == null || !(chunkToRegionField.get(manager) instanceof Map<?, ?> regions)) {
                return Optional.empty();
            }
            List<NativeBattleMapSnapshot.Cell> cells = new ArrayList<>();
            for (Map.Entry<?, ?> entry : regions.entrySet()) {
                if (!(entry.getKey() instanceof ChunkPos pos) || entry.getValue() == null) {
                    continue;
                }
                Object rawColor = regionColorMethod.invoke(entry.getValue());
                int color = rawColor instanceof Number number ? number.intValue() : 0xFFFFFF;
                Object rawCore = regionIsCoreMethod.invoke(entry.getValue());
                cells.add(new NativeBattleMapSnapshot.Cell(
                        pos.x(), pos.z(), String.format("#%06X", color & 0xFFFFFF),
                        rawCore instanceof Boolean core && core ? CORE_SYMBOL : null));
            }
            return cells.isEmpty() ? Optional.empty() : Optional.of(new NativeBattleMapSnapshot(
                    MinecraftDimensionAdapter.toDimensionId(client.level.dimension()), System.currentTimeMillis(), cells));
        } catch (ReflectiveOperationException exception) {
            LOGGER.debug("Failed to capture SimMC battle map: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private synchronized void initializeReflection() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> managerClass = Class.forName("com.simmc.mod.region.RegionManager");
            regionManagerField = managerClass.getField("regionManager");
            Class<?> managerImplClass = Class.forName("com.simmc.mod.region.RegionManagerImpl");
            chunkToRegionField = managerImplClass.getDeclaredField("chunkToRegion");
            chunkToRegionField.setAccessible(true);
            Class<?> regionClass = Class.forName("com.simmc.mod.region.Region");
            regionColorMethod = regionClass.getMethod("color");
            regionIsCoreMethod = regionClass.getMethod("isCore");
        } catch (Throwable throwable) {
            initializationError = throwable;
            LOGGER.warn("Failed to initialize SimMC bridge: {}", throwable.getMessage());
        }
    }
}
