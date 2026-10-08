package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicRenderSections;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The dirty-section tracker of a cubic level keeps cubic storage (see {@link CubicRenderSections}), and a section only compiles once the
 * cubes all around it are there, above and below included (vanilla checks the eight chunk columns around it).
 */
@Mixin(SectionUpdateTracker.class)
public abstract class MixinSectionUpdateTracker {
    @WrapOperation(method = "<init>", at = @At(value = "NEW",
            target = "(IIILnet/minecraft/client/RotatingSectionStorage$ValueCreator;)Lnet/minecraft/client/RotatingSectionStorage;"))
    private RotatingSectionStorage<?> cc_cubicSections(
            int radius, int minSectionY, int maxSectionY, RotatingSectionStorage.ValueCreator<?> creator, Operation<RotatingSectionStorage<?>> original,
            @Local(argsOnly = true) LevelHeightAccessor heightAccessor
    ) {
        if (!(heightAccessor instanceof CanBeCubic level && level.cc_isCubic())) {
            return original.call(radius, minSectionY, maxSectionY, creator);
        }
        RotatingSectionStorage<?> storage = original.call(radius, -radius, radius, creator);
        ((CubicRenderSections.Storage) storage).cc_makeCubic();
        return storage;
    }

    // TODO (P2) lighting: vanilla also waits for light in the column (LevelLightEngine.lightOnInColumn); cubes have no client light yet
    @Inject(method = "hasAllNeighbors", at = @At("HEAD"), cancellable = true)
    private void cc_hasAllNeighborCubes(ClientLevel level, long sectionNode, CallbackInfoReturnable<Boolean> cir) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long neighbor = SectionPos.offset(sectionNode, dx, dy, dz);
                    if (((CubicLevel) level).cc_getCube(Coords.sectionToCube(SectionPos.x(neighbor)), Coords.sectionToCube(SectionPos.y(neighbor)),
                            Coords.sectionToCube(SectionPos.z(neighbor)), ChunkStatus.FULL, false) == null) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }
        cir.setReturnValue(true);
    }
}
