package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicCollisionGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The snapshot mobs find their paths in. Vanilla's holds the columns around the mob, which hold no blocks in a cubic level, so there it reads
 * the level's loaded cubes instead (never loading one: a cube that isn't loaded reads as air, as a missing column does in vanilla).
 */
@Mixin(PathNavigationRegion.class)
public abstract class MixinPathNavigationRegion implements CanBeCubic, CubicCollisionGetter {
    @Shadow @Final protected Level level;

    @Unique private long cc_cachedCubePos = Long.MIN_VALUE;
    @Unique private @Nullable BlockGetter cc_cachedCube;

    @Override public boolean cc_isCubic() {
        return ((CanBeCubic) this.level).cc_isCubic();
    }

    @Override public @Nullable BlockGetter cc_getCubeForCollisions(int cubeX, int cubeY, int cubeZ) {
        return ((CubicCollisionGetter) this.level).cc_getCubeForCollisions(cubeX, cubeY, cubeZ);
    }

    @Unique private @Nullable BlockGetter cc_cubeAt(BlockPos pos) {
        int cubeX = Coords.blockToCube(pos.getX());
        int cubeY = Coords.blockToCube(pos.getY());
        int cubeZ = Coords.blockToCube(pos.getZ());
        long key = CubePos.asLong(cubeX, cubeY, cubeZ);
        if (key != this.cc_cachedCubePos) {
            this.cc_cachedCube = this.cc_getCubeForCollisions(cubeX, cubeY, cubeZ);
            this.cc_cachedCubePos = key;
        }
        return this.cc_cachedCube;
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cc_cubeBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (this.cc_isCubic()) {
            BlockGetter cube = this.cc_cubeAt(pos);
            cir.setReturnValue(cube == null ? Blocks.AIR.defaultBlockState() : cube.getBlockState(pos));
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void cc_cubeFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (this.cc_isCubic()) {
            BlockGetter cube = this.cc_cubeAt(pos);
            cir.setReturnValue(cube == null ? Fluids.EMPTY.defaultFluidState() : cube.getFluidState(pos));
        }
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void cc_cubeBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (this.cc_isCubic()) {
            BlockGetter cube = this.cc_cubeAt(pos);
            cir.setReturnValue(cube == null ? null : cube.getBlockEntity(pos));
        }
    }
}
