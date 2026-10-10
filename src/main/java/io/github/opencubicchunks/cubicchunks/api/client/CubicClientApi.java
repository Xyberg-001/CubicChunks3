package io.github.opencubicchunks.cubicchunks.api.client;

import io.github.opencubicchunks.cubicchunks.client.gui.screens.worldselection.CubicWorldCreation;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/** Cubic Chunks for other mods, on the client. */
public final class CubicClientApi {
    private CubicClientApi() {
    }

    /** Whether the world being set up on the world creation screen will be cubic (its Cubic Chunks tab, as it stands now). */
    public static boolean isNewWorldCubic(WorldCreationUiState state) {
        return state instanceof CubicWorldCreation choices && choices.cc_isCubic();
    }
}
