package io.github.opencubicchunks.cubicchunks.mixin.dh.common;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.github.opencubicchunks.cubicchunks.compat.dh.DhWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distant Horizons updates its data from the chunks a level loads and saves; a cubic level's chunks are columns with no blocks (they are in
 * the cubes), which would wipe its terrain. Its data for a cubic level comes from the cubic world generator instead (CubicDhWorldGenerator).
 */
@Mixin(SharedApi.class)
public abstract class MixinSharedApi {
    @Inject(method = "applyChunkUpdate", at = @At("HEAD"), cancellable = true)
    private void cc_ignoreCubicColumns(IChunkWrapper chunk, ILevelWrapper level, boolean waitForLoadedWorld, CallbackInfo ci) {
        if (level != null && DhWindow.isCubic(level.getWrappedMcObject())) {
            ci.cancel();
        }
    }
}
