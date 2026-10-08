package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.lighting;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLightEngine.class)
public class MixinLevelLightEngine {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    // Vanilla asks whether a whole column is lit; a cubic level lights cube by cube (see world/lighting/CubicLight), so its columns count as lit
    @Inject(method = "lightOnInColumn", at = @At("HEAD"), cancellable = true)
    private void cc_onLightOnInColumn(long columnPos, CallbackInfoReturnable<Boolean> cir) {
        if (((CanBeCubic) this.levelHeightAccessor).cc_isCubic()) {
            cir.setReturnValue(true);
        }
    }
}
