package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.entity.ai.village.poi;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.opencubicchunks.cubicchunks.world.level.CubicSectionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Points of interest in a cubic level (see MixinSectionStorage). Vanilla looks for them through whole columns, the dimension's height of
 * sections per chunk around the centre; a cubic column can be thousands of sections tall, so the search reaches as far up and down as it
 * reaches across (vanilla's getInRange keeps only those within the radius anyway; getInSquare no longer finds a bed a kilometre overhead).
 */
@Mixin(PoiManager.class)
public abstract class MixinPoiManager {
    @Inject(method = "getInSquare", at = @At("HEAD"), cancellable = true)
    private void cc_getInCubicSquare(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<PoiRecord>> cir) {
        CubicSectionStorage storage = (CubicSectionStorage) this;
        if (!storage.cc_isCubic()) {
            return;
        }
        int chunkRadius = Math.floorDiv(radius, 16) + 1;
        int minSectionY = SectionPos.blockToSectionCoord(center.getY() - radius);
        int maxSectionY = SectionPos.blockToSectionCoord(center.getY() + radius);
        cir.setReturnValue(ChunkPos.rangeClosed(ChunkPos.containing(center), chunkRadius)
                .flatMap(chunk -> IntStream.rangeClosed(minSectionY, maxSectionY)
                        .mapToObj(sectionY -> storage.cc_getOrLoad(SectionPos.asLong(chunk.x(), sectionY, chunk.z())))
                        .filter(Optional::isPresent)
                        .flatMap(section -> ((PoiSection) section.get()).getRecords(predicate, occupancy)))
                .filter(record -> {
                    BlockPos pos = record.getPos();
                    return Math.abs(pos.getX() - center.getX()) <= radius && Math.abs(pos.getZ() - center.getZ()) <= radius;
                }));
    }

    /**
     * Vanilla (finding a portal) loads the chunks around whose points of interest are not known yet, to find them from their blocks. In a
     * cubic level a cube's are kept with it and found from its blocks as it loads (MixinServerLevel), so loading the sections around is
     * enough.
     */
    @Inject(method = "ensureLoadedAndValid", at = @At("HEAD"), cancellable = true)
    private void cc_ensureCubesLoaded(LevelReader reader, BlockPos center, int radius, CallbackInfo ci) {
        CubicSectionStorage storage = (CubicSectionStorage) this;
        if (!storage.cc_isCubic()) {
            return;
        }
        ci.cancel();
        int chunkRadius = Math.floorDiv(radius, 16);
        int minSectionY = SectionPos.blockToSectionCoord(center.getY() - radius);
        int maxSectionY = SectionPos.blockToSectionCoord(center.getY() + radius);
        ChunkPos.rangeClosed(ChunkPos.containing(center), chunkRadius).forEach(chunk -> {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                storage.cc_getOrLoad(SectionPos.asLong(chunk.x(), sectionY, chunk.z()));
            }
        });
    }
}
