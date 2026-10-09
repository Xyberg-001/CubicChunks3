package io.github.opencubicchunks.cubicchunks.mixin.voxy.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import io.github.opencubicchunks.cubicchunks.client.render.VoxyCubes;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Voxy draws its distant terrain between the level's lowest and highest section, read as its renderer is made; in a cubic level those are the
 * world's heights (within what Voxy holds, see VoxyCubes), not the dimension's, so terrain above or below the dimension's range is drawn
 * too. Voxy is targeted by name: it is not on a maven to compile against.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem")
public abstract class MixinVoxyRenderSystem {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSectionY()I"))
    private int cc_cubicMinSectionY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? Math.max(SodiumCubes.minSectionY(level), VoxyCubes.minSectionY()) : original.call(level);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSectionY()I"))
    private int cc_cubicMaxSectionY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? Math.min(SodiumCubes.maxSectionY(level), VoxyCubes.maxSectionY()) : original.call(level);
    }
}
