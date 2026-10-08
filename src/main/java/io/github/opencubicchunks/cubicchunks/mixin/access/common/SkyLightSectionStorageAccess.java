package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SkyLightSectionStorage.class)
public interface SkyLightSectionStorageAccess {
    @Invoker("hasLightDataAtOrBelow") boolean cc_hasLightDataAtOrBelow(int sectionY);
}
