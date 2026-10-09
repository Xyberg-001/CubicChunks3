package io.github.opencubicchunks.cubicchunks.mixin.dh.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import io.github.opencubicchunks.cubicchunks.compat.dh.DhWindow;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Distant Horizons takes a level's height from its wrapper; a cubic level's is the window it holds (see DhWindow). */
@Mixin(ServerLevelWrapper.class)
public abstract class MixinServerLevelWrapper {
    @WrapOperation(method = "getMinHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinY()I"))
    private int cc_windowMinY(ServerLevel level, Operation<Integer> original) {
        return DhWindow.isCubic(level) ? DhWindow.minY() : original.call(level);
    }

    @WrapOperation(method = "getMaxHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getHeight()I"))
    private int cc_windowHeight(ServerLevel level, Operation<Integer> original) {
        return DhWindow.isCubic(level) ? DhWindow.height() : original.call(level);
    }
}
