package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The block view a section's mesh is built from: whether the section is worth building is read from its cube (see SodiumCubes), and its
 * height bounds are the cubic world's, so blocks above the dimension's top are not taken as outside the world.
 */
@Mixin(LevelSlice.class)
public abstract class MixinLevelSlice {
    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private static LevelChunkSection[] cc_cubeSection(LevelChunk chunk, Operation<LevelChunkSection[]> original,
            @Local(argsOnly = true) Level level, @Local(argsOnly = true) SectionPos pos) {
        return SodiumCubes.isCubic(level) ? SodiumCubes.sectionArray(level, pos.x(), pos.y(), pos.z()) : original.call(chunk);
    }

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSectionIndexFromSectionY(I)I"))
    private static int cc_cubeSectionIndex(Level level, int sectionY, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? 0 : original.call(level, sectionY);
    }

    @WrapOperation(method = "getHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeight()I"))
    private int cc_cubicHeight(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? CubicHeight.maxY(level) - CubicHeight.minY(level) + 1 : original.call(level);
    }

    @WrapOperation(method = "getMinY", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"))
    private int cc_cubicMinY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? CubicHeight.minY(level) : original.call(level);
    }
}
