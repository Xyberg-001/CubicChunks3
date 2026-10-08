package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.server.level.CubeLevel;
import io.github.opencubicchunks.cubicchunks.server.level.CubeYRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.LoadingChunkTracker")
public abstract class MixinLoadingChunkTracker extends MixinChunkTracker implements CubeYRange {
    /** The cubes of the level's heights (every cube until set). */
    private int cc_minCubeY = Integer.MIN_VALUE;
    private int cc_maxCubeY = Integer.MAX_VALUE;

    @Override public void cc_setCubeYRange(int minCubeY, int maxCubeY) {
        this.cc_minCubeY = minCubeY;
        this.cc_maxCubeY = maxCubeY;
    }

    /**
     * The level's heights as a soft wall: a cube d cubes beyond them is held no lower than the entity ticking level plus d. The cubes at the
     * edge get all a cube inside does (entity ticking needs the cubes two away full, block ticking one away), and from there the levels
     * rise as they would going out from the edge, so cubes beyond load only as far as the edge's generation needs and none past
     * {@link CubeLevel#MAX_LEVEL} (CubicWorldSettings keeps that within what a block position holds). Players beyond the heights load
     * nothing more than this.
     */
    @Override protected int cc_cubeLevelFloor(long cube) {
        int y = CloPos.extractY(cube);
        int beyond = y > this.cc_maxCubeY ? y - this.cc_maxCubeY : y < this.cc_minCubeY ? this.cc_minCubeY - y : 0;
        return beyond == 0 ? 0 : CubeYRange.EDGE_LEVEL + beyond;
    }

    /** Cubes load only up to the cube load limit; a level above it would be held by a cube that is not there. */
    @Override protected int cc_maxCubeLevel() {
        return CubeLevel.MAX_LEVEL;
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void cc_onSetLevel(long sectionPos, int level, CallbackInfo ci) {
        super.cc_onSetLevel(sectionPos, level);
    }
}
