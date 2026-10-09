package io.github.opencubicchunks.cubicchunks.compat.dh;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.CommonConfig;
import net.minecraft.world.level.Level;

/**
 * The part of a cubic world Distant Horizons holds: its data points keep a height in 12 bits, so it can hold 4096 blocks of height, from the
 * distantHorizonsMinY config up. Its level wrappers report these as the level's height in a cubic level (mixin/dh), and the cubic world
 * generator builds its columns over them (CubicDhWorldGenerator). Free of Distant Horizons' classes, for the mixins.
 */
public final class DhWindow {
    private DhWindow() {
    }

    public static boolean isCubic(Object level) {
        return level instanceof Level && ((CanBeCubic) level).cc_isCubic();
    }

    /** The lowest Y Distant Horizons holds in a cubic level. */
    public static int minY() {
        return CubicChunks.config().getDistantHorizonsMinY();
    }

    /** How many blocks up from {@link #minY} it holds. */
    public static int height() {
        return CommonConfig.DISTANT_HORIZONS_HEIGHT;
    }
}
