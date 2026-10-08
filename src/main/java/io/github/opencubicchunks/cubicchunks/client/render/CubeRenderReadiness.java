package io.github.opencubicchunks.cubicchunks.client.render;

import io.github.opencubicchunks.cc_core.api.CubePos;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Which of a cubic client level's cubes a renderer that meshes whole sections on its own can build: those held with all 26 neighbours
 * (a section's mesh reads the blocks and light around it). It keeps queues of cubes that became ready or stopped being, for the renderer to
 * take each frame. Used for Sodium, whose own tracker does this for chunk columns, which a cubic client does not hold (see the sodium
 * mixins). Client thread only.
 */
public final class CubeRenderReadiness {
    private final LongSet held = new LongOpenHashSet();
    private final LongSet ready = new LongOpenHashSet();
    private final LongSet becameReady = new LongOpenHashSet();
    private final LongSet stoppedBeingReady = new LongOpenHashSet();

    public void onCubeHeld(int x, int y, int z) {
        if (this.held.add(CubePos.asLong(x, y, z))) {
            this.updateAround(x, y, z);
        }
    }

    public void onCubeDropped(int x, int y, int z) {
        if (this.held.remove(CubePos.asLong(x, y, z))) {
            this.updateAround(x, y, z);
        }
    }

    private void updateAround(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.update(x + dx, y + dy, z + dz);
                }
            }
        }
    }

    private void update(int x, int y, int z) {
        long key = CubePos.asLong(x, y, z);
        if (this.isHeldWithNeighbours(x, y, z)) {
            if (this.ready.add(key) && !this.stoppedBeingReady.remove(key)) {
                this.becameReady.add(key);
            }
        } else if (this.ready.remove(key) && !this.becameReady.remove(key)) {
            this.stoppedBeingReady.add(key);
        }
    }

    private boolean isHeldWithNeighbours(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!this.held.contains(CubePos.asLong(x + dx, y + dy, z + dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Hands over the cubes that stopped being ready and then those that became ready since the last call. */
    public void takeChanges(CubeHandler stopped, CubeHandler became) {
        forEach(this.stoppedBeingReady, stopped);
        this.stoppedBeingReady.clear();
        forEach(this.becameReady, became);
        this.becameReady.clear();
    }

    /** Every cube ready now (for a renderer starting over); the pending changes are dropped, as they are part of this. */
    public void forEachReady(CubeHandler handler) {
        this.stoppedBeingReady.clear();
        this.becameReady.clear();
        forEach(this.ready, handler);
    }

    private static void forEach(LongSet cubes, CubeHandler handler) {
        for (LongIterator it = cubes.iterator(); it.hasNext(); ) {
            long cube = it.nextLong();
            handler.accept(CubePos.extractX(cube), CubePos.extractY(cube), CubePos.extractZ(cube));
        }
    }

    @FunctionalInterface
    public interface CubeHandler {
        void accept(int cubeX, int cubeY, int cubeZ);
    }
}
