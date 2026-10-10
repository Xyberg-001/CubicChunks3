package io.github.opencubicchunks.cubicchunks.world.level.cube;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface CubeSource {
    @Nullable CubeAccess cc_getCube(int x, int y, int z, ChunkStatus status, boolean forceLoad);

    @Nullable LevelCube cc_getCube(int x, int y, int z, boolean forceLoad);

    @Nullable LevelCube cc_getCubeNow(int x, int y, int z);

    // TODO: Phase 2 - getCubeForLighting

    boolean cc_hasCube(int x, int y, int z);

    int cc_getLoadedCubeCount();

    /** Light for this source's cubes (null unless the level is cubic). */
    default @Nullable CubicLight cc_cubicLight() {
        return null;
    }

    /** A block in a cube changed: the column's surface (its heightmaps) may have. */
    default void cc_onCubeBlockChanged(BlockPos pos) {
        CubicLight light = this.cc_cubicLight();
        if (light != null) {
            light.onBlockChangedForSurface(pos);
        }
    }

    /** A block in a cube changed how it passes or gives light. */
    default void cc_onCubeLightPropertiesChanged(BlockPos pos) {
        CubicLight light = this.cc_cubicLight();
        if (light != null) {
            light.onBlockChanged(pos);
        }
    }

    boolean cc_updateCubeForced(CubePos cubePos, boolean forced);
}
