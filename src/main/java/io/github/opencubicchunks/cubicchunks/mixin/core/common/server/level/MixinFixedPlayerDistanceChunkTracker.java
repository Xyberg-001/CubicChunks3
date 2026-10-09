package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    // ------------------------------------------------------------------ levels worked out directly
    //
    // These trackers hold each position's distance to the nearest player: a cube's is its distance in cubes to the nearest player's cube
    // (the larger of the three axes, as propagating through the 26 neighbours makes it), none past cc_maxCubeLevel; a column's is that of
    // the cubes above and below it (its cube column's distance across), and outside them one more for every chunk farther out, up to
    // maxDistance. Propagated, a player crossing into the next cube raised and lowered every cube within the view distance, each asking all
    // 26 neighbours about all 26 of theirs: a third of the server thread as a player flew. Players are few, so each position's level is
    // worked out from their cubes instead, over the boxes around the cubes players left and entered, and set through setLevel as before
    // (so the ticket tracker hears every change as it did).

    /** The cubes players are in (the sources). */
    @Unique private final LongSet cc_playerCubes = new LongOpenHashSet();
    /** Cubes players entered or left since the levels were last worked out. */
    @Unique private final LongSet cc_changedCubes = new LongOpenHashSet();
    /** The cube reach the levels were last worked out with (the old boxes reach as far, when it shrinks). */
    @Unique private int cc_lastReach = 0;

    @Override protected boolean cc_directUpdate(long node, int level) {
        if (!CloPos.isCube(node)) {
            return false;
        }
        if (level == 0) {
            this.cc_playerCubes.add(node);
        } else {
            this.cc_playerCubes.remove(node);
        }
        this.cc_changedCubes.add(node);
        return true;
    }

    /** Has every player's box worked out again (the reach changed). */
    @Unique protected void cc_reachChanged() {
        this.cc_changedCubes.addAll(this.cc_playerCubes);
    }

    @Inject(method = "runAllUpdates", at = @At("HEAD"))
    private void cc_runDirectUpdates(CallbackInfo ci) {
        if (!this.cc_isCubic || this.cc_changedCubes.isEmpty()) {
            return;
        }
        int reach = this.cc_maxCubeLevel();
        int boxReach = Math.max(reach, this.cc_lastReach);
        this.cc_lastReach = reach;
        long[] players = this.cc_playerCubes.toLongArray();
        int count = players.length;
        int[] px = new int[count];
        int[] py = new int[count];
        int[] pz = new int[count];
        for (int i = 0; i < count; i++) {
            px[i] = CloPos.extractX(players[i]);
            py[i] = CloPos.extractY(players[i]);
            pz[i] = CloPos.extractZ(players[i]);
        }
        LongSet done = new LongOpenHashSet();
        int outside = this.maxDistance; // chunk steps a column's level goes on past the cubes
        int perCube = CubicConstants.DIAMETER_IN_SECTIONS;
        for (long changed : this.cc_changedCubes.toLongArray()) {
            int cx = CloPos.extractX(changed);
            int cy = CloPos.extractY(changed);
            int cz = CloPos.extractZ(changed);
            for (int x = cx - boxReach; x <= cx + boxReach; x++) {
                for (int z = cz - boxReach; z <= cz + boxReach; z++) {
                    for (int y = cy - boxReach; y <= cy + boxReach; y++) {
                        long cube = CloPos.cubeAsLong(x, y, z);
                        if (done.add(cube)) {
                            int level = Integer.MAX_VALUE;
                            for (int i = 0; i < count; i++) {
                                level = Math.min(level, Math.max(Math.abs(x - px[i]), Math.max(Math.abs(y - py[i]), Math.abs(z - pz[i]))));
                            }
                            this.cc_applyLevel(cube, level > reach ? Integer.MAX_VALUE : level);
                        }
                    }
                }
            }
            int minChunkX = Coords.cubeToSection(cx - boxReach, 0) - outside;
            int maxChunkX = Coords.cubeToSection(cx + boxReach, perCube - 1) + outside;
            int minChunkZ = Coords.cubeToSection(cz - boxReach, 0) - outside;
            int maxChunkZ = Coords.cubeToSection(cz + boxReach, perCube - 1) + outside;
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    long column = CloPos.chunkAsLong(x, z);
                    if (done.add(column)) {
                        this.cc_applyLevel(column, cc_columnLevel(x, z, reach, px, pz, count));
                    }
                }
            }
        }
        this.cc_changedCubes.clear();
    }

    /** A column's level: its cube column's distance across to a player's cube within the reach, else the reach plus its chunks beyond. */
    @Unique private static int cc_columnLevel(int chunkX, int chunkZ, int reach, int[] px, int[] pz, int count) {
        int cubeX = Coords.sectionToCube(chunkX);
        int cubeZ = Coords.sectionToCube(chunkZ);
        int perCube = CubicConstants.DIAMETER_IN_SECTIONS;
        int level = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int across = Math.max(Math.abs(cubeX - px[i]), Math.abs(cubeZ - pz[i]));
            if (across <= reach) {
                level = Math.min(level, across);
            } else {
                int beyondX = Math.max(0, Math.max(Coords.cubeToSection(px[i] - reach, 0) - chunkX, chunkX - Coords.cubeToSection(px[i] + reach, perCube - 1)));
                int beyondZ = Math.max(0, Math.max(Coords.cubeToSection(pz[i] - reach, 0) - chunkZ, chunkZ - Coords.cubeToSection(pz[i] + reach, perCube - 1)));
                level = Math.min(level, reach + Math.max(beyondX, beyondZ));
            }
        }
        return level;
    }

    /** Sets a position's level if it changed (past maxDistance: none). */
    @Unique private void cc_applyLevel(long node, int level) {
        int old = this.chunks.get(node);
        boolean had = old <= this.maxDistance;
        boolean has = level <= this.maxDistance;
        if (has ? old != level : had) {
            this.setLevel(node, has ? level : this.levelCount - 1);
        }
    }
}
