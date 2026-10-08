package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LightEngine.class)
public interface LightEngineAccess {
    @Accessor("storage") LayerLightSectionStorage<?> cc_storage();

    @Invoker("enqueueDecrease") void cc_enqueueDecrease(long fromNode, long decreaseData);
}
