package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.border;

import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world border reaches no farther than a block position holds (see {@link CubicHeight#borderLimit()}): its absolute maximum, from
 * which its edges are clamped, starts there and is never set beyond it (the server's max-world-size). On the client too, which is never
 * told the server's: a border bigger than that is drawn and collided with where the server has it.
 */
@Mixin(WorldBorder.class)
public class MixinWorldBorder {
    @Inject(method = "<init>(Lnet/minecraft/world/level/border/WorldBorder$Settings;)V", at = @At("RETURN"))
    private void cc_defaultAbsoluteMaxSize(CallbackInfo ci) {
        WorldBorder border = (WorldBorder) (Object) this;
        border.setAbsoluteMaxSize(border.getAbsoluteMaxSize()); // clamped below, and the edges worked out again
    }

    @ModifyVariable(method = "setAbsoluteMaxSize", at = @At("HEAD"), argsOnly = true)
    private int cc_clampAbsoluteMaxSize(int size) {
        return Math.min(size, CubicHeight.borderLimit());
    }
}
