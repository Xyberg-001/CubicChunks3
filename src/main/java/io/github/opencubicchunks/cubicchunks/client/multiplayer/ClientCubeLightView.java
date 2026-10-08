package io.github.opencubicchunks.cubicchunks.client.multiplayer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelHeightAccessor;

/** The cubes the client holds, for light (the server sends cubes without light; see CubicLight). */
public final class ClientCubeLightView implements CubeLightView {
    private final ClientLevel level;
    private final ClientCubeCache cubes;

    public ClientCubeLightView(ClientLevel level, ClientCubeCache cubes) {
        this.level = level;
        this.cubes = cubes;
    }

    @Override public @Nullable CubeAccess cube(int cubeX, int cubeY, int cubeZ) {
        return this.cubes.cc_getCube(cubeX, cubeY, cubeZ, false);
    }

    @Override public List<CubeAccess> cubesTopDown(int cubeX, int cubeZ) {
        List<CubeAccess> column = new ArrayList<>();
        int center = this.cubes.cc_cubeViewCenterY();
        int radius = this.cubes.cc_cubeViewRadius();
        for (int cubeY = center + radius; cubeY >= center - radius; cubeY--) {
            CubeAccess cube = this.cube(cubeX, cubeY, cubeZ);
            if (cube != null) {
                column.add(cube);
            }
        }
        return column;
    }

    @Override public LevelHeightAccessor heightAccessor() {
        return this.level;
    }
}
