package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageAccess {
    @Invoker("storingLightForSection") boolean cc_storingLightForSection(long sectionNode);

    @Invoker("getStoredLevel") int cc_getStoredLevel(long blockNode);

    @Invoker("setStoredLevel") void cc_setStoredLevel(long blockNode, int level);
}
