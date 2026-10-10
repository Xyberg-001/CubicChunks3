package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.commands;

import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.server.commands.ForceLoadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** /forceload refuses areas beyond the world border's reach (see CubicHeight), as vanilla refuses them beyond 30,000,000. */
@Mixin(ForceLoadCommand.class)
public class MixinForceLoadCommand {
    @ModifyConstant(method = "changeForceLoad", constant = @Constant(intValue = 30000000))
    private static int cc_maxBound(int bound) {
        return CubicHeight.borderLimit();
    }

    @ModifyConstant(method = "changeForceLoad", constant = @Constant(intValue = -30000000))
    private static int cc_minBound(int bound) {
        return -CubicHeight.borderLimit();
    }
}
