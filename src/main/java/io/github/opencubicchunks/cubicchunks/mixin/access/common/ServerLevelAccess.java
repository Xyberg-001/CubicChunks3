package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Where vanilla's thunder lands from a surface spot, which a cube's thunder uses the same way (see CubicThunder). */
@Mixin(ServerLevel.class)
public interface ServerLevelAccess {
    @Invoker("findLightningTargetAround") BlockPos cc_findLightningTargetAround(BlockPos pos);
}
