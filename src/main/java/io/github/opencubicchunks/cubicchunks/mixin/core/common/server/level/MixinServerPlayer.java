package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddFieldToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddTransformToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.entity.MixinEntity;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.server.level.CCServerPlayer;
import io.github.opencubicchunks.cubicchunks.server.level.CloTrackingView;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Dasm(value = ChunkToCloSet.class, target = @Ref(ServerPlayer.class))
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends MixinEntity implements CCServerPlayer {
    @AddFieldToSets(containers = ChunkToCloSet.ServerPlayer_redirects.class, field = "chunkTrackingView:Lnet/minecraft/server/level/ChunkTrackingView;")
    private CloTrackingView cc_cloTrackingView = CloTrackingView.EMPTY;

    @AddTransformToSets(ChunkToCloSet.ServerPlayer_redirects.class)
    @TransformFromMethod("getChunkTrackingView()Lnet/minecraft/server/level/ChunkTrackingView;")
    public native CloTrackingView cc_getCloTrackingView();

    @AddTransformToSets(ChunkToCloSet.ServerPlayer_redirects.class)
    @TransformFromMethod("setChunkTrackingView(Lnet/minecraft/server/level/ChunkTrackingView;)V")
    public native void cc_setCloTrackingView(CloTrackingView chunkTrackingView);

    // TODO P3 :: findDimensionEntryPoint

    // TODO P3 :: changeDimension

    // FIXME (P2) teleportation code needs CC changes

    /**
     * The player's client says its render distance changed (as its options screen closes): in a cubic level their reach in cubes follows at
     * once (vanilla's in columns waits until they move into another chunk; for cubes the old reach held until they moved into another cube).
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "updateOptions", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void cc_cubeReachFollowsRenderDistance(net.minecraft.server.level.ClientInformation information,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        net.minecraft.server.level.ServerPlayer self = (net.minecraft.server.level.ServerPlayer) (Object) this;
        if (self.connection != null && ((io.github.opencubicchunks.cubicchunks.CanBeCubic) self.level()).cc_isCubic()) {
            ((io.github.opencubicchunks.cubicchunks.server.level.CubicChunkMap) self.level().getChunkSource().chunkMap).cc_updateCubeTracking(self);
        }
    }
}
