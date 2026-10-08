package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.client.renderer.CubicRenderSections;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cubic section storage (see {@link CubicRenderSections}): built with a Y band of -radius..radius, and from then on Y is handled like X and Z,
 * each grid slot holding whichever section of its residue class lies within the radius of the camera. Vanilla code is left alone for
 * storages of non-cubic levels.
 */
@Mixin(RotatingSectionStorage.class)
public abstract class MixinRotatingSectionStorage<T extends RotatingSectionStorage.Value> implements CubicRenderSections.Storage {
    @Shadow @Final private RotatingSectionStorage.Node<T>[] nodes;
    @Shadow @Final private int radius;
    @Shadow @Final private int sectionGridSizeY;
    @Shadow @Final private int sectionGridSizeXZ;
    @Shadow private SectionPos centerSectionPos;

    @Unique private boolean cc_cubic;

    @Shadow private int getSectionIndex(int x, int y, int z) {
        throw new AssertionError();
    }

    @Override public void cc_makeCubic() {
        cc_cubic = true;
    }

    @Override public boolean cc_isCubic() {
        return cc_cubic;
    }

    @Inject(method = "repositionCenter", at = @At("HEAD"), cancellable = true)
    private void cc_repositionCenterCubic(SectionPos newCenter, CallbackInfoReturnable<Boolean> cir) {
        if (!cc_cubic) {
            return;
        }
        if (newCenter.equals(this.centerSectionPos)) {
            cir.setReturnValue(false);
            return;
        }
        int lowestX = newCenter.x() - this.radius;
        int lowestY = newCenter.y() - this.radius;
        int lowestZ = newCenter.z() - this.radius;
        for (int gridX = 0; gridX < this.sectionGridSizeXZ; gridX++) {
            int sectionX = lowestX + Math.floorMod(gridX - lowestX, this.sectionGridSizeXZ);
            for (int gridZ = 0; gridZ < this.sectionGridSizeXZ; gridZ++) {
                int sectionZ = lowestZ + Math.floorMod(gridZ - lowestZ, this.sectionGridSizeXZ);
                for (int gridY = 0; gridY < this.sectionGridSizeY; gridY++) {
                    int sectionY = lowestY + Math.floorMod(gridY - lowestY, this.sectionGridSizeY);
                    T value = this.nodes[this.getSectionIndex(gridX, gridY, gridZ)].value();
                    long sectionNode = SectionPos.asLong(sectionX, sectionY, sectionZ);
                    if (value.getSectionNode() != sectionNode) {
                        value.setSectionNode(sectionNode);
                    }
                }
            }
        }
        this.centerSectionPos = newCenter;
        cir.setReturnValue(true);
    }

    @Inject(method = "getValue(III)Lnet/minecraft/client/RotatingSectionStorage$Value;", at = @At("HEAD"), cancellable = true)
    private void cc_getValueCubic(int sectionX, int sectionY, int sectionZ, CallbackInfoReturnable<T> cir) {
        if (!cc_cubic) {
            return;
        }
        SectionPos center = this.centerSectionPos;
        if (Math.abs(sectionX - center.x()) > this.radius || Math.abs(sectionY - center.y()) > this.radius
                || Math.abs(sectionZ - center.z()) > this.radius) {
            cir.setReturnValue(null);
            return;
        }
        int x = Math.floorMod(sectionX, this.sectionGridSizeXZ);
        int y = Math.floorMod(sectionY, this.sectionGridSizeY);
        int z = Math.floorMod(sectionZ, this.sectionGridSizeXZ);
        cir.setReturnValue(this.nodes[this.getSectionIndex(x, y, z)].value());
    }

    /** The band of section heights currently held moves with the camera. */
    @Inject(method = "minY", at = @At("HEAD"), cancellable = true)
    private void cc_minYCubic(CallbackInfoReturnable<Integer> cir) {
        if (cc_cubic) {
            cir.setReturnValue(this.centerSectionPos.y() - this.radius);
        }
    }

    @Inject(method = "maxY", at = @At("HEAD"), cancellable = true)
    private void cc_maxYCubic(CallbackInfoReturnable<Integer> cir) {
        if (cc_cubic) {
            cir.setReturnValue(this.centerSectionPos.y() + this.radius);
        }
    }
}
