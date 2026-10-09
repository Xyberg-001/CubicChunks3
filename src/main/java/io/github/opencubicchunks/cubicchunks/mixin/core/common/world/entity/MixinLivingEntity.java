package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla's client lets an entity fall at full speed only while the chunk below it is loaded, and sinks it slowly otherwise, so a player
 * never outruns the world arriving. In a cubic level the ground below is in a cube, which may not have arrived while its column has (a
 * player falling from Y 1000 fell at full speed into cubes neither side had loaded yet): the cube below is what counts.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasChunkAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean cc_cubeBelowLoaded(Level level, BlockPos below, Operation<Boolean> original) {
        if (((CanBeCubic) level).cc_isCubic()) {
            return ((CubicLevelReader) level).cc_hasCubeAt(below);
        }
        return original.call(level, below);
    }
}
