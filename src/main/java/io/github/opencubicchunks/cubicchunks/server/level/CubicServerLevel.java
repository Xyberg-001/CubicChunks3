package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import net.minecraft.server.level.FullChunkStatus;

public interface CubicServerLevel extends CubicLevel {
    @UsedFromASM
    boolean isNaturalSpawningAllowed(CloPos cloPos);

    @UsedFromASM
    void invalidateCapabilities(CloPos cloPos);

    /** A cube's full status changed: its entities are tracked and ticked accordingly (see CubicEntitySections). */
    void cc_onCubeFullStatusChange(CubePos cubePos, FullChunkStatus status);
}
