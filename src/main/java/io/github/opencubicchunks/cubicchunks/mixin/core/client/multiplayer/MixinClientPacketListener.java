package io.github.opencubicchunks.cubicchunks.mixin.core.client.multiplayer;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a cubic level light comes from the cubes, worked out on the client (see CubicClientLight). The server's light for columns covers only the
 * dimension's old height band and knows nothing of the cubes in it, so it is not applied, and a column arriving does not mark that band's
 * sections empty for the light engine.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Shadow private ClientLevel level;

    @Inject(method = "applyLightData", at = @At("HEAD"), cancellable = true)
    private void cc_noColumnLightInCubicLevel(int x, int z, ClientboundLightUpdatePacketData lightData, boolean scheduleRebuild, CallbackInfo ci) {
        if (((CanBeCubic) this.level).cc_isCubic()) {
            ci.cancel();
        }
    }

    @Inject(method = "enableChunkLight", at = @At("HEAD"), cancellable = true)
    private void cc_noColumnSectionsInCubicLevel(LevelChunk chunk, int x, int z, CallbackInfo ci) {
        if (((CanBeCubic) this.level).cc_isCubic()) {
            ci.cancel();
        }
    }
}
