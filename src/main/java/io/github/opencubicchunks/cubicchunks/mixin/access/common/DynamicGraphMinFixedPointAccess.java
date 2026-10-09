package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DynamicGraphMinFixedPoint.class)
public interface DynamicGraphMinFixedPointAccess {
    /** The levels positions are queued to take (positions with none queued are not in it). */
    @Accessor("computedLevels") Long2ByteMap cc_computedLevels();
}
