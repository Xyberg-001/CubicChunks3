package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.network;

import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A player's and vehicle's moves are held within what a block position holds, as vanilla holds them within 30,000,000 (see CubicHeight).
 * <p>
 * And in a cubic level, a player whose moves arrive several to a server tick (the server stood still a moment, as it can while cubes are
 * made around someone flying fast) is not pulled back as having "moved too quickly": vanilla measures them all from where the player was at
 * the tick's start against 100 times their number (a squared distance against a count), and counts more than five as one, so a quarter of
 * a second's stall took any player moving faster than walking back. Here each move may be up to vanilla's 10 blocks (300 squared falling
 * with elytra), for up to 20 moves.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {
    private static final int CC_MAX_PACKETS = 20;

    @Shadow public ServerPlayer player;

    @ModifyConstant(method = "handlePlayerPositionChange", constant = @Constant(intValue = 5))
    private int cc_noResetToOne(int limit) {
        return ((CanBeCubic) this.player.level()).cc_isCubic() ? Integer.MAX_VALUE : limit;
    }

    @ModifyVariable(method = "handlePlayerPositionChange", at = @At("STORE"), name = "deltaPackets")
    private int cc_distancePerMove(int packets) {
        if (!((CanBeCubic) this.player.level()).cc_isCubic()) {
            return packets;
        }
        int counted = Math.min(Math.max(packets, 1), CC_MAX_PACKETS);
        return counted * counted; // the allowance is 100 x this, against the squared distance of all the moves together
    }
    @ModifyConstant(method = "clampHorizontal", constant = @Constant(doubleValue = 3.0E7))
    private static double cc_clampHorizontalMax(double limit) {
        return CubicHeight.movementLimit();
    }

    @ModifyConstant(method = "clampHorizontal", constant = @Constant(doubleValue = -3.0E7))
    private static double cc_clampHorizontalMin(double limit) {
        return -CubicHeight.movementLimit();
    }
}
