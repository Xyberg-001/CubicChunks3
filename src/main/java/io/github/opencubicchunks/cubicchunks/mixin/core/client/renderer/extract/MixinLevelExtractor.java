package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer.extract;

import javax.annotation.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Each frame the extractor tells the section graph which of the chunks it waits for have arrived. In a cubic level the graph waits for cubes
 * (keyed by packed cube position, see MixinSectionOcclusionGraph), so arrival is checked in the cube cache.
 */
@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {
    @Shadow private @Nullable ClientLevel level;

    @WrapOperation(method = "lambda$extract$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;hasChunk(II)Z"))
    private boolean cc_hasExpectedCube(ClientChunkCache cache, int x, int z, Operation<Boolean> original, @Local(argsOnly = true) long expected) {
        if (this.level == null || !((CanBeCubic) this.level).cc_isCubic()) {
            return original.call(cache, x, z);
        }
        return ((CubeSource) cache).cc_getCube(CubePos.extractX(expected), CubePos.extractY(expected), CubePos.extractZ(expected), false) != null;
    }
}
