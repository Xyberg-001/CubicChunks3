package io.github.opencubicchunks.cubicchunks.world.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ServerLevelAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning in a cubic level, cube by cube: vanilla's ServerLevel.tickThunder, which strikes a chunk's surface 1 in 100,000 ticks during a
 * thunderstorm. A cube covers several chunks' area, so it tries that much more often, at a random spot of its own; only the cube that holds
 * the surface there (CubicRandomTicks.surfaceIn) can strike, so each spot of open ground is struck as often as in vanilla.
 */
public final class CubicThunder {
    private CubicThunder() {}

    private static final int CHANCE = 100000 / CubicRandomTicks.COLUMNS_PER_CUBE;

    public static void tickCube(ServerLevel level, LevelCube cube) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("thunder");
        if (level.isRaining() && level.isThundering() && level.getRandom().nextInt(CHANCE) == 0) {
            CubePos cubePos = cube.cc_getCubePos();
            int x = cubePos.minCubeX() + level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
            int z = cubePos.minCubeZ() + level.getRandom().nextInt(CubicConstants.DIAMETER_IN_BLOCKS);
            strikeIn(level, cube, x, z);
        }
        profiler.pop();
    }

    /** A strike at x, z when this cube holds the surface there under the open sky (see CubicRandomTicks.surfaceIn), else nothing. */
    public static @Nullable LightningBolt strikeIn(ServerLevel level, LevelCube cube, int x, int z) {
        BlockPos surface = CubicRandomTicks.surfaceIn(level, cube, x, z);
        return surface == null ? null : strikeNear(level, surface);
    }

    /**
     * The rest of vanilla's tickThunder from a surface spot: a lightning rod nearby or a living thing under the sky close by draws the strike
     * (findLightningTargetAround), and a skeleton trap may come with it. Returns the bolt, or null when it doesn't rain where it would land.
     */
    public static @Nullable LightningBolt strikeNear(ServerLevel level, BlockPos surface) {
        BlockPos pos = ((ServerLevelAccess) level).cc_findLightningTargetAround(surface);
        if (!level.isRainingAt(pos)) {
            return null;
        }
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        boolean isTrap = level.getGameRules().get(GameRules.SPAWN_MOBS)
                && level.getRandom().nextDouble() < difficulty.getEffectiveDifficulty() * 0.01
                && !level.getBlockState(pos.below()).is(BlockTags.LIGHTNING_RODS);
        if (isTrap) {
            SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.EVENT);
            if (horse != null) {
                horse.setTrap(true);
                horse.setAge(0);
                horse.setPos(pos.getX(), pos.getY(), pos.getZ());
                level.addFreshEntity(horse);
            }
        }
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt != null) {
            bolt.snapTo(Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(isTrap);
            level.addFreshEntity(bolt);
        }
        return bolt;
    }
}
