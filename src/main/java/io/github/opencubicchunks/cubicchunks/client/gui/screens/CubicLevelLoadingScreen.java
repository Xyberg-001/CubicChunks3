package io.github.opencubicchunks.cubicchunks.client.gui.screens;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.server.level.progress.CubicChunkLoadStatusView;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The loading screen's map for a cubic level, in vanilla's status colours, drawn where vanilla draws its chunk map:
 * <ul>
 * <li>left, from above: one cell per column of cubes around the focus, coloured by the least-loaded cube of the column within the window of
 * cube heights shown on the right;</li>
 * <li>right, from the side: the cubes of the slice through the focus (east-west across, cube height up), the focus height marked.</li>
 * </ul>
 * A cube is two of vanilla's 2-pixel chunk cells wide, so the map covers about the area vanilla's would.
 */
public final class CubicLevelLoadingScreen {
    private static final int CELL = 4;
    private static final int GAP = 10;
    private static final int MAX_VERTICAL_RADIUS = 4;

    private CubicLevelLoadingScreen() {}

    public static void extract(GuiGraphicsExtractor graphics, int xCenter, int yCenter, CubicChunkLoadStatusView view, Object2IntMap<ChunkStatus> colors) {
        int radius = view.cc_cubeRadius();
        int verticalRadius = Math.min(radius, MAX_VERTICAL_RADIUS);
        int width = (radius * 2 + 1) * CELL;
        int height = (verticalRadius * 2 + 1) * CELL;
        int left = xCenter - (width * 2 + GAP) / 2;
        int top = yCenter - width / 2;

        // from above: the least-loaded cube of each column in the height window
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkStatus lowest = view.cc_status(dx, -verticalRadius, dz);
                for (int dy = -verticalRadius + 1; dy <= verticalRadius && lowest != null; dy++) {
                    lowest = lowest(lowest, view.cc_status(dx, dy, dz));
                }
                int x = left + (dx + radius) * CELL;
                int y = top + (dz + radius) * CELL;
                graphics.fill(x, y, x + CELL, y + CELL, colour(colors, lowest));
            }
        }

        // from the side: the slice through the focus, highest cubes at the top
        int sideLeft = left + width + GAP;
        int sideTop = yCenter - height / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                int x = sideLeft + (dx + radius) * CELL;
                int y = sideTop + (verticalRadius - dy) * CELL;
                graphics.fill(x, y, x + CELL, y + CELL, colour(colors, view.cc_status(dx, dy, 0)));
            }
        }
        int focusY = sideTop + verticalRadius * CELL;
        graphics.fill(sideLeft - 3, focusY + 1, sideLeft - 1, focusY + CELL - 1, ARGB.opaque(0xFFFFFF));
    }

    private static @Nullable ChunkStatus lowest(ChunkStatus a, @Nullable ChunkStatus b) {
        return b == null ? null : b.isOrAfter(a) ? a : b;
    }

    private static int colour(Object2IntMap<ChunkStatus> colors, @Nullable ChunkStatus status) {
        return ARGB.opaque(colors.getInt(status));
    }
}
