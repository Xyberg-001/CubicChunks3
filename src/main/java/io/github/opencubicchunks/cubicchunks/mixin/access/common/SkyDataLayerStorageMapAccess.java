package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkyLightSectionStorage.SkyDataLayerStorageMap.class)
public interface SkyDataLayerStorageMapAccess {
    @Accessor("topSections") Long2IntOpenHashMap cc_topSections();

    @Accessor("currentLowestY") int cc_currentLowestY();
}
