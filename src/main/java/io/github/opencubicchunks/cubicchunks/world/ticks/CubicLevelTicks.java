package io.github.opencubicchunks.cubicchunks.world.ticks;

import java.util.function.LongPredicate;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.ticks.LevelChunkTicks;

/**
 * A level's scheduled block or fluid ticks (LevelTicks), cube by cube in a cubic level: each cube's tick container is kept under its cube,
 * a tick goes to the cube its position is in, and a cube's ticks run while the cube ticks. Columns hold no blocks there, so they keep none.
 */
public interface CubicLevelTicks<T> {
    /** @param cubeTickCheck whether the cube at a CubePos key runs its ticks (vanilla asks it of a chunk key) */
    void cc_makeCubic(LongPredicate cubeTickCheck);

    void cc_addContainer(CubePos pos, LevelChunkTicks<T> container);

    void cc_removeContainer(CubePos pos);
}
