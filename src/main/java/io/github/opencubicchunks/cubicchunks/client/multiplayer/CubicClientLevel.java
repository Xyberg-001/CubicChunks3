package io.github.opencubicchunks.cubicchunks.client.multiplayer;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.client.render.CubeRenderReadiness;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;

public interface CubicClientLevel extends CubicLevel {
    void cc_onCubeLoaded(CubePos cubePos);

    /** The client dropped a cube (vanilla's ClientLevel.unload, for a chunk). */
    void cc_onCubeUnloaded(LevelCube cube);

    /** Which held cubes a section-meshing renderer can build (only with Sodium installed, see SodiumCubes), or null. */
    @Nullable CubeRenderReadiness cc_renderReadiness();
}
