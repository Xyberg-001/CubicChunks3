package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.chunk.storage;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.CubicSectionStorage;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSections;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeStorage;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla keeps section data (points of interest) per chunk column: it loads, saves and makes empty a column's sections over the dimension's
 * height, in the dimension's poi region files. In a cubic level that height is not the world's (a section beyond it was never made and a
 * lookup there failed), and a column can be thousands of sections tall; there the data is kept cube by cube instead, each cube's eight
 * sections in a cube store of their own (poi/region3d), loaded when one of them is first asked for and written when they change.
 */
@Mixin(SectionStorage.class)
public abstract class MixinSectionStorage<R, P> implements CubicSectionStorage {
    @Unique private static final Logger CC_LOGGER = LogUtils.getLogger();

    @Shadow @Final private Long2ObjectMap<Optional<R>> storage;
    @Shadow @Final private Codec<P> codec;
    @Shadow @Final private Function<R, P> packer;
    @Shadow @Final private BiFunction<P, Runnable, R> unpacker;
    @Shadow @Final private RegistryAccess registryAccess;

    @Unique private @Nullable CubeStorage cc_cubes;
    @Unique private final LongSet cc_loadedCubes = new LongOpenHashSet();
    @Unique private final LongLinkedOpenHashSet cc_dirtyCubes = new LongLinkedOpenHashSet();
    /** Cubes being read by {@link #cc_prefetchCube} (server thread only). */
    @Unique private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<CompletableFuture<Void>> cc_prefetchingCubes =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    @Shadow protected abstract boolean outsideStoredRange(long sectionPos);

    @Shadow protected abstract void onSectionLoad(long sectionPos);

    @Shadow protected abstract void setDirty(long sectionPos);

    @Shadow protected abstract @Nullable Optional<R> get(long sectionPos);

    @Shadow protected abstract Optional<R> getOrLoad(long sectionPos);

    @Override public void cc_setCubeStorage(CubeStorage storage) {
        this.cc_cubes = storage;
    }

    @Override public boolean cc_isCubic() {
        return this.cc_cubes != null;
    }

    @Override public Optional<?> cc_getOrLoad(long sectionPos) {
        return this.getOrLoad(sectionPos);
    }

    @Override public CompletableFuture<?> cc_prefetchCube(CubePos cubePos, java.util.concurrent.Executor serverThread) {
        long key = cubePos.asLong();
        if (this.cc_cubes == null || this.cc_loadedCubes.contains(key)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> reading = this.cc_prefetchingCubes.get(key);
        if (reading == null) {
            reading = this.cc_cubes.read(cubePos).handleAsync((tag, error) -> {
                this.cc_prefetchingCubes.remove(key);
                if (error != null) {
                    CC_LOGGER.error("Failed to read the sections of cube {}; starting them empty", cubePos, error);
                }
                if (this.cc_loadedCubes.add(key)) { // unless something asked for them meanwhile, and they were read then
                    this.cc_unpackCube(cubePos, error == null ? tag.orElse(null) : null);
                }
                return null;
            }, serverThread);
            this.cc_prefetchingCubes.put(key, reading);
        }
        return reading;
    }

    @Unique private static CubePos cc_cubeOf(long sectionPos) {
        return CubePos.of(Coords.sectionToCube(SectionPos.x(sectionPos)), Coords.sectionToCube(SectionPos.y(sectionPos)),
                Coords.sectionToCube(SectionPos.z(sectionPos)));
    }

    @Inject(method = "getOrLoad", at = @At("HEAD"), cancellable = true)
    private void cc_getOrLoadCube(long sectionPos, CallbackInfoReturnable<Optional<R>> cir) {
        if (this.cc_cubes == null) {
            return;
        }
        if (this.outsideStoredRange(sectionPos)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        Optional<R> section = this.get(sectionPos);
        if (section == null) {
            this.cc_loadCube(cc_cubeOf(sectionPos));
            section = this.get(sectionPos);
        }
        cir.setReturnValue(section == null ? Optional.empty() : section);
    }

    /** Reads a cube's sections (vanilla's unpackChunk, for a cube): each present or known to be empty from then on. */
    @Unique private void cc_loadCube(CubePos cubePos) {
        if (!this.cc_loadedCubes.add(cubePos.asLong())) {
            return;
        }
        CompoundTag tag = null;
        try {
            tag = this.cc_cubes.read(cubePos).join().orElse(null);
        } catch (RuntimeException e) {
            CC_LOGGER.error("Failed to read the sections of cube {}; starting them empty", cubePos, e);
        }
        this.cc_unpackCube(cubePos, tag);
    }

    /** Takes in a cube's sections as read (none: all empty). */
    @Unique private void cc_unpackCube(CubePos cubePos, @Nullable CompoundTag tag) {
        CompoundTag sections = tag == null ? null : tag.getCompoundOrEmpty("Sections");
        RegistryOps<Tag> ops = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            long key = CubeSections.sectionPosOf(cubePos, i).asLong();
            if (this.storage.containsKey(key)) {
                continue;
            }
            Optional<R> section = Optional.empty();
            Tag data = sections == null ? null : sections.get(Integer.toString(i));
            if (data != null) {
                section = this.codec.parse(ops, data).resultOrPartial(CC_LOGGER::error).map(packed -> this.unpacker.apply(packed, () -> this.setDirty(key)));
            }
            this.storage.put(key, section);
            if (section.isPresent()) {
                this.onSectionLoad(key);
            }
        }
    }

    /** Writes a cube's sections (vanilla's writeChunk, for a cube); built here, on the server thread, written on the store's. */
    @Unique private void cc_writeCube(CubePos cubePos) {
        RegistryOps<Tag> ops = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag sections = new CompoundTag();
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            Optional<R> section = this.storage.get(CubeSections.sectionPosOf(cubePos, i).asLong());
            if (section != null && section.isPresent()) {
                String name = Integer.toString(i);
                this.codec.encodeStart(ops, this.packer.apply(section.get())).resultOrPartial(CC_LOGGER::error).ifPresent(t -> sections.put(name, t));
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Sections", sections);
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        CompletableFuture<Void> write = this.cc_cubes.write(cubePos, () -> tag);
        write.exceptionally(throwable -> {
            CC_LOGGER.error("Failed to write the sections of cube {}", cubePos, throwable);
            return null;
        });
    }

    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void cc_setCubeDirty(long sectionPos, CallbackInfo ci) {
        if (this.cc_cubes == null) {
            return;
        }
        ci.cancel();
        Optional<R> section = this.storage.get(sectionPos);
        if (section != null && section.isPresent()) {
            this.cc_dirtyCubes.add(cc_cubeOf(sectionPos).asLong());
        } else {
            CC_LOGGER.warn("No data for position: {}", SectionPos.of(sectionPos));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cc_tickCubes(BooleanSupplier haveTime, CallbackInfo ci) {
        if (this.cc_cubes == null) {
            return;
        }
        ci.cancel();
        LongIterator iterator = this.cc_dirtyCubes.iterator();
        while (iterator.hasNext() && haveTime.getAsBoolean()) {
            CubePos cubePos = CubePos.from(iterator.nextLong());
            iterator.remove();
            this.cc_writeCube(cubePos);
        }
    }

    @Inject(method = "flushAll", at = @At("HEAD"), cancellable = true)
    private void cc_flushAllCubes(CallbackInfo ci) {
        if (this.cc_cubes == null) {
            return;
        }
        ci.cancel();
        this.cc_dirtyCubes.forEach(key -> this.cc_writeCube(CubePos.from(key)));
        this.cc_dirtyCubes.clear();
    }

    @Override public void cc_flushCube(CubePos cubePos) {
        if (this.cc_cubes != null && this.cc_dirtyCubes.remove(cubePos.asLong())) {
            this.cc_writeCube(cubePos);
        }
    }

    @Inject(method = "hasWork", at = @At("HEAD"), cancellable = true)
    private void cc_hasCubeWork(CallbackInfoReturnable<Boolean> cir) {
        if (this.cc_cubes != null) {
            cir.setReturnValue(!this.cc_dirtyCubes.isEmpty());
        }
    }

    /** A column holds no sections in a cubic level. */
    @Inject(method = "flush", at = @At("HEAD"), cancellable = true)
    private void cc_noColumnFlush(ChunkPos chunkPos, CallbackInfo ci) {
        if (this.cc_cubes != null) {
            ci.cancel();
        }
    }

    @Inject(method = "prefetch", at = @At("HEAD"), cancellable = true)
    private void cc_noColumnPrefetch(ChunkPos chunkPos, CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (this.cc_cubes != null) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void cc_closeCubes(CallbackInfo ci) throws IOException {
        if (this.cc_cubes != null) {
            this.cc_cubes.close();
        }
    }
}
