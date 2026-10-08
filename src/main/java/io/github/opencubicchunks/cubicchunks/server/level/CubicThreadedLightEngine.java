package io.github.opencubicchunks.cubicchunks.server.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * The server's threaded light engine, opened up for cubes: tasks can be queued for its light thread like vanilla's own, and from such a task
 * the engine's calls can be applied at once (its public methods would only queue them again, for a later batch).
 */
public interface CubicThreadedLightEngine {
    void cc_addTask(int chunkX, int chunkZ, ThreadedLevelLightEngine.TaskType type, Runnable task);

    void cc_updateSectionStatusNow(SectionPos pos, boolean empty);

    void cc_propagateLightSourcesNow(ChunkPos pos);

    void cc_checkBlockNow(BlockPos pos);

    void cc_queueSectionDataNow(LightLayer layer, SectionPos pos, @Nullable DataLayer data);

    void cc_setLightEnabledNow(ChunkPos pos, boolean enabled);

    void cc_retainDataNow(ChunkPos pos, boolean retain);

    void cc_runLightUpdatesNow();

    /** The engine itself, for calls that change its storage directly (only from a task on the light thread). */
    LevelLightEngine cc_engine();

    /** The engine's calls applied at once: only for tasks running on the light thread. */
    default CubeLightEngine cc_onLightThread() {
        return new CubeLightEngine() {
            @Override public void updateSectionStatus(SectionPos pos, boolean empty) {
                cc_updateSectionStatusNow(pos, empty);
            }

            @Override public void propagateLightSources(ChunkPos pos) {
                cc_propagateLightSourcesNow(pos);
            }

            @Override public void checkBlock(BlockPos pos) {
                cc_checkBlockNow(pos);
            }

            @Override public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
                cc_queueSectionDataNow(layer, pos, data);
            }

            @Override public void setLightEnabled(ChunkPos pos, boolean enabled) {
                cc_setLightEnabledNow(pos, enabled);
            }

            @Override public void retainData(ChunkPos pos, boolean retain) {
                cc_retainDataNow(pos, retain);
            }

            @Override public void runPendingUpdates(ChunkPos near) {
                cc_runLightUpdatesNow();
            }

            @Override public void removeSkySourcesBelow(int x, int z, int startY) {
                CubeLightEngine.removeSkySourcesBelowNow(cc_engine(), x, z, startY);
            }

            @Override public void removeSkyLightWithin(int x, int z, int topY, int bottomY) {
                CubeLightEngine.removeSkyLightWithinNow(cc_engine(), x, z, topY, bottomY);
            }
        };
    }
}
