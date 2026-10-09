package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Tells listeners of each block change in a cubic server level's loaded cubes (ServerChunkCache.blockChanged, on the server thread), for
 * mods that keep their own copy of the terrain (Distant Horizons, see compat/dh).
 */
public final class CubeBlockChanges {
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private CubeBlockChanges() {
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void blockChanged(ServerLevel level, BlockPos pos) {
        for (Listener listener : LISTENERS) {
            listener.blockChanged(level, pos);
        }
    }

    @FunctionalInterface
    public interface Listener {
        void blockChanged(ServerLevel level, BlockPos pos);
    }
}
