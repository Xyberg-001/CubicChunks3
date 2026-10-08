package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * One 16x16 column of a cubic level as the light engine sees it: blocks come from whichever cube holds them (air where none is given), light
 * sources from the column's cubes, and sky sources from {@link CubicSkyLightSources}.
 */
public final class CubicLightColumn implements LightChunk {
    private final CubeLightView view;
    private final int chunkX;
    private final int chunkZ;
    private final CubicSkyLightSources skyLightSources;

    CubicLightColumn(CubeLightView view, int chunkX, int chunkZ) {
        this.view = view;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.skyLightSources = new CubicSkyLightSources(view, chunkX, chunkZ);
    }

    CubicSkyLightSources sources() {
        return this.skyLightSources;
    }

    @Override public ChunkSkyLightSources getSkyLightSources() {
        return this.skyLightSources;
    }

    @Override public void findBlockLightSources(BiConsumer<BlockPos, BlockState> output) {
        int cubeX = Coords.blockToCube(Coords.sectionToMinBlock(this.chunkX));
        int cubeZ = Coords.blockToCube(Coords.sectionToMinBlock(this.chunkZ));
        for (CubeAccess cube : this.view.cubesTopDown(cubeX, cubeZ)) {
            findSources(cube, Coords.cubeLocalSection(this.chunkX), Coords.cubeLocalSection(this.chunkZ), output);
        }
    }

    /** The light sources in this column's part of one cube (vanilla's ChunkAccess.findBlockLightSources, for a cube's sections). */
    private static void findSources(CubeAccess cube, int sectionInCubeX, int sectionInCubeZ, BiConsumer<BlockPos, BlockState> output) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = cube.getSections();
        int minX = cube.cc_getCubePos().minCubeX() + sectionInCubeX * 16;
        int minZ = cube.cc_getCubePos().minCubeZ() + sectionInCubeZ * 16;
        for (int sectionInCubeY = 0; sectionInCubeY < CubicConstants.DIAMETER_IN_SECTIONS; sectionInCubeY++) {
            LevelChunkSection section = sections[Coords.sectionToIndex(sectionInCubeX, sectionInCubeY, sectionInCubeZ)];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getLightEmission() != 0)) {
                continue;
            }
            int minY = cube.cc_getCubePos().minCubeY() + sectionInCubeY * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.getLightEmission() != 0) {
                            output.accept(pos.set(minX + x, minY + y, minZ + z), state);
                        }
                    }
                }
            }
        }
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        CubeAccess cube = this.view.cube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
        return cube == null ? Blocks.AIR.defaultBlockState() : cube.getBlockState(pos);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        CubeAccess cube = this.view.cube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
        return cube == null ? Fluids.EMPTY.defaultFluidState() : cube.getFluidState(pos);
    }

    @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null; // light reads only block states
    }

    @Override public int getMinY() {
        return CubicHeight.minY();
    }

    @Override public int getHeight() {
        return CubicHeight.maxY() - CubicHeight.minY() + 1;
    }
}
