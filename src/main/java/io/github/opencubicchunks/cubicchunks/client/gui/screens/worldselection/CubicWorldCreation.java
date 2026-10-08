package io.github.opencubicchunks.cubicchunks.client.gui.screens.worldselection;

import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;

/**
 * The world creation screen's Cubic Chunks choices (on its WorldCreationUiState, see MixinWorldCreationUiState): whether the new world is
 * cubic and the heights it holds. They start from the config.
 */
public interface CubicWorldCreation {
    boolean cc_isCubic();

    void cc_setCubic(boolean cubic);

    int cc_getMinY();

    void cc_setMinY(int minY);

    int cc_getMaxY();

    void cc_setMaxY(int maxY);

    /** The settings the new world is made with: the choices, with the heights brought within what the game can hold. */
    default CubicWorldSettings cc_settings() {
        return new CubicWorldSettings(this.cc_isCubic(), this.cc_getMinY(), this.cc_getMaxY()).clamped();
    }
}
