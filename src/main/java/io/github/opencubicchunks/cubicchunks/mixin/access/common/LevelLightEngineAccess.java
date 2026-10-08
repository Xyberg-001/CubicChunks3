package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import javax.annotation.Nullable;

import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelLightEngine.class)
public interface LevelLightEngineAccess {
    @Accessor("skyEngine") @Nullable LightEngine<?, ?> cc_skyEngine();
}
