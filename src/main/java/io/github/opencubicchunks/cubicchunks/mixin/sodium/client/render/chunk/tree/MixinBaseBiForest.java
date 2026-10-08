package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.render.chunk.tree;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.BaseBiForest;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium keeps sections in two fixed trees when the level is at most 64 sections tall, else in as many as needed; a cubic level is as tall as
 * its world, not its dimension, so it gets the latter (two trees would drop the sections outside them).
 */
@Mixin(BaseBiForest.class)
public abstract class MixinBaseBiForest {
    @WrapOperation(method = "checkApplicable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight()I"))
    private static int cc_cubicHeight(Level level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? CubicHeight.maxY(level) - CubicHeight.minY(level) + 1 : original.call(level);
    }
}
