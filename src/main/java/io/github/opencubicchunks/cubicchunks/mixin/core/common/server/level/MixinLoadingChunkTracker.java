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

    /**
     * Each position's level as this tracker last set it, if that loads it (else none: vanilla's {@code MAX_LEVEL}). Vanilla reads a position's
     * level from its holder, unless the holder is about to be dropped: a lookup in the set of holders to drop and one in the holder map (tens
     * of thousands of cubes and columns) for each of the 26 neighbours of every cube the tracker updates, a third of its time while a player
     * flew. A holder's level and whether it is dropped change only as this tracker sets a level (ChunkMap.updateChunkScheduling), so the
     * level set is the level read back.
     */
    @org.spongepowered.asm.mixin.Unique
    private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap cc_levels = cc_newLevels();

    @org.spongepowered.asm.mixin.Unique
    private static it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap cc_newLevels() {
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap levels = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
        levels.defaultReturnValue((byte) (net.minecraft.server.level.ChunkLevel.MAX_LEVEL + 1));
        return levels;
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void cc_rememberLevel(long node, int level, CallbackInfo ci) {
        boolean loaded = CloPos.isCube(node) ? CubeLevel.isLoadedCube(level) : net.minecraft.server.level.ChunkLevel.isLoaded(level);
        if (loaded) {
            this.cc_levels.put(node, (byte) level);
        } else {
            this.cc_levels.remove(node);
        }
    }

    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true)
    private void cc_rememberedLevel(long node, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if (this.cc_isCubic) {
            cir.setReturnValue((int) this.cc_levels.get(node));
        }
    }
}
