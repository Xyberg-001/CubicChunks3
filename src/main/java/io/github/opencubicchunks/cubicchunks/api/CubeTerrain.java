package io.github.opencubicchunks.cubicchunks.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cube being generated (see {@link CubeGenerator}): {@link #SIZE} blocks on each side from its lowest corner. Positions are world block
 * positions; writes outside the cube are ignored, so a generator can paint whole columns and keep what falls in the cube. Writes go straight
 * into the cube's sections (no neighbour updates, light or block entities); stairs, walls, fences and the like that must connect to their
 * neighbours can be marked for post-processing.
 */
public interface CubeTerrain {
    /** Blocks on each side of a cube. */
    int SIZE = io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_BLOCKS;

    ServerLevel level();

    int minX();

    int minY();

    int minZ();

    default int maxY() {
        return minY() + SIZE - 1;
    }

    void setBlock(int x, int y, int z, BlockState state);

    /** Sets y0 to y1 (inclusive) of the column at x, z, as far as the cube reaches. */
    void fill(int x, int y0, int y1, int z, BlockState state);

    BlockState getBlock(int x, int y, int z);

    /** Marks the block for post-processing (it is fitted to its neighbours once they exist), as stairs and fences need. */
    void markForPostProcessing(int x, int y, int z);

    /** Fills the cube's biomes from the resolver (the level's biome source, usually). */
    void fillBiomes(BiomeResolver resolver, Climate.Sampler sampler);
}
