package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.ticks.CubicLevelTicks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import io.github.opencubicchunks.cubicchunks.world.level.entity.CubicEntitySections;
import java.util.List;
import java.util.concurrent.Executor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddTransformToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.MixinLevel;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.server.level.CubicInhabitedTime;
import io.github.opencubicchunks.cubicchunks.server.level.CubicServerLevel;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.CubicRandomTicks;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.Visibility;
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

    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;
    @Shadow @Final private LevelTicks<Block> blockTicks;
    @Shadow @Final private LevelTicks<Fluid> fluidTicks;

    /** A cubic level's entities are tracked and ticked cube by cube (see CubicEntitySections). */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cc_cubicEntities(
            MinecraftServer server, Executor dispatcher, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData,
            ResourceKey dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List customSpawners, boolean tickTime,
            CallbackInfo ci
    ) {
        if (cc_isCubic) {
            ((CubicEntitySections.Manager) this.entityManager).cc_makeCubic();
            ((CubicLevelTicks<?>) this.blockTicks).cc_makeCubic(this::cc_isCubeTickingWithEntitiesLoaded);
            ((CubicLevelTicks<?>) this.fluidTicks).cc_makeCubic(this::cc_isCubeTickingWithEntitiesLoaded);
        }
    }


    /** Regional difficulty reads the time players spent near the cube at the position (see CubicInhabitedTime). */
    @WrapOperation(method = "getCurrentDifficultyAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getInhabitedTime()J"))
    private long cc_cubeInhabitedTime(ChunkAccess chunk, Operation<Long> original, @Local(argsOnly = true) BlockPos pos) {
        return cc_isCubic ? CubicInhabitedTime.at((ServerLevel) (Object) this, pos) : original.call(chunk);
    }

    /** As vanilla's isPositionTickingWithEntitiesLoaded, for a cube: in block-ticking range, and its columns' entities loaded. */
    private boolean cc_isCubeTickingWithEntitiesLoaded(long cubeKey) {
        CubePos cubePos = CubePos.from(cubeKey);
        if (!((ServerCubeCache) this.chunkSource).cc_isCubeBlockTicking(cubePos)) {
            return false;
        }
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                if (!this.entityManager.areEntitiesLoaded(ChunkPos.pack(Coords.cubeToSection(cubePos.getX(), dx), Coords.cubeToSection(cubePos.getZ(), dz)))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override public void cc_onCubeFullStatusChange(CubePos cubePos, FullChunkStatus status) {
        ((CubicEntitySections.Manager) this.entityManager).cc_updateCubeStatus(cubePos, Visibility.fromFullChunkStatus(status));
        this.cc_checkPointsOfInterest(cubePos, status);
    }

    @org.spongepowered.asm.mixin.Unique private final it.unimi.dsi.fastutil.longs.LongSet cc_poiCheckedCubes = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    /**
     * As vanilla does for a chunk as it loads (SerializableChunkData.read): the points of interest of a cube that becomes full are made sure
     * of from its blocks. A generated cube's blocks were written without them, and a world from before they were kept for cubes has none.
     */
    @org.spongepowered.asm.mixin.Unique
    private void cc_checkPointsOfInterest(CubePos cubePos, FullChunkStatus status) {
        long key = cubePos.asLong();
        if (!status.isOrAfter(FullChunkStatus.FULL)) {
            this.cc_poiCheckedCubes.remove(key);
            return;
        }
        if (!this.cc_poiCheckedCubes.add(key)) {
            return;
        }
        io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess cube = this.cc_getCubeSource().cc_getFullCubeNow(cubePos);
        if (cube == null) {
            return;
        }
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = cube.getSections();
        net.minecraft.world.entity.ai.village.poi.PoiManager poi = ((ServerLevel) (Object) this).getPoiManager();
        for (int i = 0; i < sections.length; i++) {
            if (!sections[i].hasOnlyAir() && sections[i].maybeHas(net.minecraft.world.entity.ai.village.poi.PoiTypes::hasPoi)) {
                poi.checkConsistencyWithBlocks(io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSections.sectionPosOf(cubePos, i), sections[i]);
            }
        }
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
        CubicRandomTicks.tickCube((ServerLevel) (Object) this, levelCube, randomTickSpeed);
    }

    /**
     * A cube gives tickPrecipitation and findLightningTargetAround the surface it found itself (see CubicRandomTicks and CubicThunder); a cubic
     * column keeps no heightmap to look it up in.
     */
    @WrapOperation(method = { "tickPrecipitation", "findLightningTargetAround" }, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos cc_cubeSurface(ServerLevel level, Heightmap.Types type, BlockPos pos, Operation<BlockPos> original) {
        return cc_isCubic ? pos : original.call(level, type, pos);
    }

    /**
     * Vanilla's lightning rods draw strikes only from the top of their column (WORLD_SURFACE heightmap); in a cubic level, a rod whose top
     * sees the sky.
     */
    @WrapOperation(method = "lambda$findLightningRod$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int cc_rodOnTop(ServerLevel level, Heightmap.Types type, int x, int z, Operation<Integer> original, @Local(argsOnly = true) BlockPos rodPos) {
        if (!cc_isCubic) {
            return original.call(level, type, x, z);
        }
        return level.canSeeSky(rodPos.above()) ? rodPos.getY() + 1 : Integer.MIN_VALUE;
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
