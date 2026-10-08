package io.github.opencubicchunks.cubicchunks.client.render;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.CubicClientLevel;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * What the Sodium mixins (mixin/sodium) ask of a cubic level, kept out of them and free of Sodium's classes. Sodium reads a section as
 * {@code level.getChunk(x, z).getSections()[level.getSectionIndexFromSectionY(y)]} and bounds its searches by the level's section range; a
 * cubic client holds no columns and its level keeps the dimension's height, so the mixins hand over the cube's section (as a one-section
 * array read at index 0) and the world's heights instead.
 */
public final class SodiumCubes {
    /** Whether Sodium is installed (the cubic levels then track their cubes for it, see CubeRenderReadiness). */
    public static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

    private SodiumCubes() {
    }

    public static boolean isCubic(Level level) {
        return ((CanBeCubic) level).cc_isCubic();
    }

    /**
     * The cube holding a section, if the client holds it (wherever the view centre is: the readiness tracker counts every held cube, see
     * ClientCubeCache.cc_getHeldCube).
     */
    public static @Nullable LevelCube cubeOfSection(Level level, int sectionX, int sectionY, int sectionZ) {
        return ((ClientCubeCache) level.getChunkSource()).cc_getHeldCube(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY),
                Coords.sectionToCube(sectionZ));
    }

    /** The section as a one-element array (null in it if its cube is not held), for Sodium to read at index 0. */
    public static LevelChunkSection[] sectionArray(Level level, int sectionX, int sectionY, int sectionZ) {
        LevelCube cube = cubeOfSection(level, sectionX, sectionY, sectionZ);
        LevelChunkSection section = cube == null ? null : cube.getSections()[Coords.sectionToIndex(Coords.cubeLocalSection(sectionX),
                Coords.cubeLocalSection(sectionY), Coords.cubeLocalSection(sectionZ))];
        return new LevelChunkSection[] { section };
    }

    public static int minSectionY(Level level) {
        return Coords.blockToSection(CubicHeight.minY(level));
    }

    public static int maxSectionY(Level level) {
        return Coords.blockToSection(CubicHeight.maxY(level));
    }

    /** The level's readiness tracker (only cubic client levels have one, and only with Sodium installed). */
    public static @Nullable CubeRenderReadiness readiness(Level level) {
        return level instanceof CubicClientLevel cubic ? cubic.cc_renderReadiness() : null;
    }

    /** Calls the handler for each section of a cube. */
    public static void forEachSection(int cubeX, int cubeY, int cubeZ, CubeRenderReadiness.CubeHandler handler) {
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dy = 0; dy < CubicConstants.DIAMETER_IN_SECTIONS; dy++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    handler.accept(Coords.cubeToSection(cubeX, dx), Coords.cubeToSection(cubeY, dy), Coords.cubeToSection(cubeZ, dz));
                }
            }
        }
    }
}
