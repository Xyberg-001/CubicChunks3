package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spawning from a position in a cubic level (see CubicNaturalSpawner): the chunk vanilla is given is the position's column, which holds no
 * blocks of its own there, so the start block is read from the level; and a nearby position may take a mob when its cube ticks entities
 * (vanilla asks whether the column does).
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawner {
    @WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
            + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState cc_startBlockFromLevel(ChunkAccess chunk, BlockPos pos, Operation<BlockState> original, @Local(argsOnly = true) ServerLevel level) {
        return ((CanBeCubic) level).cc_isCubic() ? level.getBlockState(pos) : original.call(chunk, pos);
    }

    @WrapOperation(method = "isRightDistanceToPlayerAndSpawnPoint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;canSpawnEntitiesInChunk(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private static boolean cc_cubeTicksEntities(ServerLevel level, ChunkPos chunkPos, Operation<Boolean> original,
            @Local(argsOnly = true) BlockPos.MutableBlockPos pos) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return original.call(level, chunkPos);
        }
        return level.isPositionEntityTicking(pos) && level.getWorldBorder().isWithinBounds(pos);
    }
}
