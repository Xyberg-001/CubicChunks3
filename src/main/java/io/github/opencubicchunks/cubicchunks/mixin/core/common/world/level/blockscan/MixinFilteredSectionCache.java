package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.blockscan;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.blockscan.FilteredSectionCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** See MixinBlockScanUtils: the scans by distance (findBlocksInBoxByManhattanDistance) read their sections from the cubes too. */
@Mixin(FilteredSectionCache.class)
public abstract class MixinFilteredSectionCache {
    @Shadow @Final private LevelReader level;

    @WrapOperation(method = "getOrLoad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSection(I)Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private LevelChunkSection cc_cubeSection(ChunkAccess chunk, int index, Operation<LevelChunkSection> original,
            @Local(argsOnly = true, ordinal = 0) int sectionX, @Local(argsOnly = true, ordinal = 1) int sectionY,
            @Local(argsOnly = true, ordinal = 2) int sectionZ) {
        if (this.level instanceof Level && this.level instanceof CanBeCubic cubic && cubic.cc_isCubic() && this.level instanceof CubicLevelReader cubes) {
            CubeAccess cube = cubes.cc_getCube(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY), Coords.sectionToCube(sectionZ),
                    ChunkStatus.FULL, true);
            if (cube != null) {
                return cube.getSection(Coords.sectionToIndex(sectionX, sectionY, sectionZ));
            }
        }
        return original.call(chunk, index);
    }
}
