package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.entity;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Whether an entity's box has any fluid, before it looks block by block (swimming, floating, drowning, lava): vanilla asks the chunks'
 * sections, which hold nothing in a cubic level, so no entity there was ever in water. In a cubic level the cubes' sections answer, and an
 * unloaded cube counts as unloaded, as vanilla's unloaded chunk does.
 */
@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {
    @Inject(method = "hasFluidAndLoaded", at = @At("HEAD"), cancellable = true)
    private static void cc_cubesHaveFluid(Level level, int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof CanBeCubic cubic && cubic.cc_isCubic() && level instanceof CubicLevelReader cubes)) {
            return;
        }
        boolean hasFluid = false;
        for (int sectionZ = SectionPos.blockToSectionCoord(z0); sectionZ <= SectionPos.blockToSectionCoord(z1); sectionZ++) {
            for (int sectionX = SectionPos.blockToSectionCoord(x0); sectionX <= SectionPos.blockToSectionCoord(x1); sectionX++) {
                for (int sectionY = SectionPos.blockToSectionCoord(y0); sectionY <= SectionPos.blockToSectionCoord(y1); sectionY++) {
                    CubeAccess cube = cubes.cc_getCube(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY), Coords.sectionToCube(sectionZ),
                            ChunkStatus.FULL, false);
                    if (cube == null) {
                        cir.setReturnValue(false);
                        return;
                    }
                    hasFluid |= cube.getSection(Coords.sectionToIndex(sectionX, sectionY, sectionZ)).hasFluid();
                }
            }
        }
        cir.setReturnValue(hasFluid);
    }
}
