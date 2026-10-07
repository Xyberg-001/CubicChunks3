package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import java.util.List;
import java.util.concurrent.Executor;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddTransformToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.MixinLevel;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.server.level.CubicServerLevel;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Dasm(value = ChunkToCloSet.class, target = @Ref(ServerLevel.class))
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel extends MixinLevel implements CubicServerLevel {
    @Shadow @Final private ServerChunkCache chunkSource;

    @Inject(method = "<init>", at = @At("CTOR_HEAD"))
    private void cc_onInit(
            MinecraftServer server, Executor dispatcher, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData,
            ResourceKey dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List customSpawners, boolean tickTime,
            CallbackInfo ci
    ) {
        // TODO conditionally mark as cubic based on dimension, config, level data, etc
    }

    @Override public ServerCubeCache cc_getCubeSource() {
        return ((ServerCubeCache) this.chunkSource);
    }

    @AddTransformToSets(ChunkToCloSet.ServerLevel_redirects.class)
    @TransformFromMethod("startTickingChunk(Lnet/minecraft/world/level/chunk/LevelChunk;)V")
    public native void cc_startTickingClo(LevelClo chunk);

    @AddMethodToSets(containers = ChunkToCloSet.ServerLevel_redirects.class, method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V")
    public void cc_tickClo(LevelClo levelClo, int randomTickSpeed) {
        if (levelClo instanceof LevelCube levelCube) {
            cc_tickCube(levelCube, randomTickSpeed);
        } else {
            // TODO (P2) chunk ticking for anything that still needs to happen on chunks (probably just the forge event for mods?)
        }
    }

    @AddMethodToSets(containers = ChunkToCubeSet.ServerLevel_redirects.class, method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V")
    public void cc_tickCube(LevelCube levelCube, int randomTickSpeed) {
        // TODO (P2) cube ticking
    }

    // TODO (P2) waitForChunkAndEntities

    // TODO: comments below don't account for 1.20.4->1.21.5 changes; will need to check for other methods that need CC changes

    // TODO: phase 3 - isNaturalSpawningAllowed

    // TODO: phase 3 - invalidateCapabilites, neoforge api

    // TODO: phase 4 - setCubeForced - new function

    // TODO: saveDebugReport - mixins, debug only, low priority, if we really really really really need it

    // TODO: phase 2 - isPositionEntityTicking - mixin


    /**
     * Local difficulty from the cube at the position (vanilla reads the chunk's inhabited time); replaced whole, as in 1.21.6 where it was
     * Level's. 26.3 moved it to ServerLevel, with the moon's brightness at the position and the overworld clock.
     */
    @Inject(method = "getCurrentDifficultyAt", at = @At(value = "HEAD"), cancellable = true)
    private void cc_replaceGetCurrentDifficultyAt(BlockPos blockPos, CallbackInfoReturnable<DifficultyInstance> cir) {
        if (cc_isCubic) {
            ServerLevel self = (ServerLevel) (Object) this;
            long inhabitedTime = 0L;
            float moonBrightness = 0.0F;
            if (this.cc_hasCubeAt(blockPos)) {
                moonBrightness = self.getMoonBrightness(blockPos);
                inhabitedTime = this.cc_getCubeAt(blockPos).getInhabitedTime();
            }
            cir.setReturnValue(new DifficultyInstance(self.getDifficulty(), self.getOverworldClockTime(), inhabitedTime, moonBrightness));
        }
    }
}
