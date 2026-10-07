package io.github.opencubicchunks.cubicchunks.world.level;

import net.minecraft.core.BlockPos;

/**
 * How far a cubic world reaches. A block position is packed into 64 bits everywhere (block ticks, maps, the network); vanilla gives 26 bits
 * to each horizontal axis and 12 to Y (Y -2048..2047). Cubic chunks gives 25, 25 and 14 instead (see MixinBlockPos): x and z within
 * about +-16.7 million, Y -8192..8191.
 */
public final class CubicHeight {
    /** Horizontal size BlockPos sizes its packing for (vanilla: 30,000,000, so 26 bits); 16,000,000 rounds up to 2^24, so 25 bits. */
    public static final int PACKING_HORIZONTAL_SIZE = 16_000_000;

    private CubicHeight() {
    }

    /** Lowest Y a cubic world can hold. */
    public static int minY() {
        return -(1 << (BlockPos.PACKED_Y_LENGTH - 1));
    }

    /** Highest Y a cubic world can hold. */
    public static int maxY() {
        return (1 << (BlockPos.PACKED_Y_LENGTH - 1)) - 1;
    }

    /** The horizontal coordinates a block position can hold: -limit up to limit - 1. */
    public static int horizontalLimit() {
        return BlockPos.MAX_HORIZONTAL_COORDINATE + 1;
    }
}
