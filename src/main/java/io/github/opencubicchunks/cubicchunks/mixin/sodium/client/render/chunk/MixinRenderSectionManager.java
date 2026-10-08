package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.render.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A section Sodium adds is looked up in its cube, not its column (see SodiumCubes), and the camera counts as inside the world's heights, not
 * the dimension's.
 */
@Mixin(RenderSectionManager.class)
public abstract class MixinRenderSectionManager {
    @Shadow @Final private ClientLevel level;

    @WrapOperation(method = "onSectionAdded", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private LevelChunkSection[] cc_cubeSection(ChunkAccess chunk, Operation<LevelChunkSection[]> original,
            @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y, @Local(argsOnly = true, ordinal = 2) int z) {
        return SodiumCubes.isCubic(this.level) ? SodiumCubes.sectionArray(this.level, x, y, z) : original.call(chunk);
    }

    @WrapOperation(method = "onSectionAdded", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSectionIndexFromSectionY(I)I"))
    private int cc_cubeSectionIndex(ClientLevel level, int sectionY, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? 0 : original.call(level, sectionY);
    }

    @WrapOperation(method = "isOutOfGraph", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSectionY()I"))
    private int cc_cubicMinSectionY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.minSectionY(level) : original.call(level);
    }

    @WrapOperation(method = "isOutOfGraph", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSectionY()I"))
    private int cc_cubicMaxSectionY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.maxSectionY(level) : original.call(level);
    }
}
