package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.world.cloned;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The sections Sodium copies for a mesh build come from their cubes (see SodiumCubes); the column it passes along is the empty one. */
@Mixin(ClonedChunkSectionCache.class)
public abstract class MixinClonedChunkSectionCache {
    @Shadow @Final private Level level;

    @WrapOperation(method = "clone", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private LevelChunkSection[] cc_cubeSection(LevelChunk chunk, Operation<LevelChunkSection[]> original,
            @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y, @Local(argsOnly = true, ordinal = 2) int z) {
        return SodiumCubes.isCubic(this.level) ? SodiumCubes.sectionArray(this.level, x, y, z) : original.call(chunk);
    }

    @WrapOperation(method = "clone", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSectionIndexFromSectionY(I)I"))
    private int cc_cubeSectionIndex(Level level, int sectionY, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? 0 : original.call(level, sectionY);
    }
}
