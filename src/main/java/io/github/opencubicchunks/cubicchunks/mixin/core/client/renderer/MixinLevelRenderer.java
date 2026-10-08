package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicRenderSections;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The view area of a cubic level is built cubic (see {@link CubicRenderSections}). */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @WrapOperation(method = "invalidateCompiledGeometry", at = @At(value = "NEW", target = "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;"
            + "IIIIILnet/minecraft/client/renderer/SectionOcclusionGraph;)Lnet/minecraft/client/renderer/ViewArea;"))
    private ViewArea cc_buildViewArea(
            SectionRenderDispatcher dispatcher, int minY, int maxY, int minSectionY, int maxSectionY, int renderDistance, SectionOcclusionGraph graph,
            Operation<ViewArea> original, @Local(argsOnly = true) ClientLevel level
    ) {
        return CubicRenderSections.build(((CanBeCubic) level).cc_isCubic(),
                () -> original.call(dispatcher, minY, maxY, minSectionY, maxSectionY, renderDistance, graph));
    }
}
