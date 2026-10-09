package io.github.opencubicchunks.cubicchunks.world.level;

import java.util.function.Supplier;

/**
 * Whether reading a block of a cubic server level may load (or generate) its cube. It may not, as a rule: vanilla's block reads load the
 * chunk they fall in and wait for it, and a cube a player falls or flies into faster than cubes load was generated there and then on the
 * server thread, which stopped ticking until it was done (an Orbis Terrarum cube can wait for map data: the watchdog ended the server). A
 * cube that is not loaded reads as void air, as Cubic Chunks for 1.12 had it. The searches that must look into cubes not loaded yet (the
 * world spawn, a player's respawn point) do so inside {@link #allowing}.
 */
public final class CubeLoads {
    private static final ThreadLocal<int[]> ALLOWED = ThreadLocal.withInitial(() -> new int[1]);

    private CubeLoads() {
    }

    public static boolean allowed() {
        return ALLOWED.get()[0] > 0;
    }

    /** Runs the action with block reads loading the cubes they need, on this thread. */
    public static <T> T allowing(Supplier<T> action) {
        int[] depth = ALLOWED.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }
}
