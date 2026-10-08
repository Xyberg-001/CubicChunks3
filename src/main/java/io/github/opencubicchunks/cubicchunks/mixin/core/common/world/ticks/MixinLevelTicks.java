package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.ticks;

import java.util.function.BiConsumer;
import java.util.function.LongPredicate;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.ticks.CubicLevelTicks;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See {@link CubicLevelTicks}: the same bookkeeping as vanilla's, keyed by CubePos instead of ChunkPos in a cubic level. */
@Mixin(LevelTicks.class)
public abstract class MixinLevelTicks<T> implements CubicLevelTicks<T> {
    @Shadow @Final private Long2ObjectMap<LevelChunkTicks<T>> allContainers;
    @Shadow @Final private Long2LongMap nextTickForContainer;
    @Shadow @Final private BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> chunkScheduleUpdater;

    @Unique private boolean cc_cubic;
    @Unique private LongPredicate cc_cubeTickCheck;

    @Override public void cc_makeCubic(LongPredicate cubeTickCheck) {
        this.cc_cubic = true;
        this.cc_cubeTickCheck = cubeTickCheck;
    }

    /** As vanilla's addContainer. */
    @Override public void cc_addContainer(CubePos pos, LevelChunkTicks<T> container) {
        long key = pos.asLong();
        this.allContainers.put(key, container);
        ScheduledTick<T> nextTick = container.peek();
        if (nextTick != null) {
            this.nextTickForContainer.put(key, nextTick.triggerTick());
        }
        container.setOnTickAdded(this.chunkScheduleUpdater);
    }

    /** As vanilla's removeContainer. */
    @Override public void cc_removeContainer(CubePos pos) {
        long key = pos.asLong();
        LevelChunkTicks<T> removed = this.allContainers.remove(key);
        this.nextTickForContainer.remove(key);
        if (removed != null) {
            removed.setOnTickAdded(null);
        }
    }

    @Unique private static long cc_cubeKey(BlockPos pos) {
        return CubePos.asLong(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
    }

    /** A column's container would sit beside the cubes' under a key of another kind; its column holds no blocks to tick anyway. */
    @Inject(method = "addContainer", at = @At("HEAD"), cancellable = true)
    private void cc_noColumnContainer(ChunkPos pos, LevelChunkTicks<T> container, CallbackInfo ci) {
        if (this.cc_cubic) {
            ci.cancel();
        }
    }

    @Inject(method = "removeContainer", at = @At("HEAD"), cancellable = true)
    private void cc_noColumnContainerToRemove(ChunkPos pos, CallbackInfo ci) {
        if (this.cc_cubic) {
            ci.cancel();
        }
    }

    @WrapOperation(method = { "schedule", "updateContainerScheduling", "hasScheduledTick" }, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;pack(Lnet/minecraft/core/BlockPos;)J"))
    private long cc_containerOf(BlockPos pos, Operation<Long> original) {
        return this.cc_cubic ? cc_cubeKey(pos) : original.call(pos);
    }

    @WrapOperation(method = "sortContainersToTick", at = @At(value = "INVOKE", target = "Ljava/util/function/LongPredicate;test(J)Z"))
    private boolean cc_cubeTicks(LongPredicate tickCheck, long key, Operation<Boolean> original) {
        return this.cc_cubic ? this.cc_cubeTickCheck.test(key) : original.call(tickCheck, key);
    }

    /** The cubes a box spans, for clearArea and copyAreaFrom (vanilla: the chunks). */
    @Inject(method = "forContainersInArea", at = @At("HEAD"), cancellable = true)
    private void cc_cubesInArea(BoundingBox box, LevelTicks.PosAndContainerConsumer<T> output, CallbackInfo ci) {
        if (!this.cc_cubic) {
            return;
        }
        ci.cancel();
        for (int x = Coords.blockToCube(box.minX()); x <= Coords.blockToCube(box.maxX()); x++) {
            for (int y = Coords.blockToCube(box.minY()); y <= Coords.blockToCube(box.maxY()); y++) {
                for (int z = Coords.blockToCube(box.minZ()); z <= Coords.blockToCube(box.maxZ()); z++) {
                    long key = CubePos.asLong(x, y, z);
                    LevelChunkTicks<T> container = this.allContainers.get(key);
                    if (container != null) {
                        output.accept(key, container);
                    }
                }
            }
        }
    }
}
