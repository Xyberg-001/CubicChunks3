package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.lighting;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LayerLightSectionStorageAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LightEngineAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla lights a column by queueing every light source the column finds, all of whose sections are in the engine (the chunk's light was
 * initialised). A cubic column finds the sources of the cubes it can see, and some of those may not have had their light initialised yet:
 * a source queued there made the next light update fail (no light data for its section). Those are skipped; such a cube spreads its
 * sources itself once its light step comes.
 */
@Mixin(BlockLightEngine.class)
public abstract class MixinBlockLightEngine {
    @WrapWithCondition(method = "lambda$propagateLightSources$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/BlockLightEngine;enqueueIncrease(JJ)V"))
    private boolean cc_onlyStoredSources(BlockLightEngine engine, long fromNode, long increaseData) {
        return ((LayerLightSectionStorageAccess) ((LightEngineAccess) this).cc_storage()).cc_storingLightForSection(SectionPos.blockToSection(fromNode));
    }
}
