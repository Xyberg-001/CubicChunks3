package io.github.opencubicchunks.cubicchunks.mixin.core.client.gui.screens;

import io.github.opencubicchunks.cubicchunks.client.gui.screens.CubicLevelLoadingScreen;
import io.github.opencubicchunks.cubicchunks.server.level.progress.CubicChunkLoadStatusView;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a cubic level loads, the screen's chunk map shows its cubes instead (see {@link CubicLevelLoadingScreen}). */
@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen {
    @Shadow @Final private static Object2IntMap<ChunkStatus> COLORS;

    @Inject(method = "extractChunksForRendering", at = @At("HEAD"), cancellable = true)
    private static void cc_extractCubes(
            GuiGraphicsExtractor graphics, int xCenter, int yCenter, int size, int margin, ChunkLoadStatusView statusView, CallbackInfo ci
    ) {
        if (statusView instanceof CubicChunkLoadStatusView cubic && cubic.cc_isCubic()) {
            CubicLevelLoadingScreen.extract(graphics, xCenter, yCenter, cubic, COLORS);
            ci.cancel();
        }
    }
}
