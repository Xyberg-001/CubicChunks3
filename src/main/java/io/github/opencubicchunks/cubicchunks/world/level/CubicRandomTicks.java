package io.github.opencubicchunks.cubicchunks.world.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSections;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * A cube's share of vanilla's ServerLevel.tickChunk: random block ticks in each of its sections, and ice and snow where the cube holds the
 * surface. Vanilla finds the surface from the column's heightmap, which a cubic column doesn't keep; a cube looks down its own blocks instead
 * and counts a spot as the surface when it sees the full sky (so snow can fall under glass that is more than a cube higher up).
 */
public final class CubicRandomTicks {
    private CubicRandomTicks() {}

    /** How many of vanilla's 16 by 16 columns a cube's top covers: each gets vanilla's tries at ice and snow. */
    static final int COLUMNS_PER_CUBE = CubicConstants.DIAMETER_IN_SECTIONS * CubicConstants.DIAMETER_IN_SECTIONS;

    public static void tickCube(ServerLevel level, LevelCube cube, int tickSpeed) {
        CubePos cubePos = cube.cc_getCubePos();
        ProfilerFiller profiler = Profiler.get();
        profiler.push("iceandsnow");
        for (int i = 0; i < tickSpeed * COLUMNS_PER_CUBE; i++) {
            if (level.getRandom().nextInt(48) == 0) {
                int x = cubePos.minCubeX() + level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
                int z = cubePos.minCubeZ() + level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
                BlockPos top = surfaceIn(level, cube, x, z);
                if (top != null) {
                    level.tickPrecipitation(top); // finds no heightmap in a cubic level and takes this as the surface (MixinServerLevel)
                }
            }
        }

        profiler.popPush("tickBlocks");
        if (tickSpeed > 0) {
            LevelChunkSection[] sections = cube.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (!section.isRandomlyTicking()) {
                    continue;
                }
                SectionPos sectionPos = CubeSections.sectionPosOf(cubePos, i);
                int minX = sectionPos.minBlockX();
                int minY = sectionPos.minBlockY();
                int minZ = sectionPos.minBlockZ();
                for (int t = 0; t < tickSpeed; t++) {
                    BlockPos pos = level.getBlockRandomPos(minX, minY, minZ, 15);
                    profiler.push("randomTick");
                    BlockState state = section.getBlockState(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ);
                    if (state.isRandomlyTicking()) {
                        state.randomTick(level, pos, level.getRandom());
                    }
                    FluidState fluid = state.getFluidState();
                    if (fluid.isRandomlyTicking()) {
                        fluid.randomTick(level, pos, level.getRandom());
                    }
                    profiler.pop();
                }
            }
        }
        profiler.pop();
    }

    /**
     * The spot above the cube's highest block at x, z that vanilla's MOTION_BLOCKING heightmap would count, if that spot is open and the sky
     * reaches it; null when not, or the cube has no such block there (then a lower cube holds this column's surface).
     */
    static @Nullable BlockPos surfaceIn(ServerLevel level, LevelCube cube, int x, int z) {
        CubePos cubePos = cube.cc_getCubePos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, cubePos.maxCubeY(), z);
        while (pos.getY() >= cubePos.minCubeY()) {
            if (cube.getSection(Coords.blockToIndex(pos)).hasOnlyAir()) {
                pos.setY(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(pos.getY())) - 1); // the section below
                continue;
            }
            if (!Heightmap.Types.MOTION_BLOCKING.isOpaque().test(cube.getBlockState(pos))) {
                pos.move(0, -1, 0);
            } else {
                // the spot above must be open too (it lies in the next cube up when this is the cube's top row), and see the sky
                BlockPos top = pos.above();
                boolean open = !Heightmap.Types.MOTION_BLOCKING.isOpaque().test(level.getBlockState(top));
                return open && level.getBrightness(LightLayer.SKY, top) == 15 ? top : null;
            }
        }
        return null;
    }
}
