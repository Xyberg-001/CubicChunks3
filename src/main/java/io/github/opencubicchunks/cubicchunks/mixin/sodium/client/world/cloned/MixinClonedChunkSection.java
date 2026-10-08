package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.world.cloned;

import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A copied section's block entities come from its cube in a cubic level (the column passed along there is the empty one). */
@Mixin(ClonedChunkSection.class)
public abstract class MixinClonedChunkSection {
    @WrapOperation(method = "copyBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockEntities()Ljava/util/Map;"))
    private static Map<BlockPos, BlockEntity> cc_cubeBlockEntities(LevelChunk chunk, Operation<Map<BlockPos, BlockEntity>> original,
            @Local(argsOnly = true) SectionPos sectionPos) {
        Level level = chunk.getLevel();
        if (!SodiumCubes.isCubic(level)) {
            return original.call(chunk);
        }
        LevelCube cube = SodiumCubes.cubeOfSection(level, sectionPos.x(), sectionPos.y(), sectionPos.z());
        return cube == null ? Map.of() : cube.getBlockEntities();
    }
}
