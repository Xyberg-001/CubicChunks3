package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cubicchunks.server.level.CubeLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.LoadingChunkTracker")
public abstract class MixinLoadingChunkTracker extends MixinChunkTracker {
    /** Cubes load only up to the cube load limit; a level above it would be held by a cube that is not there. */
    @Override protected int cc_maxCubeLevel() {
        return CubeLevel.MAX_LEVEL;
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void cc_onSetLevel(long sectionPos, int level, CallbackInfo ci) {
        super.cc_onSetLevel(sectionPos, level);
    }
}
