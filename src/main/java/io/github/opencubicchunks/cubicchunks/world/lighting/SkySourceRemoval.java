package io.github.opencubicchunks.cubicchunks.world.lighting;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.LayerLightSectionStorageAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LevelLightEngineAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LightEngineAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.SkyLightSectionStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * Takes back sky light from a column below a given height, for when a cube arrives over cubes lit as open to the sky. Vanilla's own
 * removal (SkyLightEngine.removeSourcesBelow) walks down from the new top of the sky sources and stops at the first block not at full sky
 * light; under a cube that has just arrived, the first blocks are the cube's own (stored as 0), so it would stop before reaching the light
 * below. This walk starts under the cube instead, and otherwise does the same: full sky light is set to 0 and decreased from, down to the
 * first block without it.
 * <p>
 * Changes the engine's storage directly: only from where its updates run (the client thread, or a task of the server's light thread).
 */
final class SkySourceRemoval {
    private static final long REMOVE_TOP = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);

    private SkySourceRemoval() {}

    static void removeBelow(LevelLightEngine engine, int x, int z, int startY) {
        LightEngine<?, ?> sky = ((LevelLightEngineAccess) engine).cc_skyEngine();
        if (sky == null) {
            return;
        }
        Object storage = ((LightEngineAccess) sky).cc_storage();
        LayerLightSectionStorageAccess sections = (LayerLightSectionStorageAccess) storage;
        SkyLightSectionStorageAccess skySections = (SkyLightSectionStorageAccess) storage;
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        for (int sectionY = SectionPos.blockToSectionCoord(startY); skySections.cc_hasLightDataAtOrBelow(sectionY); sectionY--) {
            if (!sections.cc_storingLightForSection(SectionPos.asLong(sectionX, sectionY, sectionZ))) {
                continue;
            }
            int sectionBottomY = SectionPos.sectionToBlockCoord(sectionY);
            for (int y = Math.min(sectionBottomY + 15, startY); y >= sectionBottomY; y--) {
                long blockNode = BlockPos.asLong(x, y, z);
                if (sections.cc_getStoredLevel(blockNode) != 15) {
                    return;
                }
                sections.cc_setStoredLevel(blockNode, 0);
                ((LightEngineAccess) sky).cc_enqueueDecrease(blockNode, y == startY ? REMOVE_TOP : REMOVE);
            }
        }
    }

    /**
     * Takes back every full sky light in a column from topY down to bottomY, not only the first run of it: for a stretch of a column below
     * where its sky starts, which vanilla never leaves at 15 (light from beside is 14 at most). A cube lit while it was the top of its column
     * starts out at full sky light throughout, and if it is saved before the cubes above take that back, it comes back with it.
     */
    static void removeWithin(LevelLightEngine engine, int x, int z, int topY, int bottomY) {
        LightEngine<?, ?> sky = ((LevelLightEngineAccess) engine).cc_skyEngine();
        if (sky == null || topY < bottomY) {
            return;
        }
        LayerLightSectionStorageAccess sections = (LayerLightSectionStorageAccess) ((LightEngineAccess) sky).cc_storage();
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        boolean clearedAbove = false;
        for (int sectionY = SectionPos.blockToSectionCoord(topY); sectionY >= SectionPos.blockToSectionCoord(bottomY); sectionY--) {
            if (!sections.cc_storingLightForSection(SectionPos.asLong(sectionX, sectionY, sectionZ))) {
                clearedAbove = false;
                continue;
            }
            int sectionBottomY = SectionPos.sectionToBlockCoord(sectionY);
            for (int y = Math.min(sectionBottomY + 15, topY); y >= Math.max(sectionBottomY, bottomY); y--) {
                long blockNode = BlockPos.asLong(x, y, z);
                if (sections.cc_getStoredLevel(blockNode) != 15) {
                    clearedAbove = false;
                    continue;
                }
                sections.cc_setStoredLevel(blockNode, 0);
                ((LightEngineAccess) sky).cc_enqueueDecrease(blockNode, clearedAbove ? REMOVE : REMOVE_TOP);
                clearedAbove = true;
            }
        }
    }
}
