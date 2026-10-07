package io.github.opencubicchunks.cubicchunks.mixin.core.common.core;

import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Packs block positions as 25 bits of x, 25 of z and 14 of Y instead of 26, 26 and 12, so a cubic world reaches Y -8192..8191 (see
 * {@link CubicHeight}). BlockPos works the split out from the horizontal size it is given; this gives it a smaller one. It applies to every
 * world, as packed positions are shared code: Level's horizontal bounds shrink to match (see MixinLevel).
 */
@Mixin(BlockPos.class)
public class MixinBlockPos {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 30000000))
    private static int cc_packingHorizontalSize(int size) {
        return CubicHeight.PACKING_HORIZONTAL_SIZE;
    }
}
