package io.github.opencubicchunks.cubicchunks.mixin.dh.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import io.github.opencubicchunks.cubicchunks.compat.dh.DhWindow;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** As MixinServerLevelWrapper, for the client's level wrapper. */
@Mixin(ClientLevelWrapper.class)
public abstract class MixinClientLevelWrapper {
    @WrapOperation(method = "getMinHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"))
    private int cc_windowMinY(ClientLevel level, Operation<Integer> original) {
        return DhWindow.isCubic(level) ? DhWindow.minY() : original.call(level);
    }

    @WrapOperation(method = "getMaxHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeight()I"))
    private int cc_windowHeight(ClientLevel level, Operation<Integer> original) {
        return DhWindow.isCubic(level) ? DhWindow.height() : original.call(level);
    }
}
