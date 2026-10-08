package io.github.opencubicchunks.cubicchunks.client.color.block;

import io.github.opencubicchunks.cc_core.api.CubePos;

/**
 * A client's cache of blended biome colours (BlockTintCache), kept per column and per Y as in vanilla. Vanilla drops the columns around a
 * chunk that arrives, since blending reads the biomes beside a block, which were missing before; a cubic client gets cubes, so it drops
 * what a cube's biomes may change: the layers of the cube's height (and one biome cell, 4 blocks, above and below, which the biome lookup
 * can reach) in its columns and those beside them.
 */
public interface CubicBlockTintCache {
    int BIOME_REACH = 4;

    void cc_invalidateForCube(CubePos cubePos);

    /** BlockTintCache's per-column data. */
    interface Column {
        void cc_removeLayers(int minY, int maxY);
    }
}
