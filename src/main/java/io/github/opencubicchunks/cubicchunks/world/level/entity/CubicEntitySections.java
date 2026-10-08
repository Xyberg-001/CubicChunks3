package io.github.opencubicchunks.cubicchunks.world.level.entity;

import java.util.function.LongFunction;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.entity.Visibility;

/**
 * Entities of a cubic level, cube by cube. Vanilla gives entity sections the visibility of their chunk column (loaded, tracked, ticking),
 * which in a cubic level would load, track and tick every entity of a column at any height while any of its cubes is loaded. Here a
 * section's visibility is the lower of its column's and its cube's: entities whose cube is not loaded stay in memory with their column (and
 * are saved with it, in vanilla's per-column entity storage) but are neither tracked nor ticked, as at the edge of loaded chunks in vanilla.
 */
public final class CubicEntitySections {
    private CubicEntitySections() {}

    /** A PersistentEntitySectionManager that can work cube by cube (see MixinPersistentEntitySectionManager). */
    public interface Manager {
        void cc_makeCubic();

        /** A cube's full status changed (as ChunkMap reports a chunk's to the manager). */
        void cc_updateCubeStatus(CubePos cubePos, Visibility visibility);
    }

    /** An EntitySectionStorage whose new sections take their visibility from a function of the section, not of its column. */
    public interface Storage {
        void cc_setSectionVisibility(LongFunction<Visibility> sectionVisibility);
    }

    /** The lower of two visibilities (they are ordered hidden, tracked, ticking). */
    public static Visibility lower(Visibility a, Visibility b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
