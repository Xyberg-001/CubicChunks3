package io.github.opencubicchunks.cubicchunks.world.level.cube;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import net.minecraft.core.SectionPos;

/** Where a cube's sections are. */
public final class CubeSections {
    private CubeSections() {}

    /** The section at index i of a cube's sections, as Coords.sectionToIndex numbers them (Coords.indexToSection* are not its inverse). */
    public static SectionPos sectionPosOf(CubePos cube, int i) {
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dy = 0; dy < CubicConstants.DIAMETER_IN_SECTIONS; dy++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    if (Coords.sectionToIndex(dx, dy, dz) == i) {
                        return SectionPos.of(Coords.cubeToSection(cube.getX(), dx), Coords.cubeToSection(cube.getY(), dy), Coords.cubeToSection(cube.getZ(), dz));
                    }
                }
            }
        }
        throw new IllegalArgumentException("No section " + i + " in a cube");
    }
}
