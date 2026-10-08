package io.github.opencubicchunks.cubicchunks.server.level;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;

/** The cubes a cubic level's heights cover (see CubicWorldSettings), for its distance manager: cubes beyond them hardly load. */
public interface CubeYRange {
    /** The level a cube just beyond the heights is held no lower than, plus one (see MixinLoadingChunkTracker). */
    int EDGE_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    /** How many cubes beyond its heights a level can load: past this a cube's lowest level is above the cube load limit. */
    int CUBES_BEYOND = CubeLevel.MAX_LEVEL - EDGE_LEVEL;

    void cc_setCubeYRange(int minCubeY, int maxCubeY);
}
