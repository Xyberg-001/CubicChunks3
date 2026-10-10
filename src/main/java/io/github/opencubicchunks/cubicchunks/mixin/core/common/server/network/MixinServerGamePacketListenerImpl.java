package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.network;

import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A player's and vehicle's moves are held within what a block position holds, as vanilla holds them within 30,000,000 (see CubicHeight). */
@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {
    @ModifyConstant(method = "clampHorizontal", constant = @Constant(doubleValue = 3.0E7))
    private static double cc_clampHorizontalMax(double limit) {
        return CubicHeight.movementLimit();
    }

    @ModifyConstant(method = "clampHorizontal", constant = @Constant(doubleValue = -3.0E7))
    private static double cc_clampHorizontalMin(double limit) {
        return -CubicHeight.movementLimit();
    }
}
