package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A map drawn in a cubic level: vanilla reads each pixel's surface and blocks from the column's chunk, which in a cubic level holds neither
 * (every pixel came out as bedrock). There they come from the level (its surface worked out from the cubes, see MixinLevel.getHeight, and
 * its blocks from the cubes). A column whose surface cube is not loaded is left as the map has it, as vanilla leaves a chunk not loaded; a
 * block in a cube not loaded stops the look down (as stone) rather than walking thousands of blocks of nothing.
 */
@Mixin(MapItem.class)
public abstract class MixinMapItem {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;isEmpty()Z"))
    private boolean cc_surfaceNotLoaded(LevelChunk chunk, Operation<Boolean> original, @Local(argsOnly = true) Level level) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return original.call(chunk);
        }
        int x = chunk.getPos().getMiddleBlockX(), z = chunk.getPos().getMiddleBlockZ();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        return !cc_cubeLoaded(level, x, surfaceY, z);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int cc_cubicSurface(LevelChunk chunk, Heightmap.Types type, int x, int z, Operation<Integer> original, @Local(argsOnly = true) Level level) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return original.call(chunk, type, x, z);
        }
        return level.getHeight(type, x, z) - 1; // the chunk's heightmap gives the top block, the level's the Y above it
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState cc_cubicBlock(LevelChunk chunk, BlockPos pos, Operation<BlockState> original, @Local(argsOnly = true) Level level) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return original.call(chunk, pos);
        }
        return cc_cubeLoaded(level, pos.getX(), pos.getY(), pos.getZ()) ? level.getBlockState(pos) : Blocks.STONE.defaultBlockState();
    }

    private static boolean cc_cubeLoaded(Level level, int x, int y, int z) {
        return ((CubeSource) level.getChunkSource()).cc_getCubeNow(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z)) != null;
    }
}
