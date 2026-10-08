package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.server.level.CubicThreadedLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * The light engine calls {@link CubicLight} makes. On the client they go straight to the level's engine; the server's threaded engine either
 * queues them for its light thread or, from a task already running there, applies them at once (see MixinThreadedLevelLightEngine).
 */
public interface CubeLightEngine {
    void updateSectionStatus(SectionPos pos, boolean empty);

    void propagateLightSources(ChunkPos pos);

    void checkBlock(BlockPos pos);

    void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data);

    void setLightEnabled(ChunkPos pos, boolean enabled);

    void retainData(ChunkPos pos, boolean retain);

    /**
     * The light work already queued in the engine, done now (near: a chunk where it is needed, for the server's task queue). Before a cube's
     * sections leave: the engine drops sections before it spreads light, and light queued from them (a cube's sky sources, when it arrived
     * in the same batch) would then point at sections it no longer has.
     */
    void runPendingUpdates(ChunkPos near);

    /** See {@link SkySourceRemoval}: only where the engine's updates run, so not for queued calls. */
    void removeSkySourcesBelow(int x, int z, int startY);

    /** {@link SkySourceRemoval} on an engine, for a side whose engine calls are applied at once on another path. */
    static void removeSkySourcesBelowNow(LevelLightEngine engine, int x, int z, int startY) {
        SkySourceRemoval.removeBelow(engine, x, z, startY);
    }

    /** See {@link SkySourceRemoval#removeWithin}: only where the engine's updates run, so not for queued calls. */
    void removeSkyLightWithin(int x, int z, int topY, int bottomY);

    static void removeSkyLightWithinNow(LevelLightEngine engine, int x, int z, int topY, int bottomY) {
        SkySourceRemoval.removeWithin(engine, x, z, topY, bottomY);
    }

    static CubeLightEngine of(LevelLightEngine engine) {
        return new CubeLightEngine() {
            @Override public void updateSectionStatus(SectionPos pos, boolean empty) {
                engine.updateSectionStatus(pos, empty);
            }

            @Override public void propagateLightSources(ChunkPos pos) {
                engine.propagateLightSources(pos);
            }

            @Override public void checkBlock(BlockPos pos) {
                engine.checkBlock(pos);
            }

            @Override public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
                engine.queueSectionData(layer, pos, data);
            }

            @Override public void setLightEnabled(ChunkPos pos, boolean enabled) {
                engine.setLightEnabled(pos, enabled);
            }

            @Override public void retainData(ChunkPos pos, boolean retain) {
                engine.retainData(pos, retain);
            }

            @Override public void runPendingUpdates(ChunkPos near) {
                if (engine instanceof CubicThreadedLightEngine threaded) {
                    // its own runLightUpdates is not to be called: the light thread runs it, so ask for it there
                    threaded.cc_addTask(near.x(), near.z(), ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
                            threaded::cc_runLightUpdatesNow);
                } else {
                    engine.runLightUpdates();
                }
            }

            @Override public void removeSkySourcesBelow(int x, int z, int startY) {
                SkySourceRemoval.removeBelow(engine, x, z, startY);
            }

            @Override public void removeSkyLightWithin(int x, int z, int topY, int bottomY) {
                SkySourceRemoval.removeWithin(engine, x, z, topY, bottomY);
            }
        };
    }
}
