package io.github.opencubicchunks.cubicchunks.mixin.core.client.worldselection;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.gui.screens.worldselection.CubicWorldCreation;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Holds the screen's Cubic Chunks choices beside vanilla's (see CubicWorldCreation); a change tells the screen's listeners, as vanilla's do. */
@Mixin(WorldCreationUiState.class)
public abstract class MixinWorldCreationUiState implements CubicWorldCreation {
    @Unique private boolean cc_cubic = CubicChunks.config().shouldGenerateNewWorldsAsCC();
    @Unique private int cc_minY = CubicChunks.config().getNewWorldMinY();
    @Unique private int cc_maxY = CubicChunks.config().getNewWorldMaxY();

    @Shadow public abstract void onChanged();

    @Override public boolean cc_isCubic() {
        return this.cc_cubic;
    }

    @Override public void cc_setCubic(boolean cubic) {
        this.cc_cubic = cubic;
        this.onChanged();
    }

    @Override public int cc_getMinY() {
        return this.cc_minY;
    }

    @Override public void cc_setMinY(int minY) {
        this.cc_minY = minY;
        this.onChanged();
    }

    @Override public int cc_getMaxY() {
        return this.cc_maxY;
    }

    @Override public void cc_setMaxY(int maxY) {
        this.cc_maxY = maxY;
        this.onChanged();
    }
}
