package io.github.opencubicchunks.cubicchunks.world.level.cube.status;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.api.CubeTerrain;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * A cube being generated, for a {@link io.github.opencubicchunks.cubicchunks.api.CubeGenerator}: writes go straight into the sections'
 * palettes (the cube is still a proto cube on a generation thread), and each touched section's counts are worked out once at the end
 * ({@link #finish}), as vanilla's noise step does.
 */
final class CubeTerrainImpl implements CubeTerrain {
    private final ServerLevel level;
    private final CubeAccess cube;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final boolean[] touched;

    CubeTerrainImpl(ServerLevel level, CubeAccess cube) {
        this.level = level;
        this.cube = cube;
        this.minX = cube.cc_getCubePos().minCubeX();
        this.minY = cube.cc_getCubePos().minCubeY();
        this.minZ = cube.cc_getCubePos().minCubeZ();
        this.touched = new boolean[cube.getSections().length];
    }

    @Override public ServerLevel level() {
        return this.level;
    }

    @Override public int minX() {
        return this.minX;
    }

    @Override public int minY() {
        return this.minY;
    }

    @Override public int minZ() {
        return this.minZ;
    }

    private boolean inside(int x, int y, int z) {
        return x >= this.minX && x < this.minX + SIZE && y >= this.minY && y < this.minY + SIZE && z >= this.minZ && z < this.minZ + SIZE;
    }

    private int sectionIndex(int x, int y, int z) {
        return Coords.sectionToIndex(Coords.blockToCubeLocalSection(x), Coords.blockToCubeLocalSection(y), Coords.blockToCubeLocalSection(z));
    }

    @Override public void setBlock(int x, int y, int z, BlockState state) {
        if (!this.inside(x, y, z)) {
            return;
        }
        int index = this.sectionIndex(x, y, z);
        this.cube.getSections()[index].getStates().getAndSetUnchecked(x & 15, y & 15, z & 15, state);
        this.touched[index] = true;
    }

    @Override public void fill(int x, int y0, int y1, int z, BlockState state) {
        if (x < this.minX || x >= this.minX + SIZE || z < this.minZ || z >= this.minZ + SIZE) {
            return;
        }
        int from = Math.max(y0, this.minY);
        int to = Math.min(y1, this.minY + SIZE - 1);
        int lx = x & 15;
        int lz = z & 15;
        for (int y = from; y <= to; ) {
            int index = this.sectionIndex(x, y, z);
            int end = Math.min(to, (y & ~15) + 15);
            var states = this.cube.getSections()[index].getStates();
            for (int yy = y; yy <= end; yy++) {
                states.getAndSetUnchecked(lx, yy & 15, lz, state);
            }
            this.touched[index] = true;
            y = end + 1;
        }
    }

    @Override public BlockState getBlock(int x, int y, int z) {
        if (!this.inside(x, y, z)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return this.cube.getSections()[this.sectionIndex(x, y, z)].getBlockState(x & 15, y & 15, z & 15);
    }

    @Override public void markForPostProcessing(int x, int y, int z) {
        if (this.inside(x, y, z)) {
            this.cube.markPosForPostProcessing(new BlockPos(x, y, z));
        }
    }

    @Override public void fillBiomes(BiomeResolver resolver, Climate.Sampler sampler) {
        this.cube.fillBiomesFromNoise(resolver, sampler);
    }

    /** The counts (blocks, fluids, ticking) the direct writes skipped, for the sections written to. */
    void finish() {
        LevelChunkSection[] sections = this.cube.getSections();
        for (int i = 0; i < this.touched.length; i++) {
            if (this.touched[i]) {
                sections[i].recalcBlockCounts();
            }
        }
    }
}
