package io.github.opencubicchunks.cubicchunks;

import java.lang.reflect.InvocationTargetException;

import io.github.opencubicchunks.cc_core.CubicChunksBase;
import io.github.opencubicchunks.cc_core.config.EarlyConfig;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.config.CommonConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import io.github.opencubicchunks.cubicchunks.network.CCNetworkHandler;
import io.github.opencubicchunks.cubicchunks.server.commands.CubicChunksCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;

/** The mod's entry point on both sides. */
public class CubicChunks extends CubicChunksBase implements ModInitializer {
    /**
     * true when running in a junit test, false otherwise.
     */
    public static boolean IS_IN_TEST = false;
    protected static CommonConfig config = null;
    // For hardcoding height in P1
    public static final int SUPERFLAT_HEIGHT = 5;

    @Override public void onInitialize() {
        ChunkMap.class.getName();
        LOGGER.info("Block positions packed as {} bits of x and z and {} of Y: cubic worlds reach Y {}..{}, x and z +-{}",
                net.minecraft.core.BlockPos.PACKED_HORIZONTAL_LENGTH, net.minecraft.core.BlockPos.PACKED_Y_LENGTH,
                io.github.opencubicchunks.cubicchunks.world.level.CubicHeight.minY(), io.github.opencubicchunks.cubicchunks.world.level.CubicHeight.maxY(),
                io.github.opencubicchunks.cubicchunks.world.level.CubicHeight.horizontalLimit());
//        if (!(CubeMap.class.isAssignableFrom(ChunkMap.class))) {
//            throw new IllegalStateException("Mixin not applied!");
//        }
        EarlyConfig.getDiameterInSections();

        Coords.blockToIndex(new BlockPos(0, 0, 0));
//        ClassDuplicator.init();
        if (System.getProperty("cubicchunks.debug", "false").equalsIgnoreCase("true")) {
            try {
                Class.forName("io.github.opencubicchunks.cubicchunks.debug.DebugVisualization").getMethod("enable").invoke(null);
                SharedConstants.IS_RUNNING_IN_IDE = true;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
                LOGGER.catching(e);
            }
        }

        CCNetworkHandler.register();
        io.github.opencubicchunks.cubicchunks.compat.dh.DhCubes.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.register((server, level) ->
                io.github.opencubicchunks.cubicchunks.api.CubicApi.forgetLevel(level));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> CubicChunksCommand.register(dispatcher));
    }

    public static CommonConfig config() {
        if (config == null) {
            config = CommonConfig.getConfig();
        }
        return config;
    }
}
