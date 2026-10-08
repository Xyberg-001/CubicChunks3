package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicRenderSections;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A view area built for a cubic level (see {@link CubicRenderSections}) keeps its sections in cubic storage, and its block-height bounds,
 * which limit how far occlusion culling traces, are the cubic world's.
 */
@Mixin(ViewArea.class)
public abstract class MixinViewArea implements CubicRenderSections.Area {
    @Shadow @Final private RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;

    @WrapOperation(method = "<init>", at = @At(value = "NEW",
            target = "(IIILnet/minecraft/client/RotatingSectionStorage$ValueCreator;)Lnet/minecraft/client/RotatingSectionStorage;"))
    private RotatingSectionStorage<?> cc_cubicSections(
            int radius, int minSectionY, int maxSectionY, RotatingSectionStorage.ValueCreator<?> creator, Operation<RotatingSectionStorage<?>> original
    ) {
        if (!CubicRenderSections.buildingCubic()) {
            return original.call(radius, minSectionY, maxSectionY, creator);
        }
        RotatingSectionStorage<?> storage = original.call(radius, -radius, radius, creator);
        ((CubicRenderSections.Storage) storage).cc_makeCubic();
        return storage;
    }

    @Override public boolean cc_isCubic() {
        return ((CubicRenderSections.Storage) this.sections).cc_isCubic();
    }

    @Inject(method = "minY", at = @At("HEAD"), cancellable = true)
    private void cc_minY(CallbackInfoReturnable<Integer> cir) {
        if (this.cc_isCubic()) {
            cir.setReturnValue(CubicHeight.minY());
        }
    }

    @Inject(method = "maxY", at = @At("HEAD"), cancellable = true)
    private void cc_maxY(CallbackInfoReturnable<Integer> cir) {
        if (this.cc_isCubic()) {
            cir.setReturnValue(CubicHeight.maxY());
        }
    }
}
