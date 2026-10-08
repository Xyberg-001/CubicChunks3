package io.github.opencubicchunks.cubicchunks.mixin.core.client.multiplayer;

import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a cubic level columns are not what renders: cubes report their own loading and their sections' emptiness (see MixinClientChunkCache), so
 * a column arriving or leaving must not mark the old height band's sections empty over the cubes' data.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class MixinClientChunkCache$Storage {
    @Shadow @Final ClientChunkCache this$0;

    @Inject(method = { "onChunkAdded", "onChunkRemoved", "refreshEmptySections" }, at = @At("HEAD"), cancellable = true)
    private void cc_columnsDoNotRender(LevelChunk chunk, CallbackInfo ci) {
        if (((ClientCubeCache) this.this$0).cc_isCubic()) {
            ci.cancel();
        }
    }
}
