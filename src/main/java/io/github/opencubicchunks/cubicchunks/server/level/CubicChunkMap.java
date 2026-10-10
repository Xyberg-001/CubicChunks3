package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface CubicChunkMap {
    ChunkGenerationTask cc_scheduleGenerationTask(ChunkStatus chunkStatus, CubePos cubePos);

    void cc_onFullChunkStatusChange(CubePos cubePos, FullChunkStatus fullChunkStatus);

    boolean cc_isChunkTracked(ServerPlayer player, int x, int y, int z);

    /** Queues the cube to be saved soon (vanilla's setChunkUnsaved, for cubes). */
    void cc_markCubeUnsaved(CubePos cubePos);

    /** The cube's saved data, or empty if it was never saved (read on the cube storage's thread, after any pending writes). */
    CompletableFuture<Optional<CompoundTag>> cc_readSavedCube(CubePos cubePos);

    /** The server thread's executor (where holders are looked up and generation tasks scheduled). */
    java.util.concurrent.Executor cc_mainThreadExecutor();
}
