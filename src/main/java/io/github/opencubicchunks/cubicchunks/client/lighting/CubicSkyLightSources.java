package io.github.opencubicchunks.cubicchunks.client.lighting;

import java.util.Arrays;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Where sky light starts in one 16x16 column of a cubic level, from the cubes the client holds: walking down from the top of the highest loaded
 * cube, the lowest source is the top of the first edge vanilla would call occluded. A cubic column has no top, so what lies above the loaded
 * cubes counts as open sky, and a column open all the way through them has sources below the world, as vanilla says for an empty chunk.
 * Entries are worked out when asked for and forgotten when the column's cubes or blocks change.
 */
public final class CubicSkyLightSources extends ChunkSkyLightSources {
    static final int UNKNOWN = Integer.MAX_VALUE;

    private final CubicClientLight light;
    private final int chunkX;
    private final int chunkZ;
    private final int[] lowestSourceY = new int[16 * 16];

    CubicSkyLightSources(LevelHeightAccessor level, CubicClientLight light, int chunkX, int chunkZ) {
        super(level);
        this.light = light;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Arrays.fill(this.lowestSourceY, UNKNOWN);
    }

    /** The entries worked out so far ({@link #UNKNOWN} where none was asked for). */
    int[] known() {
        return this.lowestSourceY.clone();
    }

    void forgetAll() {
        Arrays.fill(this.lowestSourceY, UNKNOWN);
    }

    void forget(int localX, int localZ) {
        this.lowestSourceY[localX + localZ * 16] = UNKNOWN;
    }

    @Override public int getLowestSourceY(int x, int z) {
        int index = x + z * 16;
        int value = this.lowestSourceY[index];
        if (value == UNKNOWN) {
            value = this.findLowestSourceY(x, z);
            this.lowestSourceY[index] = value;
        }
        return value;
    }

    @Override public int getHighestLowestSourceY() {
        int highest = NEGATIVE_INFINITY;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                highest = Math.max(highest, this.getLowestSourceY(x, z));
            }
        }
        return highest;
    }

    private int findLowestSourceY(int localX, int localZ) {
        int blockX = Coords.sectionToMinBlock(this.chunkX) + localX;
        int blockZ = Coords.sectionToMinBlock(this.chunkZ) + localZ;
        int cubeX = Coords.blockToCube(blockX);
        int cubeZ = Coords.blockToCube(blockZ);
        int sectionInCubeX = Coords.blockToCubeLocalSection(blockX);
        int sectionInCubeZ = Coords.blockToCubeLocalSection(blockZ);
        BlockState topState = Blocks.AIR.defaultBlockState();
        for (int cubeY = this.light.topCubeY(); cubeY >= this.light.bottomCubeY(); cubeY--) {
            LevelCube cube = this.light.cube(cubeX, cubeY, cubeZ);
            if (cube == null) {
                topState = Blocks.AIR.defaultBlockState(); // not held: counted open
                continue;
            }
            LevelChunkSection[] sections = cube.getSections();
            for (int sectionInCubeY = CubicConstants.DIAMETER_IN_SECTIONS - 1; sectionInCubeY >= 0; sectionInCubeY--) {
                LevelChunkSection section = sections[Coords.sectionToIndex(sectionInCubeX, sectionInCubeY, sectionInCubeZ)];
                int sectionMinY = Coords.cubeToMinBlock(cubeY) + sectionInCubeY * 16;
                if (section.hasOnlyAir()) {
                    topState = Blocks.AIR.defaultBlockState();
                    continue;
                }
                for (int y = 15; y >= 0; y--) {
                    BlockState bottomState = section.getBlockState(localX, y, localZ);
                    if (isEdgeOccluded(topState, bottomState)) {
                        return sectionMinY + y + 1;
                    }
                    topState = bottomState;
                }
            }
        }
        return NEGATIVE_INFINITY;
    }

    /** Vanilla's rule (ChunkSkyLightSources.isEdgeOccluded, private there). */
    static boolean isEdgeOccluded(BlockState topState, BlockState bottomState) {
        if (bottomState.getLightDampening() != 0) {
            return true;
        }
        return Shapes.faceShapeOccludes(LightEngine.getOcclusionShape(topState, Direction.DOWN), LightEngine.getOcclusionShape(bottomState, Direction.UP));
    }
}
