package io.github.opencubicchunks.cubicchunks.compat.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

/**
 * Distant Horizons in cubic worlds: each cubic server level it loads (in singleplayer too, where its world generation runs on the integrated
 * server) gets a CubicDhWorldGenerator, which is where its terrain comes from (the cubic level's columns hold no blocks, see MixinSharedApi).
 * Only touched when Distant Horizons is installed, as this class uses its API.
 */
public final class DhCubes {
    private DhCubes() {
    }

    /** Called at startup; does nothing without Distant Horizons. */
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            Registration.register();
        }
    }

    /**
     * What Distant Horizons holds for the column at x, z of the singleplayer level, top down (for tests): each run's Y range and block, up to
     * the first few below the surface. Only call with Distant Horizons installed.
     */
    public static String describeColumn(int x, int z) {
        return Registration.describeColumn(x, z);
    }

    /** Kept apart so that DhCubes itself can be loaded without Distant Horizons' classes. */
    private static final class Registration {
        static void register() {
            DhApi.events.bind(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
                @Override public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> event) {
                    IDhApiLevelWrapper levelWrapper = event.value.levelWrapper;
                    if (levelWrapper.getWrappedMcObject() instanceof ServerLevel level && DhWindow.isCubic(level)) {
                        DhApiResult<Void> result = DhApi.worldGenOverrides.registerWorldGeneratorOverride(levelWrapper,
                                new CubicDhWorldGenerator(level, levelWrapper));
                        CubicChunks.LOGGER.info("Distant Horizons gets cubic level {} from Y {} to {}: {}", level.dimension().identifier(),
                                DhWindow.minY(), DhWindow.minY() + DhWindow.height() - 1, result.success ? "generator registered" : result.message);
                    }
                }
            });
        }

        static String describeColumn(int x, int z) {
            IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
            if (levelWrapper == null) {
                return "no level";
            }
            DhApiResult<DhApiTerrainDataPoint[]> column = DhApi.Delayed.terrainRepo.getColumnDataAtBlockPos(levelWrapper, x, z,
                    DhApi.Delayed.terrainRepo.createSoftCache());
            if (!column.success || column.payload == null) {
                return "none (" + column.message + ")";
            }
            StringBuilder out = new StringBuilder();
            int solid = 0;
            for (DhApiTerrainDataPoint point : column.payload) {
                if (point == null) {
                    continue;
                }
                out.append(' ').append(point.bottomYBlockPos).append("..").append(point.topYBlockPos - 1).append(' ')
                        .append(point.blockStateWrapper.getSerialString()).append(" sky ").append(point.skyLightLevel);
                if (!point.blockStateWrapper.isAir() && ++solid >= 2) {
                    break;
                }
            }
            return out.length() == 0 ? "empty" : out.toString();
        }
    }
}
