package io.github.opencubicchunks.cubicchunks.world.lighting;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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

            @Override public void removeSkySourcesBelow(int x, int z, int startY) {
                SkySourceRemoval.removeBelow(engine, x, z, startY);
            }

            @Override public void removeSkyLightWithin(int x, int z, int topY, int bottomY) {
                SkySourceRemoval.removeWithin(engine, x, z, topY, bottomY);
            }
        };
    }
}
