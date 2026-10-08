package io.github.opencubicchunks.cubicchunks.mixin.core.common.core;

import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Packs block positions with more bits for Y than vanilla's 26, 26 and 12 (25, 25 and 14, or 24, 24 and 16 with the tall height limit; see
 * {@link CubicHeight}). BlockPos works the split out from the horizontal size it is given; this gives it a smaller one, read from the config
 * as BlockPos is first loaded. It applies to every world, as packed positions are shared code: Level's horizontal bounds shrink to match
 * (see MixinLevel).
 */
@Mixin(BlockPos.class)
public class MixinBlockPos {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 30000000))
    private static int cc_packingHorizontalSize(int size) {
        return CubicHeight.packingHorizontalSize();
    }
}
