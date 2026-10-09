package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link ChunkTracker} is a class that determines the edges and as well as the values to be propagated to the edges in
 * {@link DynamicGraphMinFixedPoint}.
 * It marks all chunks in a 1 chunk radius around the center as edges. Edge chunks have 1 level higher than the center chunk.
 * <br>
 * <br>
 * This mixin replaces {@link ChunkPos} with {@link CloPos} and adds in logic to handle propagation for cubes.
 */
@Mixin(ChunkTracker.class)
public abstract class MixinChunkTracker extends DynamicGraphMinFixedPoint implements MarkableAsCubic {
    protected boolean cc_isCubic;
    private int cc_noChunkLevel;

    /**
     * This is a list of all loaded cubes grouped by "big" column positions (32x32 columns) since cubes are 32x32x32.
     * It is needed for quick lookups to determine which cubes are next to a column when propagating level.
     */
    private Long2ObjectMap<IntSet> cc_existingCubesForCubeColumns;

    protected MixinChunkTracker() {
        super(0, 0, 0);
    }

    @Override public void cc_setCubic() {
        cc_isCubic = true;
        cc_existingCubesForCubeColumns = new Long2ObjectLinkedOpenHashMap<>();
        cc_noChunkLevel = levelCount - 1;
    }

    @Override public boolean cc_isCubic() {
        return cc_isCubic;
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/ChunkPos;INVALID_CHUNK_POS:J"))
    private long cc_sentinelValue() {
        if (cc_isCubic) {
            return CloPos.INVALID_CLO_POS;
        }
        return ChunkPos.INVALID_CHUNK_POS;
    }

    /**
     * The highest level a cube can hold in this tracker; above it a cube counts as having none ({@code levelCount - 1}). Every level by
     * default; the loading tracker lowers it to the cube load limit (see {@link MixinLoadingChunkTracker}).
     */
    protected int cc_maxCubeLevel() {
        return levelCount - 1;
    }

    /** The lowest level this cube can hold in this tracker (none by default; see {@link MixinLoadingChunkTracker}). */
    protected int cc_cubeLevelFloor(long cube) {
        return 0;
    }

    @Shadow protected abstract int getLevelFromSource(long to);

    /**
     * A ticket's level reaches its position without {@link #computeLevelFromNeighbor} (vanilla's update hands it straight on), so the cube
     * floor and cap apply here too: without it a player beyond a level's heights put the cubes there at their ticket level, and the edge
     * cubes' generation then wanted neighbours the floor had left without a holder.
     */
    /**
     * A tracker that works its cubes' levels out itself rather than by propagation (see MixinFixedPlayerDistanceChunkTracker) takes the
     * source change here: true when it did.
     */
    protected boolean cc_directUpdate(long node, int level) {
        return false;
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void cc_onUpdate(long node, int newLevelFrom, boolean onlyDecreased, CallbackInfo ci) {
        if (this.cc_isCubic && this.cc_directUpdate(node, newLevelFrom)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true)
    private int cc_capSourceLevel(int newLevelFrom, @Local(argsOnly = true) long node) {
        if (!cc_isCubic || !CloPos.isCube(node)) {
            return newLevelFrom;
        }
        int level = Math.max(newLevelFrom, this.cc_cubeLevelFloor(node));
        return level > cc_maxCubeLevel() ? levelCount - 1 : level;
    }

    /**
     * Vanilla's rule (a neighbour's level plus one; the source's own level from {@link #getLevelFromSource}), and in a cubic level:
     * <ul>
     * <li>a cube passes its level on to its columns unchanged;</li>
     * <li>a cube never gets a level below {@link #cc_cubeLevelFloor} (cubes beyond the level's heights);</li>
     * <li>a cube never gets a level above {@link #cc_maxCubeLevel}. In the loading tracker cubes load only up to the cube load limit, below
     * the column one, and a cube's level there is read back from its holder: a cube given a level between the two had none (so read back as
     * unloaded) yet held up the columns beneath it, and when its ticket went the tracker saw no change, so those columns never unloaded.</li>
     * </ul>
     * One method in place of vanilla's rather than injections into it: the trackers call it for every neighbour of every position they
     * update, and two injected handlers there (each making its callback object) took a sixth of the server thread after a teleport.
     *
     * @author Xyberg-001
     * @reason see above
     */
    @Overwrite
    protected int computeLevelFromNeighbor(long from, long to, int fromLevel) {
        if (!cc_isCubic) {
            return from == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(to) : fromLevel + 1;
        }
        int level = from == CloPos.INVALID_CLO_POS ? this.getLevelFromSource(to) : fromLevel + (CloPos.isCube(from) && CloPos.isChunk(to) ? 0 : 1);
        if (!CloPos.isCube(to)) {
            return level;
        }
        level = Math.max(level, this.cc_cubeLevelFloor(to));
        return level > cc_maxCubeLevel() ? levelCount - 1 : level;
    }

    @Inject(method = "checkNeighborsAfterUpdate", at = @At("HEAD"), cancellable = true)
    private void cc_onCheckNeighborsAfterUpdate(long pos, int level, boolean isDecreasing, CallbackInfo ci) {
        if (cc_isCubic) {
            ci.cancel();
            // as vanilla's: a position at the last level passes nothing on when levels only fall
            if (!isDecreasing || level < this.levelCount - 2) {
                CloPos.forEachNeighbor(pos, n -> this.checkNeighbor(pos, n, level, isDecreasing));
            }
        }
    }

    /**
     * This computes the propagation of the level from neighbor to neighbor.
     * <br>
     * <br>
     * We have to handle two cases: whether we are propagating to a column or a cube.
     * <br>
     * <br>
     * However, it is important to note that you cannot propagate from a column to a cube. This is because
     * if we did do that, then a single column could potentially load an infinite amount of cubes. So only
     * cubes can propagate to a column, or cubes propagating to cubes.
     */
    @SuppressWarnings({ "checkstyle:CyclomaticComplexity", "checkstyle:JavaNCSS" }) // <-- TODO can this be improved?
    @Inject(method = "getComputedLevel", at = @At("HEAD"), cancellable = true)
    private void cc_onGetComputedLevel(long pos, long excludedSourcePos, int level, CallbackInfoReturnable<Integer> cir) {
        if (!cc_isCubic) {
            return;
        }
        if (CloPos.isChunk(pos)) {
            int out = level;

            int x = CloPos.extractX(pos);
            int z = CloPos.extractZ(pos);
            for (int x2 = -1; x2 <= 1; ++x2) {
                for (int z2 = -1; z2 <= 1; ++z2) {
                    long neighbor = CloPos.chunkAsLong(x + x2, z + z2);
                    if (neighbor == pos) {
                        neighbor = CloPos.INVALID_CLO_POS;
                    }

                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            cir.setReturnValue(out);
                            return;
                        }
                    }
                }
            }
            // This is propagating the level from neighboring cubes to this column.
            long cubeColumnKey = CloPos.cubeAsLong(Coords.sectionToCube(x), 0, Coords.sectionToCube(z));
            IntSet neighborCubeYSet = this.cc_existingCubesForCubeColumns.get(cubeColumnKey);
            if (neighborCubeYSet != null) {
                for (Integer cubeY : neighborCubeYSet) {
                    long neighbor = CloPos.setY(cubeColumnKey, cubeY);
                    assert neighbor != pos;
                    if (neighbor != excludedSourcePos) {
                        int k1 = this.computeLevelFromNeighbor(neighbor, pos, this.getLevel(neighbor));
                        if (out > k1) {
                            out = k1;
                        }

                        if (out == 0) {
                            cir.setReturnValue(out);
                            return;
                        }
                    }
                }
            }
            cir.setReturnValue(out);
        } else {
            // computeLevelFromNeighbor for each neighbour, with the floor and cap applied once (they depend on this cube alone).
            // The search stops at a neighbour that keeps the cube at the level it has, if the cube has no level queued: then no
            // neighbour holds it lower (one that went lower queued the cube's new level as it did), so that is the answer. The tracker
            // asks this for every neighbour of a cube whose level rises; without the stop a player's tickets going cost 26 x 26 lookups
            // a cube, a quarter of a minute of server thread as a player left.
            int out = level;
            int x = CloPos.extractX(pos);
            int y = CloPos.extractY(pos);
            int z = CloPos.extractZ(pos);
            // only for a cube with no level queued: a queued one may be on its way down, and must hear every neighbour
            int current = ((io.github.opencubicchunks.cubicchunks.mixin.access.common.DynamicGraphMinFixedPointAccess) this).cc_computedLevels().containsKey(pos)
                    ? -1 : this.getLevel(pos);
            if (excludedSourcePos != CloPos.INVALID_CLO_POS) {
                out = Math.min(out, this.getLevelFromSource(pos));
            }
            search:
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (out == 0 || out == current) {
                            break search;
                        }
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        long neighbor = CloPos.cubeAsLong(x + dx, y + dy, z + dz);
                        if (neighbor != excludedSourcePos) {
                            int fromNeighbor = this.getLevel(neighbor) + 1;
                            if (out > fromNeighbor) {
                                out = fromNeighbor;
                            }
                        }
                    }
                }
            }
            out = Math.max(out, this.cc_cubeLevelFloor(pos));
            cir.setReturnValue(out > cc_maxCubeLevel() ? levelCount - 1 : out);
        }
    }

    /**
     * This function adds in new cubes and sorts them into the cube column map.
     * <br>
     * <br>
     * It should be called from each implementation of {@link DynamicGraphMinFixedPoint#setLevel(long, int)} on a ChunkTracker subclass.
     * For CC, this is handled by:
     * <ul>
     * <li>{@link MixinFixedPlayerDistanceChunkTracker#cc_onSetLevel(long, int)}.</li>
     * <li>{@link MixinLoadingChunkTracker#cc_onSetLevel(long, int)}.</li>
     * <li>{@link MixinSimulationChunkTracker#cc_onSetLevel(long, int)}.</li>
     * </ul>
     */
    protected void cc_onSetLevel(long pos, int level) {
        if (cc_isCubic && CloPos.isCube(pos)) {
            long key = CloPos.setY(pos, 0);
            IntSet cubes = cc_existingCubesForCubeColumns.get(key);
            if (level >= cc_noChunkLevel) {
                if (cubes != null) {
                    cubes.remove(CloPos.extractY(pos));
                    if (cubes.isEmpty()) {
                        cc_existingCubesForCubeColumns.remove(key);
                    }
                }
            } else {
                if (cubes == null) {
                    cubes = new IntOpenHashSet();
                    cc_existingCubesForCubeColumns.put(key, cubes);
                }
                cubes.add(CloPos.extractY(pos));
            }
        }
    }
}
