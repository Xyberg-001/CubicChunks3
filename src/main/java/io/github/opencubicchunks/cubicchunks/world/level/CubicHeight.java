package io.github.opencubicchunks.cubicchunks.world.level;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * How far a cubic world reaches. A block position is packed into 64 bits everywhere (block ticks, maps, the network); vanilla gives 26 bits
 * to each horizontal axis and 12 to Y (Y -2048..2047). Cubic chunks gives more to Y (see MixinBlockPos), as the heightLimit config says when
 * the game starts: 25, 25 and 14 bits (x and z within about +-16.7 million, Y -8192..8191) or 24, 24 and 16 (x and z within about +-8.4
 * million, Y -32768..32767). That is the most any cubic world can hold; each world holds its own part of it (see {@link #minY(Level)} and
 * CubicWorldSettings).
 */
public final class CubicHeight {
    private CubicHeight() {
    }

    /**
     * Horizontal size BlockPos sizes its packing for (vanilla: 30,000,000, so 26 bits): 16,000,000 rounds up to 2^24, so 25 bits, leaving Y
     * 14; 8,000,000 rounds up to 2^23, so 24 bits, leaving Y 16.
     */
    public static int packingHorizontalSize() {
        return CubicChunks.config().getHeightLimit() == CommonConfig.HEIGHT_LIMIT_TALL ? 8_000_000 : 16_000_000;
    }

    /** Lowest Y a cubic world can hold. */
    public static int minY() {
        return -(1 << (BlockPos.PACKED_Y_LENGTH - 1));
    }

    /** Highest Y a cubic world can hold. */
    public static int maxY() {
        return (1 << (BlockPos.PACKED_Y_LENGTH - 1)) - 1;
    }

    /** Lowest Y this cubic level holds (its world's choice, within {@link #minY()}). */
    public static int minY(Level level) {
        return ((BuildHeight) level).cc_minBuildY();
    }

    /** Highest Y this cubic level holds (its world's choice, within {@link #maxY()}). */
    public static int maxY(Level level) {
        return ((BuildHeight) level).cc_maxBuildY();
    }

    /** The horizontal coordinates a block position can hold: -limit up to limit - 1. */
    public static int horizontalLimit() {
        return BlockPos.MAX_HORIZONTAL_COORDINATE + 1;
    }

    /**
     * Farthest the world border reaches from 0 on x and z (vanilla: 29,999,984): the packing's limit less a margin, so that nothing loaded
     * around a player at the border (view distance, a far-view mod's generation) reaches past what a block position holds, where positions
     * would wrap around to the other side of the world. Applies to every world, as the packing does.
     */
    public static int borderLimit() {
        return horizontalLimit() - BORDER_MARGIN;
    }

    /** Where entities are held (vanilla: 30,000,000, just past its border). */
    public static double movementLimit() {
        return borderLimit() + 16;
    }

    private static final int BORDER_MARGIN = 65_536;

    /** A level's own height limits (implemented on Level, see MixinLevel); a level that is not cubic answers with its dimension's. */
    public interface BuildHeight {
        int cc_minBuildY();

        int cc_maxBuildY();
    }
}
