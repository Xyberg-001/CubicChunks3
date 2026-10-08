package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.List;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * The cubes a side (client or server) can light from. Cubes not given count as air, and above the highest cube of a column is open sky (see
 * {@link CubicSkyLightSources}).
 */
public interface CubeLightView {
    /** The cube at a cube position, if this side holds it ready for light. */
    @Nullable CubeAccess cube(int cubeX, int cubeY, int cubeZ);

    /** The cubes of one cube column that {@link #cube} would give, highest first. */
    List<CubeAccess> cubesTopDown(int cubeX, int cubeZ);

    LevelHeightAccessor heightAccessor();
}
