package io.github.opencubicchunks.cubicchunks.world.level;

import java.util.List;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.NaturalSpawner$SpawnStateAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Natural spawning in a cubic level, cube by cube. Vanilla tries once per category per spawning chunk each tick, from a random height between
 * the world's floor and the column's surface (NaturalSpawner.spawnForChunk); a cubic column has neither, so each spawning cube tries once per
 * category from a random block of its own. Everything else is vanilla's spawnCategoryForPosition: which mob, groups, light, room, spawn
 * potential, and the global and per-player caps of the spawn state.
 */
public final class CubicNaturalSpawner {
    private CubicNaturalSpawner() {}

    public static void spawnForCube(ServerLevel level, LevelCube cube, NaturalSpawner.SpawnState state, List<MobCategory> categories) {
        NaturalSpawner$SpawnStateAccess access = (NaturalSpawner$SpawnStateAccess) state;
        for (MobCategory category : categories) {
            BlockPos start = randomPosWithin(level, cube);
            if (start == null) {
                continue;
            }
            ChunkPos chunkPos = ChunkPos.containing(start);
            if (!access.cc_canSpawnForCategoryLocal(category, chunkPos)) {
                continue;
            }
            // vanilla wants the chunk the start is in (for bookkeeping; blocks are read from the level in a cubic one, see MixinNaturalSpawner)
            LevelChunk column = level.getChunk(chunkPos.x(), chunkPos.z());
            NaturalSpawner.spawnCategoryForPosition(category, level, column, start, access::cc_canSpawn, access::cc_afterSpawn);
        }
    }

    /** A random block of the cube, or null when its section is only air (nothing there could be stood on, nor below it in the section). */
    private static @Nullable BlockPos randomPosWithin(ServerLevel level, LevelCube cube) {
        CubePos pos = cube.cc_getCubePos();
        int x = level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
        int y = level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
        int z = level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
        BlockPos start = new BlockPos(pos.minCubeX() + x, pos.minCubeY() + y, pos.minCubeZ() + z);
        return cube.getSection(Coords.blockToIndex(start)).hasOnlyAir() ? null : start;
    }
}
