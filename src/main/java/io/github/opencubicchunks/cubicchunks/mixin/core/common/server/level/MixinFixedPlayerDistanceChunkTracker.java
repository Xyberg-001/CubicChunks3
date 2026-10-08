package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cc_core.utils.Coords;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.FixedPlayerDistanceChunkTracker.class)
public abstract class MixinFixedPlayerDistanceChunkTracker extends MixinChunkTracker {
    @Shadow @Final protected int maxDistance;
    @Shadow @Final protected Long2ByteMap chunks;

    /**
     * How far from a player this tracker reaches, in a cubic level: its distance counted in cubes, which are two chunks wide, so it covers
     * what vanilla's covers in chunks (the spawning counter's 8 chunks are 4 cubes, 128 blocks). Counted in cubes as given, it reached twice
     * as far in every direction, up and down too: eight times the cubes, and the player ticket tracker's 32 levels made a cube of 65 cubes
     * a side worked through on every teleport.
     */
    @Override protected int cc_maxCubeLevel() {
        return cc_isCubic ? Coords.sectionToCubeCeil(this.maxDistance) : super.cc_maxCubeLevel();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void cc_onSetLevel(long sectionPos, int level, CallbackInfo ci) {
        super.cc_onSetLevel(sectionPos, level);
    }
}
