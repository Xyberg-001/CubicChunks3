package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.render.chunk.occlusion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.RayOcclusionSectionTree;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The ray occlusion test treats the level's heights as open; in a cubic level they are its world's. */
@Mixin(RayOcclusionSectionTree.class)
public abstract class MixinRayOcclusionSectionTree {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinSectionY()I"))
    private int cc_cubicMinSectionY(Level level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.minSectionY(level) : original.call(level);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxSectionY()I"))
    private int cc_cubicMaxSectionY(Level level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.maxSectionY(level) : original.call(level);
    }
}
