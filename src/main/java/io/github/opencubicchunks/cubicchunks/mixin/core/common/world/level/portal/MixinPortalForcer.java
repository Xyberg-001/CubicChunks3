package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.portal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A portal made in a cubic level: between the heights the level holds, not its dimension type's (an Orbis world's type can end below its
 * ground, and no portal could be made at all), and looking for room no deeper than {@link #SEARCH_DEPTH} under the surface (vanilla looks
 * all the way down, which in a cubic level would load cubes thousands of blocks deep).
 */
@Mixin(PortalForcer.class)
public abstract class MixinPortalForcer {
    private static final int SEARCH_DEPTH = 96;

    @Shadow @Final private ServerLevel level;

    @WrapOperation(method = "createPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMaxY()I"))
    private int cc_cubicMaxY(ServerLevel level, Operation<Integer> original) {
        return ((CanBeCubic) level).cc_isCubic() ? CubicHeight.maxY(level) : original.call(level);
    }

    @WrapOperation(method = "createPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getLogicalHeight()I"))
    private int cc_cubicLogicalHeight(ServerLevel level, Operation<Integer> original) {
        return ((CanBeCubic) level).cc_isCubic() ? CubicHeight.maxY(level) - CubicHeight.minY(level) + 1 : original.call(level);
    }

    @WrapOperation(method = "createPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinY()I"))
    private int cc_cubicMinY(ServerLevel level, Operation<Integer> original, @Local(argsOnly = true) BlockPos origin) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return original.call(level);
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
        return Math.max(CubicHeight.minY(level), surface - SEARCH_DEPTH);
    }
}
