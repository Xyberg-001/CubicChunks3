package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.List;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.SectionPos;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.server.level.ServerPlayer;

public interface CubeHolder {
    /**
     * A section of the cube had its light change (vanilla's sectionLightChanged, for a chunk's section): true when it is the first change to
     * the layer there since the last broadcast, so the holder is to be broadcast.
     */
    boolean cc_sectionLightChanged(LightLayer layer, SectionPos sectionPos);

    @FunctionalInterface
    interface LevelChangeListener {
        void cc_onLevelChange(CubePos cubePos, IntSupplier queueLevelGetter, int ticketLevel, IntConsumer queueLevelSetter);
    }

    interface PlayerProvider {
        /**
         * Returns the players tracking the given cube.
         */
        List<ServerPlayer> cc_getPlayers(CubePos pos, boolean boundaryOnly);
    }
}
