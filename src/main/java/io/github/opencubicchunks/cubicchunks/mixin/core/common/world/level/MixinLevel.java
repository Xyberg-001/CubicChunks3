package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level;

import javax.annotation.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Direction;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Dasm(value = ChunkToCloSet.class, target = @Ref(Level.class))
@Mixin(Level.class)
public abstract class MixinLevel implements CubicLevel, MarkableAsCubic, LevelAccessor, CubicHeight.BuildHeight {
    @Shadow public abstract @Nullable ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus requestedStatus, boolean forceLoad);

    protected boolean cc_isCubic;
    /** The heights a cubic level holds: its world's choice (see CubicWorldSettings). */
    private int cc_minBuildY;
    private int cc_maxBuildY;

    @Override public void cc_setCubic() {
        cc_isCubic = true;
    }

    @Override public boolean cc_isCubic() {
        return cc_isCubic;
    }

    @Override public int cc_minBuildY() {
        return cc_isCubic ? cc_minBuildY : this.getMinY();
    }

    @Override public int cc_maxBuildY() {
        return cc_isCubic ? cc_maxBuildY : this.getMaxY();
    }

    /**
     * cc_core's build heights (MixinLevelHeightAccessor), which bound SpawnPlaceFinder's search: a cubic level's own, not its dimension
     * type's (a generator may give that a height that only fits its terrain, and the search then never found the air above the ground).
     */
    public int getMinBuildHeight() {
        return this.cc_minBuildY();
    }

    public int getMaxBuildHeight() {
        return this.cc_maxBuildY();
    }

    /**
     * A cubic world holds blocks between the heights its world was made with (see CubicWorldSettings), whatever its dimension type says (its columns
     * keep the dimension's height for what they still size by it); other worlds as vanilla.
     */
    @Override public boolean isOutsideBuildHeight(int y) {
        if (cc_isCubic) {
            return y < cc_minBuildY || y > cc_maxBuildY;
        }
        return y < this.getMinY() || y > this.getMaxY();
    }

    /** 26.x asks the positive question in its bounds checks (isInWorldBounds, isInValidBounds); same answer. */
    @Override public boolean isInsideBuildHeight(int y) {
        return !this.isOutsideBuildHeight(y);
    }

    /** Horizontal bounds shrink to what a packed block position holds (see MixinBlockPos), in every world. */
    @ModifyConstant(method = {"isInWorldBoundsHorizontal", "getHeight"}, constant = @Constant(intValue = 30000000))
    private static int cc_horizontalBound(int bound) {
        return CubicHeight.horizontalLimit();
    }

    @ModifyConstant(method = {"isInWorldBoundsHorizontal", "getHeight"}, constant = @Constant(intValue = -30000000))
    private static int cc_horizontalBoundNegative(int bound) {
        return -CubicHeight.horizontalLimit();
    }

    public LevelCube cc_getCubeAt(BlockPos blockPos) {
        return this.cc_getCube(Coords.blockToCube(blockPos.getX()), Coords.blockToCube(blockPos.getY()), Coords.blockToCube(blockPos.getZ()));
    }

    public LevelCube cc_getCube(int cubeX, int cubeY, int cubeZ) {
        return (LevelCube) this.cc_getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL);
    }

    @Override public @Nullable CubeAccess cc_getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status, boolean forceLoad) {
        CubeAccess cubeaccess = ((CubeSource) this.getChunkSource()).cc_getCube(cubeX, cubeY, cubeZ, status, forceLoad);
        if (cubeaccess == null && forceLoad) {
            throw new IllegalStateException("Should always be able to create a cube!");
        } else {
            return cubeaccess;
        }
    }

    /** A level is cubic if its world is: the server's world, or for a client level the server it joined (see CubicWorldSettings). */
    @Inject(method = "<init>", at = @At(value = "CTOR_HEAD"))
    private void cc_init(
            WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionType,
            boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci
    ) {
        CubicWorldSettings settings = CubicWorldSettings.forNewLevel(isClientSide);
        if (settings.cubic()) {
            this.cc_setCubic();
            this.cc_minBuildY = settings.minY();
            this.cc_maxBuildY = settings.maxY();
        }
    }

    @Override public @Nullable BlockGetter cc_getCubeForCollisions(int cubeX, int cubeY, int cubeZ) {
        return this.cc_getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, false);
    }

    // setBlock
    // Uses LevelChunk to call setBlockState and getFullStatus, so we replace it with a LevelCube and call the Cubic variants of those functions.
    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level"
            + "/Level;getChunkAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk cc_replaceLevelChunkInGetChunkAt(
            Level level, BlockPos blockPos, Operation<LevelChunk> original, @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            levelCubeLocalRef.set(this.cc_getCubeAt(blockPos));
            return null;
        }
        return original.call(level, blockPos);
    }

    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState cc_replaceLevelChunkInSetBlockState(
            LevelChunk levelChunk, BlockPos blockPos, BlockState blockState, int flags, Operation<BlockState> original,
            @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            return levelCubeLocalRef.get().setBlockState(blockPos, blockState, flags);
        }
        return original.call(levelChunk, blockPos, blockState, flags);
    }

    // 26.3 inlines the old markAndNotifyBlock into setBlock; the only other use of the chunk there is its full status
    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;getFullStatus()Lnet/minecraft/server/level/FullChunkStatus;"))
    private FullChunkStatus cc_replaceLevelChunkInGetFullStatus(
            LevelChunk levelChunk, Operation<FullChunkStatus> original, @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            return levelCubeLocalRef.get().getFullStatus();
        }
        return original.call(levelChunk);
    }

    // getBlockState
    // Replaces LevelChunk with a LevelCube to call getBlockState
    @Inject(method = "getBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"),
            cancellable = true)
    private void cc_replaceLevelChunkInGetBlockState(
            BlockPos blockPos, CallbackInfoReturnable<BlockState> cir, @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            LevelCube cube = this.cc_readableCubeAt(blockPos);
            if (cube == null) {
                cir.setReturnValue(net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState());
                return;
            }
            levelCubeLocalRef.set(cube);
        }
    }

    /**
     * The cube a block read looks in: on the server only one that is loaded already, unless loading is allowed here (see CubeLoads); null
     * when there is none (the block reads as void air, its fluid as none). Block reads are many: the loaded cube is looked up first, on the
     * server thread through the chunk source's cache of the last cubes (as vanilla's getChunkNow), and whether loading is allowed only asked
     * when there is none.
     */
    @org.spongepowered.asm.mixin.Unique
    private @Nullable LevelCube cc_readableCubeAt(BlockPos blockPos) {
        Level level = (Level) (Object) this;
        if (level.isClientSide()) {
            return this.cc_getCubeAt(blockPos);
        }
        int cubeX = Coords.blockToCube(blockPos.getX()), cubeY = Coords.blockToCube(blockPos.getY()), cubeZ = Coords.blockToCube(blockPos.getZ());
        LevelCube cube = ((io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource) level.getChunkSource()).cc_getCubeNow(cubeX, cubeY, cubeZ);
        if (cube == null) { // not on the server thread, or not loaded
            CubeAccess full = ((io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache) level.getChunkSource())
                    .cc_getFullCubeNow(io.github.opencubicchunks.cc_core.api.CubePos.of(cubeX, cubeY, cubeZ));
            cube = full instanceof LevelCube levelCube ? levelCube : null;
        }
        if (cube == null && io.github.opencubicchunks.cubicchunks.world.level.CubeLoads.allowed()) {
            return this.cc_getCubeAt(blockPos);
        }
        return cube;
    }

    @WrapOperation(method = "getBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockState(Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState cc_replaceLevelChunkInGetBlockState(
            LevelChunk levelChunk, BlockPos blockPos, Operation<BlockState> original, @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            return levelCubeLocalRef.get().getBlockState(blockPos);
        }
        return original.call(levelChunk, blockPos);
    }

    // getBlockEntity
    // Replaces LevelChunk with a LevelCube to call getBlockEntity
    @Inject(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunkAt(Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/chunk/LevelChunk;"), cancellable = true)
    private void cc_replaceGetChunkAtInSetBlockEntity(BlockPos blockPos, CallbackInfoReturnable<BlockEntity> cir) {
        if (cc_isCubic) {
            cir.setReturnValue(this.cc_getCubeAt(blockPos).getBlockEntity(blockPos, LevelChunk.EntityCreationType.IMMEDIATE));
        }
    }

    // getFluidState
    // Replaces LevelChunk with a LevelCube to call getFluidState
    @WrapOperation(method = "getFluidState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getFluidState(Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState cc_replaceGetChunkAtInGetFluidState(LevelChunk levelChunk, BlockPos blockPos, Operation<FluidState> original) {
        if (cc_isCubic) {
            LevelCube cube = this.cc_readableCubeAt(blockPos);
            return cube == null ? net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState() : cube.getFluidState(blockPos);
        }
        return original.call(levelChunk, blockPos);
    }

    // setBlockEntity
    // Replaces LevelChunk with a LevelCube to call addAndRegisterBlockEntity
    @Inject(method = "setBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunkAt(Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/chunk/LevelChunk;"), cancellable = true)
    private void cc_replaceLevelChunkInSetBlockEntity(BlockEntity blockEntity, CallbackInfo ci, @Local(ordinal = 0) BlockPos blockPos) {
        if (cc_isCubic) {
            this.cc_getCubeAt(blockPos).addAndRegisterBlockEntity(blockEntity);
            ci.cancel();
        }
    }

    // removeBlockEntity
    // Replaces LevelChunk with a LevelCube to call removeBlockEntity, needs a local ref to do so
    @WrapOperation(method = "removeBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunkAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk cc_replaceGetChunkAtInRemoveBlockEntity(
            Level level, BlockPos pos, Operation<LevelChunk> original, @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            levelCubeLocalRef.set(cc_getCubeAt(pos));
            return null;
        }
        return original.call(level, pos);
    }

    @WrapOperation(method = "removeBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"))
    private void cc_replaceLevelChunkInRemoveBlockEntity(
            LevelChunk levelChunk, BlockPos pos, Operation<Void> original, @Share("levelCube") LocalRef<LevelCube> levelCubeLocalRef
    ) {
        if (cc_isCubic) {
            levelCubeLocalRef.get().removeBlockEntity(pos);
        } else {
            original.call(levelChunk, pos);
        }
    }

    // isLoaded
    // Replaces ChunkSource with a CubeSource to call hasCube
    @WrapOperation(method = "isLoaded", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkSource;hasChunk(II)Z"))
    private boolean cc_replaceHasChunkInIsLoaded(ChunkSource chunkSource, int x, int z, Operation<Boolean> original, BlockPos blockPos) {
        if (cc_isCubic) {
            return ((CubeSource) chunkSource).cc_hasCube(Coords.blockToCube(blockPos.getX()), Coords.blockToCube(blockPos.getY()),
                    Coords.blockToCube(blockPos.getZ()));
        }
        return false;
    }

    // loadedAndEntityCanStandOnFace
    // Uses an inject here since the entire second half of the method needs to be replaced anyways
    @Inject(method = "loadedAndEntityCanStandOnFace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"), cancellable = true)
    private void cc_replaceGetChunkAtInLoadedAndEntityCanStandOnFace(
            BlockPos blockPos, Entity entity, Direction direction, CallbackInfoReturnable<Boolean> cir
    ) {
        if (cc_isCubic) {
            CubeAccess cubeAccess = this.cc_getCube(Coords.blockToCube(blockPos.getX()), Coords.blockToCube(blockPos.getY()),
                    Coords.blockToCube(blockPos.getZ()), ChunkStatus.FULL, false);
            cir.setReturnValue(
                    cubeAccess == null ? false : cubeAccess.getBlockState(blockPos).entityCanStandOnFace(this, blockPos, entity, direction));
        }
    }

    // blockEntityChanged
    // This function is small enough that we can just replace it entirely
    @Inject(method = "blockEntityChanged", at = @At(value = "HEAD"), cancellable = true)
    private void cc_replaceBlockEntityChanged(BlockPos blockPos, CallbackInfo ci) {
        if (cc_isCubic) {
            if (this.cc_hasCubeAt(blockPos)) {
                this.cc_getCubeAt(blockPos).markUnsaved();
            }
            ci.cancel();
        }
    }

    // TODO: Phase 3 low priority: Add a method to modify isOutsideSpawnableHeight to respect the limits of the packing for CloPos
}
