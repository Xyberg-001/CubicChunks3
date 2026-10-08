package io.github.opencubicchunks.cubicchunks.world.level.cube;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface CubeSource {
    @Nullable CubeAccess cc_getCube(int x, int y, int z, ChunkStatus status, boolean forceLoad);

    @Nullable LevelCube cc_getCube(int x, int y, int z, boolean forceLoad);

    @Nullable LevelCube cc_getCubeNow(int x, int y, int z);

    // TODO: Phase 2 - getCubeForLighting

    boolean cc_hasCube(int x, int y, int z);

    int cc_getLoadedCubeCount();

    /** A block in a cube changed how it passes or gives light (lights it on the client; the server has no cube light yet). */
    default void cc_onCubeLightPropertiesChanged(BlockPos pos) {
    }

    boolean cc_updateCubeForced(CubePos cubePos, boolean forced);
}
