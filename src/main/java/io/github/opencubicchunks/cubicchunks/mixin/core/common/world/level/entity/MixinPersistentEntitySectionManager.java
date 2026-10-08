package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.entity;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.entity.CubicEntitySections;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entities cube by cube in a cubic level (see {@link CubicEntitySections}): sections take the lower of their column's and their cube's
 * visibility. Columns still load and save their entities, as in vanilla; cubes only decide which of them are tracked and ticked.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class MixinPersistentEntitySectionManager<T extends EntityAccess> implements CubicEntitySections.Manager {
    @Shadow @Final private Long2ObjectMap<Visibility> chunkVisibility;
    @Shadow @Final private LongSet chunksToUnload;
    @Shadow @Final private EntitySectionStorage<T> sectionStorage;

    @Unique private boolean cc_cubic;
    @Unique private final Long2ObjectMap<Visibility> cc_cubeVisibility = new Long2ObjectOpenHashMap<>();

    @Shadow private void ensureChunkQueuedForLoad(long chunkPos) {
        throw new AssertionError();
    }

    @Shadow private void startTicking(T entity) {
        throw new AssertionError();
    }

    @Shadow private void stopTicking(T entity) {
        throw new AssertionError();
    }

    @Shadow private void startTracking(T entity) {
        throw new AssertionError();
    }

    @Shadow private void stopTracking(T entity) {
        throw new AssertionError();
    }

    @Override public void cc_makeCubic() {
        this.cc_cubic = true;
        this.cc_cubeVisibility.defaultReturnValue(Visibility.HIDDEN);
        ((CubicEntitySections.Storage) this.sectionStorage).cc_setSectionVisibility(this::cc_sectionVisibility);
    }

    @Unique private Visibility cc_sectionVisibility(long sectionKey) {
        int sectionX = SectionPos.x(sectionKey);
        int sectionY = SectionPos.y(sectionKey);
        int sectionZ = SectionPos.z(sectionKey);
        Visibility column = this.chunkVisibility.get(ChunkPos.pack(sectionX, sectionZ));
        Visibility cube = this.cc_cubeVisibility.get(CubePos.asLong(Coords.sectionToCube(sectionX), Coords.sectionToCube(sectionY), Coords.sectionToCube(sectionZ)));
        return CubicEntitySections.lower(column, cube);
    }

    /** As vanilla's updateChunkStatus, but each section of the column gets the lower of the column's and its cube's visibility. */
    @Inject(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V", at = @At("HEAD"), cancellable = true)
    private void cc_updateColumnStatus(ChunkPos pos, Visibility chunkStatus, CallbackInfo ci) {
        if (!this.cc_cubic) {
            return;
        }
        ci.cancel();
        long chunkKey = pos.pack();
        if (chunkStatus == Visibility.HIDDEN) {
            this.chunkVisibility.remove(chunkKey);
            this.chunksToUnload.add(chunkKey);
        } else {
            this.chunkVisibility.put(chunkKey, chunkStatus);
            this.chunksToUnload.remove(chunkKey);
            this.ensureChunkQueuedForLoad(chunkKey);
        }
        this.sectionStorage.getExistingSectionPositionsInChunk(chunkKey).forEach(sectionKey -> {
            EntitySection<T> section = this.sectionStorage.getSection(sectionKey);
            if (section != null) {
                this.cc_setSectionStatus(section, this.cc_sectionVisibility(sectionKey));
            }
        });
    }

    @Override public void cc_updateCubeStatus(CubePos cubePos, Visibility visibility) {
        if (visibility == Visibility.HIDDEN) {
            this.cc_cubeVisibility.remove(cubePos.asLong());
        } else {
            this.cc_cubeVisibility.put(cubePos.asLong(), visibility);
        }
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dy = 0; dy < CubicConstants.DIAMETER_IN_SECTIONS; dy++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    long sectionKey = SectionPos.asLong(Coords.cubeToSection(cubePos.getX(), dx), Coords.cubeToSection(cubePos.getY(), dy),
                            Coords.cubeToSection(cubePos.getZ(), dz));
                    EntitySection<T> section = this.sectionStorage.getSection(sectionKey);
                    if (section != null) {
                        this.cc_setSectionStatus(section, this.cc_sectionVisibility(sectionKey));
                    }
                }
            }
        }
    }

    /** Vanilla's per-section step of updateChunkStatus: entities start or stop being tracked and ticked as the section's visibility changes. */
    @Unique private void cc_setSectionStatus(EntitySection<T> section, Visibility status) {
        Visibility previous = section.updateChunkStatus(status);
        if (previous.isTicking() && !status.isTicking()) {
            section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::stopTicking);
        }
        if (previous.isAccessible() && !status.isAccessible()) {
            section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::stopTracking);
        } else if (!previous.isAccessible() && status.isAccessible()) {
            section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::startTracking);
        }
        if (!previous.isTicking() && status.isTicking()) {
            section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::startTicking);
        }
    }

    /** A position ticks entities when its cube does (and its column). */
    @Inject(method = "canPositionTick(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void cc_canCubePositionTick(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.cc_cubic) {
            cir.setReturnValue(this.cc_sectionVisibility(SectionPos.asLong(pos)).isTicking());
        }
    }
}
