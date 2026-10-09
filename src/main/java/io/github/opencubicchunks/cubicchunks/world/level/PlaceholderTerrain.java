package io.github.opencubicchunks.cubicchunks.world.level;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The temporary terrain cubic worlds generate until they have a real generator: smooth stone hills of sine waves around Y 5, solid all the
 * way down. Cube generation builds it (CubeStatusTasks.buildTerrain); Distant Horizons is shown it for land no cube was made for.
 */
public final class PlaceholderTerrain {
    private static final int AMPLITUDE = 20;

    private PlaceholderTerrain() {
    }

    /** The highest solid Y at x, z. */
    public static int surfaceY(int x, int z) {
        return CubicChunks.SUPERFLAT_HEIGHT - (int) Math.round((AMPLITUDE * (Math.sin(x / 8.0 + z / 21.0) + Math.cos(z / 13.0))) / 2.0);
    }

    /** The highest Y a cube needs filling up to (no surface is above it). */
    public static int maxSurfaceY() {
        return CubicChunks.SUPERFLAT_HEIGHT + AMPLITUDE;
    }

    public static BlockState block() {
        return Blocks.SMOOTH_STONE.defaultBlockState();
    }
}
