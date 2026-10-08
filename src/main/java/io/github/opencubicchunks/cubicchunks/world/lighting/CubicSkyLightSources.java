package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.Arrays;
import java.util.List;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Where sky light starts in one 16x16 column of a cubic level, from the cubes a {@link CubeLightView} gives: walking down from the top of the
 * highest cube, the lowest source is the top of the first edge vanilla would call occluded. A cubic column has no top, so what lies above
 * those cubes counts as open sky, and a column open all the way through them has sources below the world, as vanilla says for an empty chunk.
 * Entries are worked out when asked for and forgotten when the column's cubes or blocks change. The light engine may ask from its own thread
 * while blocks change on another; a forgotten entry is simply worked out again.
 */
public final class CubicSkyLightSources extends ChunkSkyLightSources {
    static final int UNKNOWN = Integer.MAX_VALUE;

    private final CubeLightView view;
    private final int chunkX;
    private final int chunkZ;
    private final int[] lowestSourceY = new int[16 * 16];

    CubicSkyLightSources(CubeLightView view, int chunkX, int chunkZ) {
        super(view.heightAccessor());
        this.view = view;
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
            this.workOutUnknown();
            value = this.lowestSourceY[index];
        }
        return value;
    }

    @Override public int getHighestLowestSourceY() {
        this.workOutUnknown();
        int highest = NEGATIVE_INFINITY;
        for (int value : this.lowestSourceY) {
            highest = Math.max(highest, value);
        }
        return highest;
    }

    /** Works out every unknown entry from one look at the column's cubes. */
    private void workOutUnknown() {
        List<CubeAccess> cubes = null;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = x + z * 16;
                if (this.lowestSourceY[index] == UNKNOWN) {
                    if (cubes == null) {
                        int cubeX = Coords.blockToCube(Coords.sectionToMinBlock(this.chunkX));
                        int cubeZ = Coords.blockToCube(Coords.sectionToMinBlock(this.chunkZ));
                        cubes = this.view.cubesTopDown(cubeX, cubeZ);
                    }
                    this.lowestSourceY[index] = findLowestSourceY(cubes, Coords.cubeLocalSection(this.chunkX), Coords.cubeLocalSection(this.chunkZ), x, z);
                }
            }
        }
    }

    private static int findLowestSourceY(List<CubeAccess> cubesTopDown, int sectionInCubeX, int sectionInCubeZ, int localX, int localZ) {
        BlockState topState = Blocks.AIR.defaultBlockState();
        int previousCubeY = Integer.MAX_VALUE;
        for (CubeAccess cube : cubesTopDown) {
            int cubeY = cube.cc_getCubePos().getY();
            if (cubeY != previousCubeY - 1) {
                topState = Blocks.AIR.defaultBlockState(); // a gap: cubes not given count as air
            }
            previousCubeY = cubeY;
            LevelChunkSection[] sections = cube.getSections();
            for (int sectionInCubeY = CubicConstants.DIAMETER_IN_SECTIONS - 1; sectionInCubeY >= 0; sectionInCubeY--) {
                LevelChunkSection section = sections[Coords.sectionToIndex(sectionInCubeX, sectionInCubeY, sectionInCubeZ)];
                if (section.hasOnlyAir()) {
                    topState = Blocks.AIR.defaultBlockState();
                    continue;
                }
                int sectionMinY = Coords.cubeToMinBlock(cubeY) + sectionInCubeY * 16;
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
