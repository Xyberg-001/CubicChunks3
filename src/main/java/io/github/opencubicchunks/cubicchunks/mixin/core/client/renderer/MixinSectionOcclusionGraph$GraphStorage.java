package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicRenderSections;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The octree of visible sections centres its box on the camera vertically only when the level is taller than the box; otherwise it starts
 * at the world's floor. A cubic level counts as taller than any box.
 */
@Mixin(targets = "net.minecraft.client.renderer.SectionOcclusionGraph$GraphStorage")
public class MixinSectionOcclusionGraph$GraphStorage {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;sectionCount()I"))
    private int cc_unboundedHeight(ViewArea viewArea, Operation<Integer> original) {
        return ((CubicRenderSections.Area) viewArea).cc_isCubic() ? Integer.MAX_VALUE : original.call(viewArea);
    }
}
