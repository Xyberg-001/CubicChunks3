package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cubicchunks.server.level.CubicServerLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixer;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddFieldToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddTransformToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.server.level.CubeYRange;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.chunk.storage.MixinChunkStorage;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.GlobalSet;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.SectionPosToCubeSet;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundSetCubeCacheCenterPacket;
import io.github.opencubicchunks.cubicchunks.server.level.CCServerPlayer;
import io.github.opencubicchunks.cubicchunks.server.level.CloGenerationTask;
import io.github.opencubicchunks.cubicchunks.server.level.CloHolder;
import io.github.opencubicchunks.cubicchunks.server.level.CloTrackingView;
import io.github.opencubicchunks.cubicchunks.server.level.CubeHolder;
import io.github.opencubicchunks.cubicchunks.server.level.CubicChunkMap;
import io.github.opencubicchunks.cubicchunks.server.level.GeneratingCubeMap;
import io.github.opencubicchunks.cubicchunks.server.level.GenerationCloHolder;
import io.github.opencubicchunks.cubicchunks.util.StaticCache3D;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.ImposterProtoClo;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.status.CubeStep;
import io.github.opencubicchunks.cubicchunks.world.level.entity.CloStatusUpdateListener;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLight;
import io.github.opencubicchunks.cubicchunks.world.storage.ColumnSerializer;
import net.minecraft.world.level.chunk.ChunkAccess;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeSerializer;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeStorage;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.ReportedException;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.debug.LevelDebugSynchronizers;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla {@link ChunkMap} class stores all loaded chunks for a world and handles loading and unloading them, including dependencies on
 * neighboring chunks. This mixin adds cubic chunks equivalents for methods where necessary, to allow ChunkMap to work with CLOs (i.e. both chunks and
 * cubes).
 */
@Dasm(value = ChunkToCloSet.class, target = @Ref(ChunkMap.class))
@Mixin(ChunkMap.class)
public abstract class MixinChunkMap extends MixinChunkStorage implements GeneratingCubeMap, CubicChunkMap, CubeHolder.PlayerProvider {
    @Shadow public abstract ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details);

    @Shadow protected abstract ChunkHolder getUpdatingChunkIfPresent(long aLong);

    @Shadow @Final ServerLevel level;
    @Shadow @Final private ChunkMap.DistanceManager distanceManager;
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;
    @Shadow @Final private Long2ByteMap chunkTypeCache;
    @Shadow @Final private AtomicInteger activeChunkWrites;
    @Shadow @Final private PoiManager poiManager;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow @Final private Long2LongMap nextChunkSaveTime;
    @Shadow @Final private Queue<Runnable> unloadQueue;

    @Shadow @Final private static CompletableFuture<ChunkResult<List<CloAccess>>> UNLOADED_CHUNK_LIST_FUTURE;
    @Shadow @Final private static ChunkResult<List<CloAccess>> UNLOADED_CHUNK_LIST_RESULT;

    @Shadow private static double euclideanDistanceSquared(ChunkPos chunkPos, Vec3 pos) {
        throw new IllegalStateException();
    }

    @AddFieldToSets(containers = ChunkToCloSet.ChunkMap_redirects.class, field = "chunkStatusListener:Lnet/minecraft/world/level/entity/ChunkStatusUpdateListener;")
    private CloStatusUpdateListener cc_cloStatusListener;
    /** Where this dimension's cubes are saved; null in a world that is not cubic. */
    private @Nullable CubeStorage cc_cubeStorage;
    private final AtomicInteger cc_cubesLoaded = new AtomicInteger();
    private final AtomicInteger cc_cubesCreated = new AtomicInteger();
    private final AtomicInteger cc_cubesSaved = new AtomicInteger();
    private final AtomicInteger cc_cubesUnloaded = new AtomicInteger();

    // TODO once we can target non-return locations in constructors, do this when the vanilla field is set
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cc_onInit(
            ServerLevel level, LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer fixerUpper,
            StructureTemplateManager structureManager, Executor dispatcher, BlockableEventLoop mainThreadExecutor, LightChunkGetter lightChunk,
            ChunkGenerator generator, ChunkStatusUpdateListener chunkStatusListener, TicketStorage ticketStorage, int serverViewDistance,
            boolean sync, CallbackInfo ci
    ) {
        if (((CanBeCubic) level).cc_isCubic()) {
            // vanilla's listener is the level's entity manager (updateChunkStatus); cubes report to it cube by cube (see CubicEntitySections)
            cc_cloStatusListener = (cloPos, fullChunkStatus) -> {
                if (cloPos.isCube()) {
                    ((CubicServerLevel) level).cc_onCubeFullStatusChange(cloPos.cubePos(), fullChunkStatus);
                } else {
                    chunkStatusListener.onChunkStatusChange(cloPos.chunkPos(), fullChunkStatus);
                }
            };
            ((MarkableAsCubic) distanceManager).cc_setCubic();
            ((CubeYRange) distanceManager).cc_setCubeYRange(Coords.blockToCube(CubicHeight.minY(level)), Coords.blockToCube(CubicHeight.maxY(level)));
            cc_cubeStorage = new CubeStorage(levelStorageAccess.getDimensionPath(level.dimension()), level.dimension().identifier().toString());
            // points of interest cube by cube, in a cube store of their own (see MixinSectionStorage)
            ((io.github.opencubicchunks.cubicchunks.world.level.CubicSectionStorage) poiManager).cc_setCubeStorage(new CubeStorage(
                    levelStorageAccess.getDimensionPath(level.dimension()).resolve("poi"), level.dimension().identifier() + " poi"));
        }
    }


    @Override public java.util.concurrent.Executor cc_mainThreadExecutor() {
        return this.mainThreadExecutor;
    }

    @Override public CompletableFuture<Optional<CompoundTag>> cc_readSavedCube(CubePos cubePos) {
        return cc_cubeStorage == null ? CompletableFuture.completedFuture(Optional.empty()) : cc_cubeStorage.read(cubePos);
    }

    @Override public void cc_markCubeUnsaved(CubePos cubePos) {
        cc_setCloUnsaved(CloPos.cube(cubePos));
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void cc_onClose(CallbackInfo ci) throws IOException {
        if (cc_cubeStorage != null) {
            cc_cubeStorage.close();
            CubicChunks.LOGGER.info("Cubes in {}: {} loaded from disk, {} new, {} saves, {} unloaded", level.dimension().identifier(),
                    cc_cubesLoaded.get(), cc_cubesCreated.get(), cc_cubesSaved.get(), cc_cubesUnloaded.get());
        }
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("setChunkUnsaved(Lnet/minecraft/world/level/ChunkPos;)V")
    private native void cc_setCloUnsaved(CloPos cloPos);

    /**
     * Returns the squared distance to the center of the cube.
     */
    @AddMethodToSets(containers = ChunkToCloSet.ChunkMap_redirects.class, method = "euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/phys/Vec3;)D")
    private static double cc_euclideanDistanceSquared(CloPos cloPos, Vec3 vec3) {
        if (cloPos.isChunk()) {
            // FIXME we shouldn't be getting euclidean distance for chunks, as this doesn't make sense in context
            return euclideanDistanceSquared(cloPos.chunkPos(), vec3);
//            throw new UnsupportedOperationException("Should not call euclideanDistanceSquared with a chunk position");
        }
        double cubeCenterX = Coords.cubeToCenterBlock(cloPos.getX());
        double cubeCenterY = Coords.cubeToCenterBlock(cloPos.getX());
        double cubeCenterZ = Coords.cubeToCenterBlock(cloPos.getX());
        double dx = cubeCenterX - vec3.x();
        double dy = cubeCenterY - vec3.y();
        double dz = cubeCenterZ - vec3.z();
        return dx * dx + dy * dy + dz * dz;
    }

    // TODO make vanilla isChunkTracked/isChunkOnTrackedBorder fail in cubic world

    // These methods are not copied due to taking 3 ints instead of 2
    @Override public boolean cc_isChunkTracked(ServerPlayer player, int x, int y, int z) {
        return ((CCServerPlayer) player).cc_getCloTrackingView().cc_contains(x, y, z)
                // TODO this requires PlayerChunkSender to accept Clo longs
                && !player.connection.chunkSender.isPending(CloPos.cubeAsLong(x, y, z));
    }

    private boolean cc_isChunkOnTrackedBorder(ServerPlayer player, int x, int y, int z) {
        if (this.cc_isChunkTracked(player, x, y, z)) {
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dy = -1; dy <= 1; ++dy) {
                        if ((dx != 0 || dz != 0 || dy != 0) && !this.cc_isChunkTracked(player, x + dx, y + dy, z + dz)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // TODO getChunkDebugData - low prio

    // region [cc_getChunkRangeFuture dasm + mixin]
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("getChunkRangeFuture(Lnet/minecraft/server/level/ChunkHolder;ILjava/util/function/IntFunction;)Ljava/util/concurrent/CompletableFuture;")
    private native CompletableFuture<ChunkResult<List<CloAccess>>> cc_getChunkRangeFuture(
            ChunkHolder cloHolder, int radius, IntFunction<ChunkStatus> statusByRadius
    );

    // TODO this could be substantially improved probably hopefully

    /**
     * Cubes require different adjacency logic compared to Chunks
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity") // <-- TODO this method is just a bit of a disaster
    @Dynamic @Inject(method = "cc_getChunkRangeFuture", at = @At("HEAD"), cancellable = true)
    private void cc_onGetChunkRangeFuture(
            ChunkHolder cloHolder, int radius, IntFunction<ChunkStatus> statusByRadius,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<List<CloAccess>>>> cir
    ) {
        // Note that statusByRadius sometimes isn't actually correct for cubes beyond the first few steps, but getChunkRangeFuture is only called with
        // parameters for which it's correct within the radius
        CloPos pos = ((CloHolder) cloHolder).cc_getCloPos();
        if (!pos.isCube()) {
            return;
        }
        // The vanilla method has an early exit for radius=0 here; this is not valid for cubes because even if radius=0 we still depend on chunks that
        // neighbor the cube
        int cubeDiameter = radius * 2 + 1;
        int chunkDiameter = cubeDiameter * CubicConstants.DIAMETER_IN_SECTIONS;
        int futureCount = cubeDiameter * cubeDiameter * cubeDiameter + chunkDiameter * chunkDiameter;
        List<CompletableFuture<ChunkResult<CloAccess>>> futures = new ArrayList<>(futureCount);
        int middleCubeIndex = -1;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                // We want the chunks intersecting this column of cubes to be loaded at the maximum level of any of those cubes;
                // this occurs when dy=0, so we only consider x/z distance
                int chunkDistance = Math.max(Math.abs(dz), Math.abs(dx));
                for (int sectionZ = 0; sectionZ < CubicConstants.DIAMETER_IN_SECTIONS; sectionZ++) {
                    for (int sectionX = 0; sectionX < CubicConstants.DIAMETER_IN_SECTIONS; sectionX++) {
                        ChunkHolder holder = this.getUpdatingChunkIfPresent(
                                CloPos.chunkAsLong(Coords.cubeToSection(pos.getX() + dx, sectionX), Coords.cubeToSection(pos.getZ() + dz, sectionZ)));
                        if (holder == null) {
                            cir.setReturnValue(UNLOADED_CHUNK_LIST_FUTURE);
                            return;
                        }
                        ChunkStatus expectedStatus = statusByRadius.apply(chunkDistance);
                        futures.add((CompletableFuture<ChunkResult<CloAccess>>) (Object) holder.scheduleChunkGenerationTask(expectedStatus,
                                (ChunkMap) (Object) this));
                    }
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        middleCubeIndex = futures.size();
                    }
                    ChunkHolder holder = this.getUpdatingChunkIfPresent(CloPos.cubeAsLong(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                    if (holder == null) {
                        cir.setReturnValue(UNLOADED_CHUNK_LIST_FUTURE);
                        return;
                    }
                    ChunkStatus expectedStatus = statusByRadius.apply(Math.max(chunkDistance, Math.abs(dy)));
                    futures.add((CompletableFuture<ChunkResult<CloAccess>>) (Object) holder.scheduleChunkGenerationTask(expectedStatus,
                            (ChunkMap) (Object) this));
                }
            }
        }

        // Vanilla expects that the center chunk is in the middle of the list; this is not the case for cubes, so we manually swap the center cube to
        // the middle
        // - this is a """temporary""" approach, that we may or may not actually fix later.
        Collections.swap(futures, middleCubeIndex, futures.size() / 2);

        cir.setReturnValue(Util.sequence(futures).thenApply(resultList -> {
            List<CloAccess> outputList = new ArrayList<>(resultList.size());

            for (final ChunkResult<CloAccess> chunkResult : resultList) {
                if (chunkResult == null) {
                    throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"),
                            "n/a");
                }

                CloAccess cloAccess = chunkResult.orElse(null);
                if (cloAccess == null) {
                    return UNLOADED_CHUNK_LIST_RESULT;
                }

                outputList.add(cloAccess);
            }

            return ChunkResult.of(outputList);
        }));
    }
    // endregion

    // region [cc_updateCubeScheduling dasm + mixin]
    @AddTransformToSets(ChunkToCubeSet.ChunkMap_redirects.class)
    @TransformFromMethod(useRedirectSets = ChunkToCubeSet.class, value = "updateChunkScheduling(JILnet/minecraft/server/level/ChunkHolder;I)Lnet/minecraft/server/level/ChunkHolder;")
    public native @Nullable ChunkHolder cc_updateCubeScheduling(long cubePos, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    @Inject(method = "updateChunkScheduling", at = @At("HEAD"), cancellable = true)
    private void cc_onUpdateChunkScheduling(long cloPos, int newLevel, ChunkHolder holder, int oldLevel, CallbackInfoReturnable<ChunkHolder> cir) {
        if (((CanBeCubic) level).cc_isCubic() && CloPos.isCube(cloPos)) {
            cir.setReturnValue(cc_updateCubeScheduling(cloPos, newLevel, holder, oldLevel));
        }
    }
    // endregion

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("onLevelChange(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/IntSupplier;ILjava/util/function/IntConsumer;)V")
    private native void cc_onLevelChange(CloPos cloPos, IntSupplier intsupplier, int i, IntConsumer intconsumer);

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkMap_redirects.class, method = "onLevelChange(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/IntSupplier;ILjava/util/function/IntConsumer;)V")
    public void cc_onCubeLevelChange(CubePos cubePos, IntSupplier queueLevelGetter, int ticketLevel, IntConsumer queueLevelSetter) {
        cc_onLevelChange(CloPos.cube(cubePos), queueLevelGetter, ticketLevel, queueLevelSetter);
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("saveAllChunks(Z)V")
    public native void cc_saveAllChunks(boolean flush);

    @Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
    private void cc_onSaveAllChunks(boolean flush, CallbackInfo ci) {
        if (((CanBeCubic) level).cc_isCubic()) {
            cc_saveAllChunks(flush);
            if (flush && cc_cubeStorage != null) {
                cc_cubeStorage.synchronize();
            }
            ci.cancel();
        }
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("saveChunksEagerly(Ljava/util/function/BooleanSupplier;)V")
    private native void cc_saveClosEagerly(BooleanSupplier hasMoreTime);

    /**
     * Unloads a cube or column once it has no ticket left: the cubic counterpart of vanilla's scheduleUnload, written out rather than
     * copied. A cube is saved, then its block entities and ticks leave the level; a column is only released (in a cubic world it holds no
     * blocks, and columns are not saved yet). Light is left alone: cubes have none yet. Runs from the unload queue, after any save
     * already under way for it.
     */
    /** As vanilla's scheduleUnload does for a chunk (lightEngine.updateChunkStatus): an unloaded cube's light goes. */
    private void cc_unlight(CubeAccess cube) {
        CubicLight light = ((CubeSource) level.getChunkSource()).cc_cubicLight();
        if (light != null) {
            light.onCubeUnloaded(cube);
            level.getChunkSource().getLightEngine().tryScheduleUpdate();
        }
    }

    @AddMethodToSets(containers = ChunkToCloSet.ChunkMap_redirects.class, method = "scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V")
    private void cc_scheduleUnload(long cloPos, ChunkHolder holder) {
        CompletableFuture<?> saveSync = holder.getSaveSyncFuture();
        saveSync.thenRunAsync(() -> {
            if (holder.getSaveSyncFuture() != saveSync) {
                cc_scheduleUnload(cloPos, holder);
                return;
            }
            CloAccess clo = ((GenerationCloHolder) holder).cc_getLatestClo();
            if (!pendingUnloads.remove(cloPos, holder) || clo == null) {
                return;
            }
            if (clo instanceof LevelCube cube) {
                cube.setLoaded(false);
                cc_save(cube);
                cube.clearAllBlockEntities();
                cube.unregisterTickContainerFromLevel(level);
                cc_unlight(cube);
                cc_cubesUnloaded.incrementAndGet();
            } else if (clo instanceof CubeAccess cube) {
                cc_save(cube);
                cc_unlight(cube);
                cc_cubesUnloaded.incrementAndGet();
            } else if ((Object) clo instanceof LevelChunk column) {
                cc_save(clo); // its structures, if it keeps any (see ColumnSerializer)
                column.setLoaded(false);
                level.unload(column);
            } else {
                cc_save(clo); // a column still generating: its structures, if it has them already
            }
            nextChunkSaveTime.remove(cloPos);
            // only once saved: an unfinished cube's save looks here to learn whether a finished one is on disk (else it reads it back)
            chunkTypeCache.remove(cloPos);
        }, unloadQueue::add).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                CubicChunks.LOGGER.error("Failed to unload {}", CloPos.fromLong(cloPos), throwable);
            }
        });
    }

    // region [cc_scheduleChunkLoad dasm + mixin]
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("scheduleChunkLoad(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;")
    private native CompletableFuture<CloAccess> cc_scheduleChunkLoad(CloPos cloPos);

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkMap_redirects.class, method = "scheduleChunkLoad(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<CloAccess> cc_scheduleChunkLoad(CubePos cubePos) {
        return cc_scheduleCubeLoad(cubePos);
    }

    /**
     * Loads a cube from disk, or makes an empty one to generate if it was never saved: the cubic counterpart of vanilla's
     * scheduleChunkLoad, written out rather than copied, as vanilla's goes through SerializableChunkData. Read on the cube storage's
     * thread, parsed on a background thread, built on the server thread.
     */
    private CompletableFuture<CloAccess> cc_scheduleCubeLoad(CubePos cubePos) {
        CloPos cloPos = CloPos.cube(cubePos);
        if (cc_cubeStorage == null) {
            return CompletableFuture.completedFuture(cc_createEmptyChunk(cloPos));
        }
        return cc_cubeStorage.read(cubePos)
                .thenApplyAsync(tag -> tag.map(t -> {
                    CubeSerializer.Parsed parsed = CubeSerializer.parse(level.registryAccess(), t);
                    if (parsed == null) {
                        CubicChunks.LOGGER.error("Cube file at {} is missing level data, skipping", cubePos);
                    }
                    return parsed;
                }), Util.backgroundExecutor().forName("parseCube"))
                .thenApplyAsync(parsed -> {
                    if (parsed.isPresent()) {
                        CubeAccess cube = parsed.get().read(level, cubePos);
                        cc_markPosition(cloPos, cube.getPersistedStatus().getChunkType());
                        cc_cubesLoaded.incrementAndGet();
                        return (CloAccess) cube;
                    }
                    cc_cubesCreated.incrementAndGet();
                    return cc_createEmptyChunk(cloPos);
                }, mainThreadExecutor)
                .exceptionallyAsync(throwable -> {
                    CubicChunks.LOGGER.error("Failed to load cube {}; generating it again", cubePos, throwable);
                    return cc_createEmptyChunk(cloPos);
                }, mainThreadExecutor);
    }

    /**
     * A cubic level's column: its structures from the column storage (see ColumnSerializer), or a new one; its blocks are its cubes'. Read
     * on the cube storage's thread, built on the server thread.
     */
    @Inject(method = "scheduleChunkLoad(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"),
            cancellable = true)
    private void cc_scheduleColumnLoad(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (cc_cubeStorage == null) {
            return;
        }
        CloPos cloPos = CloPos.chunk(pos);
        cir.setReturnValue(cc_cubeStorage.readColumn(pos.x(), pos.z()).thenApplyAsync(tag -> {
            ChunkAccess column = (ChunkAccess) cc_createEmptyChunk(cloPos);
            if (tag.isPresent() && column instanceof net.minecraft.world.level.chunk.ProtoChunk proto) {
                ColumnSerializer.read(level, tag.get(), proto);
            }
            return column;
        }, mainThreadExecutor).exceptionallyAsync(throwable -> {
            CubicChunks.LOGGER.error("Failed to load column {}; making it again", pos, throwable);
            return (ChunkAccess) cc_createEmptyChunk(cloPos);
        }, mainThreadExecutor));
    }

    /** Saves a cubic level's column if its cube generator places structures and it has its structure starts (see ColumnSerializer). */
    private boolean cc_saveColumn(ChunkAccess column) {
        if (!io.github.opencubicchunks.cubicchunks.api.CubicApi.usesStructures(level) || !ColumnSerializer.worthSaving(column) || !column.tryMarkSaved()) {
            return false;
        }
        ChunkPos pos = column.getPos();
        cc_cubeStorage.writeColumn(pos.x(), pos.z(), () -> ColumnSerializer.write(level, column));
        return true;
    }

    @Dynamic @Redirect(method = "cc_scheduleChunkLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;prefetch(Lio/github/opencubicchunks/cc_core/world/level/CloPos;)"
            + "Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> cc_onScheduleChunkLoad_poiManagerPreFetch(PoiManager instance, CloPos cloPos) {
        // TODO (P2) save/load - PoiManager
        return CompletableFuture.completedFuture(null);
    }
    // endregion

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("handleChunkLoadFailure(Ljava/lang/Throwable;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    private native ChunkResult<CloAccess> cc_handleChunkLoadFailure(Throwable exception, CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod(value = "createEmptyChunk(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    private native CloAccess cc_createEmptyChunk(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("markPositionReplaceable(Lnet/minecraft/world/level/ChunkPos;)V")
    private native void cc_markPositionReplaceable(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("markPosition(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/status/ChunkType;)B")
    private native byte cc_markPosition(CloPos cloPos, ChunkType chunkType);

    // region [cc_applyCubeStep dasm + mixin]
    @AddTransformToSets(ChunkToCubeSet.ChunkMap_redirects.class)
    @TransformFromMethod(useRedirectSets = ChunkToCubeSet.class, value = "applyStep(Lnet/minecraft/server/level/GenerationChunkHolder;Lnet/minecraft/world/level/chunk/status/ChunkStep;"
            + "Lnet/minecraft/util/StaticCache2D;)Ljava/util/concurrent/CompletableFuture;")
    public native CompletableFuture<CubeAccess> cc_applyCubeStep(
            GenerationChunkHolder generationchunkholder, CubeStep chunkstep, StaticCache3D<GenerationChunkHolder> cache
    );

    @Dynamic @Redirect(method = "cc_applyCubeStep", at = @At(value = "INVOKE", target = "Lio/github/opencubicchunks/cubicchunks/util/StaticCache3D;get(II)Ljava/lang/Object;"))
    private Object cc_onApplyCubeStep_staticCacheGet(StaticCache3D instance, int x, int z, @Local(ordinal = 0) CubePos cubePos) {
        return instance.get(cubePos.getX(), cubePos.getY(), cubePos.getZ());
    }
    // endregion

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("scheduleGenerationTask(Lnet/minecraft/world/level/chunk/status/ChunkStatus;Lnet/minecraft/world/level/ChunkPos;)"
            + "Lnet/minecraft/server/level/ChunkGenerationTask;")
    public native ChunkGenerationTask cc_scheduleGenerationTask(ChunkStatus chunkstatus, CloPos cloPos);

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkMap_redirects.class, method = "scheduleGenerationTask(Lnet/minecraft/world/level/chunk/status/ChunkStatus;Lnet/minecraft/world/level/ChunkPos;)"
            + "Lnet/minecraft/server/level/ChunkGenerationTask;")
    public ChunkGenerationTask cc_scheduleGenerationTask(ChunkStatus chunkstatus, CubePos cubePos) {
        return cc_scheduleGenerationTask(chunkstatus, CloPos.cube(cubePos));
    }

    // region [cc_runGenerationTask dasm + mixin]
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("runGenerationTask(Lnet/minecraft/server/level/ChunkGenerationTask;)V")
    private native void cc_runGenerationTask(ChunkGenerationTask chunkgenerationtask);

    // Delegate to the cube method for cubes
    @Inject(method = "runGenerationTask", at = @At("HEAD"), cancellable = true)
    private void cc_onVanillaRunGenerationTask(ChunkGenerationTask task, CallbackInfo ci) {
        if (((CloGenerationTask) task).cc_getCloPos().isCube()) {
            ci.cancel();
            cc_runGenerationTask(task);
        }
    }
    // endregion

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("prepareEntityTickingChunk(Lnet/minecraft/server/level/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;")
    public native CompletableFuture<ChunkResult<LevelClo>> cc_prepareEntityTickingChunk(ChunkHolder holder);

    @Inject(method = "prepareEntityTickingChunk", at = @At("HEAD"), cancellable = true)
    private void cc_onVanillaPrepareEntityTickingChunk(ChunkHolder chunk, CallbackInfoReturnable<CompletableFuture<ChunkResult<LevelClo>>> cir) {
        if (((CloHolder) chunk).cc_getCloPos().isCube()) {
            cir.setReturnValue(cc_prepareEntityTickingChunk(chunk));
        }
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("prepareTickingChunk(Lnet/minecraft/server/level/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;")
    public native CompletableFuture<ChunkResult<LevelClo>> cc_prepareTickingChunk(ChunkHolder holder);

    @Inject(method = "prepareTickingChunk", at = @At("HEAD"), cancellable = true)
    private void cc_onVanillaPrepareTickingChunk(ChunkHolder chunk, CallbackInfoReturnable<CompletableFuture<ChunkResult<LevelClo>>> cir) {
        if (((CloHolder) chunk).cc_getCloPos().isCube()) {
            cir.setReturnValue(cc_prepareTickingChunk(chunk));
        }
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("onChunkReadyToSend(Lnet/minecraft/server/level/ChunkHolder;Lnet/minecraft/world/level/chunk/LevelChunk;)V")
    private native void cc_onChunkReadyToSend(ChunkHolder chunkholder, LevelClo cloPos);

    // 26.3 registers a ready chunk's debug values (debug subscriptions, sent to clients that ask for them); cubes have none yet
    @Dynamic @Redirect(method = "cc_onChunkReadyToSend", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/debug/LevelDebugSynchronizers;registerChunk(Lio/github/opencubicchunks/cubicchunks/world/level/chunklike/LevelClo;)V"))
    private void cc_registerCloDebugValues(LevelDebugSynchronizers synchronizers, LevelClo clo) {
        if (clo instanceof LevelChunk chunk) {
            synchronizers.registerChunk(chunk);
        }
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("prepareAccessibleChunk(Lnet/minecraft/server/level/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;")
    public native CompletableFuture<ChunkResult<LevelClo>> cc_prepareAccessibleChunk(ChunkHolder holder);

    @Inject(method = "prepareAccessibleChunk", at = @At("HEAD"), cancellable = true)
    private void cc_onVanillaPrepareAccessibleChunk(ChunkHolder chunk, CallbackInfoReturnable<CompletableFuture<ChunkResult<LevelClo>>> cir) {
        if (((CloHolder) chunk).cc_getCloPos().isCube()) {
            cir.setReturnValue(cc_prepareAccessibleChunk(chunk));
        }
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("saveChunkIfNeeded(Lnet/minecraft/server/level/ChunkHolder;J)Z")
    private native boolean cc_saveChunkIfNeeded(ChunkHolder holder, long gameTime);

    /**
     * Saves a cube: the cubic counterpart of vanilla's save, written out rather than copied, as vanilla's goes through
     * SerializableChunkData (and asks for heightmaps and light that cubes do not have yet). Columns are not saved yet: in a cubic world
     * they hold no blocks.
     */
    @AddMethodToSets(containers = ChunkToCloSet.ChunkMap_redirects.class, method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z")
    private boolean cc_save(CloAccess cloAccess) {
        CloPos cloPos = cloAccess.cc_getCloPos();
        if (cc_cubeStorage == null) {
            return false;
        }
        if (!cloPos.isCube()) {
            return cc_saveColumn(cloAccess instanceof ImposterProtoClo imposter ? (ChunkAccess) imposter.cc_getWrappedClo() : (ChunkAccess) cloAccess);
        }
        // as vanilla flushes a chunk's points of interest as it saves the chunk
        ((io.github.opencubicchunks.cubicchunks.world.level.CubicSectionStorage) poiManager).cc_flushCube(cloPos.cubePos());
        CubeAccess cube = cloAccess instanceof ImposterProtoClo imposter ? (CubeAccess) imposter.cc_getWrappedClo() : (CubeAccess) cloAccess;
        boolean changed = cube.tryMarkSaved();
        if (!changed && !cube.cc_inhabitedTimeUnsaved()) {
            return false;
        }
        CubePos cubePos = cloPos.cubePos();
        try {
            ChunkStatus status = cube.getPersistedStatus();
            if (status.getChunkType() != ChunkType.LEVELCHUNK) {
                if (cc_isExistingCubeFull(cubePos)) {
                    return false;
                }
                if (status == ChunkStatus.EMPTY && cube.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                    return false;
                }
            }
            activeChunkWrites.incrementAndGet();
            long inhabitedTime = cube.getInhabitedTime();
            CubeSerializer.Snapshot snapshot = CubeSerializer.copyOf(level, cube);
            cube.cc_setSavedInhabitedTime(inhabitedTime);
            CompletableFuture<CompoundTag> tag = CompletableFuture.supplyAsync(snapshot::write, Util.backgroundExecutor());
            cc_cubeStorage.write(cubePos, tag::join).handle((ignored, throwable) -> {
                if (throwable != null) {
                    CubicChunks.LOGGER.error("Failed to save cube {}", cubePos, throwable);
                }
                activeChunkWrites.decrementAndGet();
                return null;
            });
            cc_markPosition(cloPos, status.getChunkType());
            cc_cubesSaved.incrementAndGet();
            return true;
        } catch (Exception e) {
            CubicChunks.LOGGER.error("Failed to save cube {}", cubePos, e);
            return false;
        }
    }

    /** Whether the cube on disk is finished, so a cube still generating must not overwrite it (as vanilla's isExistingChunkFull). */
    private boolean cc_isExistingCubeFull(CubePos cubePos) {
        long key = CloPos.cube(cubePos).asLong();
        byte known = chunkTypeCache.get(key);
        if (known != 0) {
            return known == 1;
        }
        CompoundTag tag;
        try {
            tag = cc_cubeStorage.read(cubePos).join().orElse(null);
        } catch (Exception e) {
            CubicChunks.LOGGER.error("Failed to read cube {}", cubePos, e);
            chunkTypeCache.put(key, (byte) -1);
            return false;
        }
        if (tag == null) {
            chunkTypeCache.put(key, (byte) -1);
            return false;
        }
        ChunkType type = CubeSerializer.statusOf(tag).getChunkType();
        chunkTypeCache.put(key, (byte) (type == ChunkType.PROTOCHUNK ? -1 : 1));
        return type == ChunkType.LEVELCHUNK;
    }

//    //region [cc_save dasm + mixin]
//    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class) @TransformFromMethod(@MethodSig("save
//    (Lnet/minecraft/world/level/chunk/ChunkAccess;)Z"))
//    private native boolean cc_save(CloAccess cloAccess);
//
//    /**
//     * Redirect error logging to log with CloPos
//     */
//    @Dynamic @Inject(method = "cc_save", at = @At(value = "INVOKE", target = "Lio/github/opencubicchunks/cc_core/world/level/CloPos;getX
//    ()I"), cancellable = true)
//    private void cc_onSave_errorLog(CloAccess cloAccess, CallbackInfoReturnable<Boolean> cir, @Local Exception exception) {
//        LOGGER.error("Failed to save chunk or cube {}", cloAccess.cc_getCloPos().toString(), exception);
//        cir.setReturnValue(false);
//    }
//    //endregion

    // This calls ChunkSerializer.getChunkTypeFromTag, which could be an issue?
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("isExistingChunkFull(Lnet/minecraft/world/level/ChunkPos;)Z")
    private native boolean cc_isExistingChunkFull(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)V")
    private native void cc_markChunkPendingToSend(ServerPlayer player, CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V")
    private static native void cc_markChunkPendingToSend(ServerPlayer player, LevelClo clo);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("dropChunk(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)V")
    private static native void cc_dropChunk(ServerPlayer player, CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("getChunkToSend(J)Lnet/minecraft/world/level/chunk/LevelChunk;")
    public native LevelClo cc_getChunkToSend(long cloPos);

    // dumpChunks (low prio)

    // printFuture - only ever called in dumpChunks

    // TODO (P2) readChunk: this.upgradeChunkTag might need a dasm redirect?

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("readChunk(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;")
    private native CompletableFuture<Optional<CompoundTag>> cc_readChunk(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("collectSpawningChunks(Ljava/util/List;)V")
    native void cc_collectSpawningClos(List<LevelClo> list);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("forEachBlockTickingChunk(Ljava/util/function/Consumer;)V")
    native void cc_forEachBlockTickingClo(Consumer<LevelClo> consumer);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z")
    public native boolean cc_anyPlayerCloseEnoughForSpawning(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("anyPlayerCloseEnoughForSpawningInternal(Lnet/minecraft/world/level/ChunkPos;)Z")
    private native boolean cc_anyPlayerCloseEnoughForSpawningInternal(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("getPlayersCloseForSpawning(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;")
    public native List<ServerPlayer> cc_getPlayersCloseForSpawning(CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("playerIsCloseEnoughForSpawning(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)Z")
    private native boolean cc_playerIsCloseEnoughForSpawning(ServerPlayer player, CloPos cloPos);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("updatePlayerStatus(Lnet/minecraft/server/level/ServerPlayer;Z)V")
    public native void cc_updatePlayerStatus(ServerPlayer player, boolean track);

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("move(Lnet/minecraft/server/level/ServerPlayer;)V")
    public native void cc_move(ServerPlayer player);

    // region [cc_updateChunkTracking dasm + mixin]
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V")
    private native void cc_updateChunkTracking(ServerPlayer player);

    @Dynamic @WrapOperation(method = "cc_updateChunkTracking", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I"))
    private int cc_onUpdateChunkTracking_getViewDistance(ChunkMap instance, ServerPlayer player, Operation<Integer> original) {
        return Coords.sectionToCubeRenderDistance(original.call(instance, player));
    }
    // endregion

    // region [cc_applyChunkTrackingView dasm + mixin]
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("applyChunkTrackingView(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ChunkTrackingView;)V")
    private native void cc_applyChunkTrackingView(ServerPlayer player, CloTrackingView chunkTrackingView);

    @Dynamic @Redirect(method = "cc_applyChunkTrackingView", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void cc_onApplyChunkTrackingView_setChunkCacheCenterPacket(
            ServerGamePacketListenerImpl instance, Packet packet, ServerPlayer player, CloTrackingView cloTrackingView
    ) {
        ServerPlayNetworking.send(player,
                new CCClientboundSetCubeCacheCenterPacket(((CloTrackingView.Positioned) cloTrackingView).center().cubePos()));
    }
    // endregion

    // region [cc_getPlayers dasm + mixin]
    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;")
    public native List<ServerPlayer> cc_getPlayers(CloPos pos, boolean boundaryOnly);

    @Dynamic @Redirect(method = "cc_getPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnTrackedBorder(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean cc_getPlayers_isChunkOnTrackedBorder(ChunkMap instance, ServerPlayer player, int x, int z, @Local CloPos pos) {
        return this.cc_isChunkOnTrackedBorder(player, pos.getX(), pos.getY(), pos.getZ());
    }

    @Dynamic @Redirect(method = "cc_getPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean cc_getPlayers_isChunkTracked(ChunkMap instance, ServerPlayer player, int x, int z, @Local CloPos pos) {
        return this.cc_isChunkTracked(player, pos.getX(), pos.getY(), pos.getZ());
    }
    // endregion

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkMap_redirects.class, method = "getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;")
    @Override public List<ServerPlayer> cc_getPlayers(CubePos pos, boolean boundaryOnly) {
        return cc_getPlayers(CloPos.cube(pos), boundaryOnly);
    }

    // Replace `SectionPos.chunk()` with `SectionPos.cc_cube()` unconditionally here
    @AddTransformToSets(GlobalSet.ChunkMap_redirects.class)
    @TransformFromMethod(value = "tick(Ljava/util/function/BooleanSupplier;)V", useRedirectSets = { ChunkToCloSet.class, SectionPosToCubeSet.class })
    protected native void cc_tick(BooleanSupplier hasMoreTime);

    @AddTransformToSets(GlobalSet.ChunkMap_redirects.class)
    @TransformFromMethod(value = "tick()V", useRedirectSets = { ChunkToCloSet.class, SectionPosToCubeSet.class })
    public native void cc_tick();

    @AddTransformToSets(GlobalSet.ChunkMap_redirects.class)
    @TransformFromMethod(value = "processUnloads(Ljava/util/function/BooleanSupplier;)V")
    private native void cc_processUnloads(BooleanSupplier hasMoreTime);

    // TODO resendBiomesForChunks - only used for FillBiomeCommand

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("onFullChunkStatusChange(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V")
    public native void cc_onFullChunkStatusChange(CloPos cloPos, FullChunkStatus fullChunkStatus);

    @AddMethodToSets(containers = ChunkToCubeSet.ChunkMap_redirects.class, method = "onFullChunkStatusChange(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V")
    public void cc_onFullChunkStatusChange(CubePos cubePos, FullChunkStatus fullChunkStatus) {
        cc_onFullChunkStatusChange(CloPos.cube(cubePos), fullChunkStatus);
    }

    @AddTransformToSets(ChunkToCloSet.ChunkMap_redirects.class)
    @TransformFromMethod("waitForLightBeforeSending(Lnet/minecraft/world/level/ChunkPos;I)V")
    public native void cc_waitForLightBeforeSending(CloPos cloPos, int radius);
}
