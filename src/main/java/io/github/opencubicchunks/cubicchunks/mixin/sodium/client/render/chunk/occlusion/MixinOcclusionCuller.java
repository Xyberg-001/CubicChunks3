package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.render.chunk.occlusion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The culler searches from the camera's section when it is inside the level's heights, and from the top or bottom layer when it is outside;
 * in a cubic level those heights are its world's.
 */
@Mixin(OcclusionCuller.class)
public abstract class MixinOcclusionCuller {
    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinSectionY()I"))
    private int cc_cubicMinSectionY(Level level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.minSectionY(level) : original.call(level);
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxSectionY()I"))
    private int cc_cubicMaxSectionY(Level level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.maxSectionY(level) : original.call(level);
    }
}
