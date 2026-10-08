package io.github.opencubicchunks.cubicchunks.server.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ImposterProtoCube;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface GenerationCloHolder {
    CloPos cc_getCloPos();

    @Nullable CubePos cc_getCubePos();

    void cc_replaceProtoCube(ImposterProtoCube cube);

    /** The cube or column at its latest status (vanilla's getLatestChunk casts to a chunk, so it cannot hold a cube). */
    @Nullable CloAccess cc_getLatestClo();

    /** The cube once it has reached a status (null for a column holder, or before then). */
    @Nullable CubeAccess cc_getCubeIfPresentUnchecked(ChunkStatus status);
}
