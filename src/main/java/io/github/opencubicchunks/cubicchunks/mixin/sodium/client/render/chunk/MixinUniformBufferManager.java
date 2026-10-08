package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.render.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium sizes a per-region buffer from the render distance across and the level's height up (it grows it if that falls short). A cubic
 * client holds cubes as far up and down as across, so the height counted is the render distance's, a little more for the cube edges, within
 * the world's heights: counting the whole world would make it far too big.
 */
@Mixin(UniformBufferManager.class)
public abstract class MixinUniformBufferManager {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSectionY()I"))
    private int cc_heldMaxSectionY(ClientLevel level, Operation<Integer> original, @Local(argsOnly = true) int renderDistance) {
        if (!SodiumCubes.isCubic(level)) {
            return original.call(level);
        }
        int held = 2 * renderDistance + 1 + 2 * CubicConstants.DIAMETER_IN_SECTIONS;
        return SodiumCubes.minSectionY(level) + Math.min(held, SodiumCubes.maxSectionY(level) - SodiumCubes.minSectionY(level) + 1) - 1;
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSectionY()I"))
    private int cc_heldMinSectionY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.minSectionY(level) : original.call(level);
    }
}
