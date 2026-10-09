package io.github.opencubicchunks.cubicchunks.mixin.core.common.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.world.SpawnPlaceFinder;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.server.level.progress.CubicChunkLoadStatusView;
import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The initial spawn in a cubic world. 26.3 takes the spawn chunk from the generator's origin and its height from the generator's spawn
 * height (falling back to the heightmap below the world's floor); in a cubic world the height comes from SpawnPlaceFinder, searching the
 * cubes, and the floor it is checked against is the cubic one. (26.x has no spawn chunks, so nothing loads a spawn area to count.) Whether
 * the world is cubic at all is settled as the server creates its levels.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    /** Whether the world is cubic, and its heights, are settled before its levels are made (see CubicWorldSettings). */
    @Inject(method = "createLevels", at = @At("HEAD"))
    private void cc_decideWorldSettings(CallbackInfo ci) {
        CubicWorldSettings.decideForServer(this.storageSource);
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void cc_forgetWorldSettings(CallbackInfo ci) {
        CubicWorldSettings.onServerStopped();
    }

    @WrapOperation(method = "setInitialSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getSpawnHeight(Lnet/minecraft/world/level/LevelHeightAccessor;)I"))
    private static int cc_spawnHeightFromCubes(
            ChunkGenerator generator, LevelHeightAccessor heightAccessor, Operation<Integer> original, @Local(argsOnly = true) ServerLevel level,
            @Local ChunkPos spawnChunk
    ) {
        if (!((CanBeCubic) level).cc_isCubic()) {
            return original.call(generator, heightAccessor);
        }
        // the search reads cubes that are not loaded yet, which it loads (see CubeLoads)
        BlockPos top = io.github.opencubicchunks.cubicchunks.world.level.CubeLoads.allowing(() -> SpawnPlaceFinder.getTopBlockBisect(level,
                spawnChunk.getWorldPosition().offset(8, 0, 8), false,
                pos -> level.getBlockState(pos).is(BlockTags.VALID_SPAWN), pos -> level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()));
        return top != null ? top.getY() : level.getSeaLevel() + 1; // vanilla's default
    }

    @WrapOperation(method = "setInitialSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinY()I"))
    private static int cc_spawnFloor(ServerLevel level, Operation<Integer> original) {
        return ((CanBeCubic) level).cc_isCubic() ? CubicHeight.minY(level) : original.call(level);
    }

    /** The loading screen's view of chunk loading also shows the cubes of a cubic level (see CubicChunkLoadStatusView). */
    @Inject(method = "createChunkLoadStatusView", at = @At("RETURN"), cancellable = true)
    private void cc_cubicLoadStatusView(int radius, CallbackInfoReturnable<ChunkLoadStatusView> cir) {
        cir.setReturnValue(new CubicChunkLoadStatusView((MinecraftServer) (Object) this, cir.getReturnValue()));
    }
}
