package io.github.opencubicchunks.cubicchunks.world.level;

import java.util.Optional;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeStorage;

/**
 * Vanilla's section storage (the points of interest: beds, job sites, bells, portals) in a cubic level: loaded and saved cube by cube in a
 * cube store of its own instead of column by column over the dimension's height (see MixinSectionStorage).
 */
public interface CubicSectionStorage {
    /** Makes the storage cubic, kept in this store; done as the level's chunk map is made. */
    void cc_setCubeStorage(CubeStorage storage);

    boolean cc_isCubic();

    /** Writes the cube's sections if they changed (as vanilla's flush does for a chunk when it is saved). */
    void cc_flushCube(CubePos cubePos);

    /** The section's data, loading its cube's from disk if need be (vanilla's getOrLoad). */
    Optional<?> cc_getOrLoad(long sectionPos);

    /**
     * Reads a cube's sections from disk off the server thread and takes them in on it (the executor), as vanilla's prefetch does for a chunk
     * as it loads; done at once if they are in already. The server thread then finds them without waiting for the disk.
     */
    java.util.concurrent.CompletableFuture<?> cc_prefetchCube(CubePos cubePos, java.util.concurrent.Executor serverThread);
}
