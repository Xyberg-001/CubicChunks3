package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * How long players have been near a place, for regional difficulty, in a cubic level. Vanilla keeps it per chunk, counting each tick a
 * chunk is within spawning range of a player; a cubic level counts it per cube the same way (and saves it with the cube), so a place's time
 * is its cube's. Columns keep none there: they are not saved.
 */
public final class CubicInhabitedTime {
    private CubicInhabitedTime() {}

    /** The inhabited time of the loaded cube at pos, or 0 when it is not loaded (as vanilla's for a chunk not loaded). Any thread. */
    public static long at(ServerLevel level, BlockPos pos) {
        CubeAccess cube = ((ServerCubeCache) level.getChunkSource()).cc_getFullCubeNow(CubePos.from(pos));
        return cube == null ? 0L : cube.getInhabitedTime();
    }
}
