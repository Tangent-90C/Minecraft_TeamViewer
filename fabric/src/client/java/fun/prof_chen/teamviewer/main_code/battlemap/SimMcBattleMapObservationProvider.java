package fun.prof_chen.teamviewer.main_code.battlemap;

import fun.prof_chen.teamviewer.main_code.config.Config;
import fun.prof_chen.teamviewer.main_code.model.ReportDataSchemas;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SimMcBattleMapObservationProvider implements BattleMapObservationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimMcBattleMapObservationProvider.class);
    private static final String SMC_MOD_ID = "smcmod";
    private static final String CORE_SYMBOL = "╫";

    private boolean reflectionInitialized = false;
    private Throwable reflectionError;
    private Field regionManagerField;
    private Field chunkToRegionField;
    private Method regionColorMethod;
    private Method regionIsCoreMethod;

    @Override
    public BattleMapMode mode() {
        return BattleMapMode.SIMMC;
    }

    @Override
    public void tick(MinecraftClient client) {
    }

    @Override
    public Optional<BattleMapObservationResult> collect(MinecraftClient client, Config config) {
        if (client == null || client.player == null || client.world == null || !isAvailable()) {
            return Optional.empty();
        }
        try {
            Map<?, ?> chunkToRegion = getChunkToRegionSnapshot();
            if (chunkToRegion == null || chunkToRegion.isEmpty()) {
                return Optional.empty();
            }

            List<ObservedChunkCell> observedCells = new ArrayList<>();
            for (Map.Entry<?, ?> entry : chunkToRegion.entrySet()) {
                if (!(entry.getKey() instanceof ChunkPos chunkPos) || entry.getValue() == null) {
                    continue;
                }
                observedCells.add(new ObservedChunkCell(
                        chunkPos.x,
                        chunkPos.z,
                        toHexColor(entry.getValue()),
                        isCore(entry.getValue()) ? CORE_SYMBOL : null
                ));
            }
            if (observedCells.isEmpty()) {
                return Optional.empty();
            }
            observedCells.sort(Comparator.comparingInt(ObservedChunkCell::chunkZ).thenComparingInt(ObservedChunkCell::chunkX));

            int minChunkX = observedCells.stream().mapToInt(ObservedChunkCell::chunkX).min().orElse(0);
            int minChunkZ = observedCells.stream().mapToInt(ObservedChunkCell::chunkZ).min().orElse(0);
            int maxChunkX = observedCells.stream().mapToInt(ObservedChunkCell::chunkX).max().orElse(minChunkX);
            int maxChunkZ = observedCells.stream().mapToInt(ObservedChunkCell::chunkZ).max().orElse(minChunkZ);
            int mapSize = Math.max(maxChunkX - minChunkX + 1, maxChunkZ - minChunkZ + 1);
            long now = System.currentTimeMillis();
            String dimension = client.player.getWorld().getRegistryKey().getValue().toString();

            List<Map<String, Object>> candidatePayloads = List.of(
                    new ReportDataSchemas.BattleMapObservationCandidatePayload(
                            minChunkX,
                            minChunkZ,
                            now,
                            "history_primary"
                    ).toMap()
            );

            List<Map<String, Object>> cellPayloads = new ArrayList<>();
            for (ObservedChunkCell cell : observedCells) {
                cellPayloads.add(new ReportDataSchemas.BattleMapObservationCellPayload(
                        cell.chunkX() - minChunkX,
                        cell.chunkZ() - minChunkZ,
                        cell.symbol(),
                        cell.colorRaw()
                ).toMap());
            }

            BattleMapProjectionUtil.Projection projection = BattleMapProjectionUtil.buildProjection(
                    dimension,
                    minChunkX,
                    minChunkZ,
                    cellPayloads
            );
            return Optional.of(new BattleMapObservationResult(
                    mode().id(),
                    dimension,
                    mapSize,
                    0,
                    0,
                    now,
                    now,
                    candidatePayloads,
                    cellPayloads,
                    projection.semanticHash(),
                    projection.chunkIds()
            ));
        } catch (Exception e) {
            if (config != null && config.isBattleMapDebugEnabled()) {
                LOGGER.info("Failed to collect SimMC battle map snapshot: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean isAvailable() {
        if (!FabricLoader.getInstance().isModLoaded(SMC_MOD_ID)) {
            return false;
        }
        ensureReflectionInitialized();
        return reflectionError == null;
    }

    @Override
    public String unavailableReason() {
        if (!FabricLoader.getInstance().isModLoaded(SMC_MOD_ID)) {
            return "smcmod_not_loaded";
        }
        ensureReflectionInitialized();
        return reflectionError == null ? null : reflectionError.getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> getChunkToRegionSnapshot() throws ReflectiveOperationException {
        ensureReflectionInitialized();
        if (reflectionError != null) {
            throw new ReflectiveOperationException("simmc reflection unavailable", reflectionError);
        }
        Object regionManager = regionManagerField.get(null);
        if (regionManager == null) {
            return null;
        }
        return (Map<?, ?>) chunkToRegionField.get(regionManager);
    }

    private String toHexColor(Object region) throws ReflectiveOperationException {
        Object rawColor = regionColorMethod.invoke(region);
        int color = rawColor instanceof Number number ? number.intValue() : 0xFFFFFF;
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private boolean isCore(Object region) throws ReflectiveOperationException {
        Object rawValue = regionIsCoreMethod.invoke(region);
        return rawValue instanceof Boolean bool && bool;
    }

    private synchronized void ensureReflectionInitialized() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        try {
            Class<?> regionManagerClass = Class.forName("com.simmc.mod.region.RegionManager");
            regionManagerField = regionManagerClass.getField("regionManager");

            Class<?> regionManagerImplClass = Class.forName("com.simmc.mod.region.RegionManagerImpl");
            chunkToRegionField = regionManagerImplClass.getDeclaredField("chunkToRegion");
            chunkToRegionField.setAccessible(true);

            Class<?> regionClass = Class.forName("com.simmc.mod.region.Region");
            regionColorMethod = regionClass.getMethod("color");
            regionIsCoreMethod = regionClass.getMethod("isCore");
        } catch (Throwable throwable) {
            reflectionError = throwable;
            LOGGER.warn("Failed to initialize SimMC battle map bridge: {}", throwable.getMessage());
        }
    }

    private record ObservedChunkCell(int chunkX, int chunkZ, String colorRaw, String symbol) {
    }
}
