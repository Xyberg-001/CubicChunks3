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
        };
    }
}
