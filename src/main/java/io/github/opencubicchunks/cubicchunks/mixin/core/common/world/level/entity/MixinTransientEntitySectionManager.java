package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.entity;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.entity.CubicEntitySections;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's entities cube by cube in a cubic level: a section's entities tick while the client holds its cube, and are only tracked
 * (still drawn, moved by the server's updates) otherwise, as vanilla does in a chunk that is not ticking. Vanilla goes by the columns the
 * client holds, and a cubic client holds none, so no entity but the player ever ticked on it.
 */
@Mixin(TransientEntitySectionManager.class)
public abstract class MixinTransientEntitySectionManager<T extends EntityAccess> implements CubicEntitySections.Manager {
    @Shadow @Final private LongSet tickingChunks;
    @Shadow @Final private EntitySectionStorage<T> sectionStorage;
    @Shadow @Final private LevelCallback<T> callbacks;

    @Unique private boolean cc_cubic;
    @Unique private final LongSet cc_heldCubes = new LongOpenHashSet();

    @Override public void cc_makeCubic() {
        this.cc_cubic = true;
        ((CubicEntitySections.Storage) this.sectionStorage).cc_setSectionVisibility(this::cc_sectionVisibility);
    }

    @Unique private Visibility cc_sectionVisibility(long sectionKey) {
        long cubeKey = CubePos.asLong(Coords.sectionToCube(SectionPos.x(sectionKey)), Coords.sectionToCube(SectionPos.y(sectionKey)),
                Coords.sectionToCube(SectionPos.z(sectionKey)));
        return this.cc_heldCubes.contains(cubeKey) ? Visibility.TICKING : Visibility.TRACKED;
    }

    /** Columns do not decide it in a cubic level (see above); the client's columns, if any, are only noted. */
    @Inject(method = "startTicking", at = @At("HEAD"), cancellable = true)
    private void cc_startTickingColumn(ChunkPos pos, CallbackInfo ci) {
        if (this.cc_cubic) {
            ci.cancel();
            this.tickingChunks.add(pos.pack());
        }
    }

    @Inject(method = "stopTicking", at = @At("HEAD"), cancellable = true)
    private void cc_stopTickingColumn(ChunkPos pos, CallbackInfo ci) {
        if (this.cc_cubic) {
            ci.cancel();
            this.tickingChunks.remove(pos.pack());
        }
    }

    /** The client got (TICKING) or dropped (anything else) a cube. */
    @Override public void cc_updateCubeStatus(CubePos cubePos, Visibility visibility) {
        if (visibility.isTicking()) {
            this.cc_heldCubes.add(cubePos.asLong());
        } else {
            this.cc_heldCubes.remove(cubePos.asLong());
        }
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dy = 0; dy < CubicConstants.DIAMETER_IN_SECTIONS; dy++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    long sectionKey = SectionPos.asLong(Coords.cubeToSection(cubePos.getX(), dx), Coords.cubeToSection(cubePos.getY(), dy),
                            Coords.cubeToSection(cubePos.getZ(), dz));
                    EntitySection<T> section = this.sectionStorage.getSection(sectionKey);
                    if (section != null) {
                        this.cc_setStatus(section, this.cc_sectionVisibility(sectionKey));
                    }
                }
            }
        }
    }

    /** Vanilla's step of startTicking/stopTicking for one section. */
    @Unique private void cc_setStatus(EntitySection<T> section, Visibility status) {
        Visibility previous = section.updateChunkStatus(status);
        if (!previous.isTicking() && status.isTicking()) {
            section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this.callbacks::onTickingStart);
        } else if (previous.isTicking() && !status.isTicking()) {
            section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this.callbacks::onTickingEnd);
        }
    }
}
