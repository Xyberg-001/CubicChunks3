package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.server.level.CubicThreadedLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** See {@link CubicThreadedLightEngine}: the "now" calls are the LevelLightEngine methods vanilla's own light-thread tasks call. */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class MixinThreadedLevelLightEngine extends LevelLightEngine implements CubicThreadedLightEngine {
    protected MixinThreadedLevelLightEngine(LightChunkGetter chunkSource, boolean hasBlockLight, boolean hasSkyLight) {
        super(chunkSource, hasBlockLight, hasSkyLight);
    }

    @Shadow private void addTask(int chunkX, int chunkZ, ThreadedLevelLightEngine.TaskType type, Runnable runnable) {
        throw new AssertionError();
    }

    @Override public void cc_addTask(int chunkX, int chunkZ, ThreadedLevelLightEngine.TaskType type, Runnable task) {
        this.addTask(chunkX, chunkZ, type, task);
    }

    @Override public void cc_updateSectionStatusNow(SectionPos pos, boolean empty) {
        super.updateSectionStatus(pos, empty);
    }

    @Override public void cc_propagateLightSourcesNow(ChunkPos pos) {
        super.propagateLightSources(pos);
    }

    @Override public void cc_checkBlockNow(BlockPos pos) {
        super.checkBlock(pos);
    }

    @Override public void cc_queueSectionDataNow(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
        super.queueSectionData(layer, pos, data);
    }
}
