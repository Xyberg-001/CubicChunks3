package io.github.opencubicchunks.cubicchunks.world.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import cubicchunks.regionlib.impl.EntryLocation2D;
import cubicchunks.regionlib.impl.EntryLocation3D;
import cubicchunks.regionlib.impl.SaveCubeColumns;
import cubicchunks.regionlib.impl.header.TimestampHeaderEntryProvider;
import cubicchunks.regionlib.impl.save.SaveSection2D;
import cubicchunks.regionlib.impl.save.SaveSection3D;
import cubicchunks.regionlib.lib.ExtRegion;
import cubicchunks.regionlib.lib.Region;
import cubicchunks.regionlib.lib.provider.SharedCachedRegionProvider;
import cubicchunks.regionlib.lib.provider.SimpleRegionProvider;
import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * Cube data on disk: regionlib's 3D region files (16x16x16 cubes per file) under a dimension's {@code region3d} folder,
 * with {@code region2d} beside it for column data. Every read and write runs on one thread per dimension, in the order
 * asked, so a read always sees the writes queued before it.
 */
public class CubeStorage implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path directory;
    private final ExecutorService io;
    private @Nullable SaveCubeColumns save;
    private volatile boolean closed;

    public CubeStorage(Path directory, String name) {
        this.directory = directory;
        this.io = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("CubeIO-" + name)
                .setPriority(Thread.NORM_PRIORITY - 1).build());
    }

    /** The cube's saved data, or empty if it was never saved. */
    public CompletableFuture<Optional<CompoundTag>> read(CubePos pos) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<ByteBuffer> buf = save().load(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), true);
                if (buf.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(NbtIo.readCompressed(new ByteArrayInputStream(buf.get().array()), NbtAccounter.unlimitedHeap()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read cube " + pos, e);
            }
        }, io);
    }

    /** Writes the cube's data (built by the supplier, on this storage's thread) over any saved before. */
    public CompletableFuture<Void> write(CubePos pos, Supplier<CompoundTag> data) {
        return CompletableFuture.runAsync(() -> {
            CompoundTag tag = data.get();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(tag, out);
                save().save3d(new EntryLocation3D(pos.getX(), pos.getY(), pos.getZ()), ByteBuffer.wrap(out.toByteArray()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write cube " + pos, e);
            }
        }, io);
    }

    /** A column's saved data (see ColumnSerializer), or empty. */
    public CompletableFuture<Optional<CompoundTag>> readColumn(int x, int z) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<ByteBuffer> buf = save().load(new EntryLocation2D(x, z), true);
                if (buf.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(NbtIo.readCompressed(new ByteArrayInputStream(buf.get().array()), NbtAccounter.unlimitedHeap()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read column " + x + ", " + z, e);
            }
        }, io);
    }

    /** Writes a column's data (built by the supplier, on this storage's thread) over any saved before. */
    public CompletableFuture<Void> writeColumn(int x, int z, Supplier<CompoundTag> data) {
        return CompletableFuture.runAsync(() -> {
            CompoundTag tag = data.get();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(tag, out);
                save().save2d(new EntryLocation2D(x, z), ByteBuffer.wrap(out.toByteArray()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write column " + x + ", " + z, e);
            }
        }, io);
    }

    /** Waits for every read and write asked so far. */
    public void synchronize() {
        if (!closed) {
            CompletableFuture.runAsync(() -> { }, io).join();
        }
    }

    @Override public void close() throws IOException {
        if (closed) {
            return;
        }
        synchronize();
        closed = true;
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    if (save != null) {
                        save.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    save = null;
                }
            }, io).join();
        } finally {
            io.shutdown();
            try {
                if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOGGER.warn("Cube storage {} did not stop within 30 s", directory);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Only called on the io thread
    private SaveCubeColumns save() throws IOException {
        if (save == null) {
            save = createSave(directory);
        }
        return save;
    }

    private static SaveCubeColumns createSave(Path directory) throws IOException {
        Path part2d = directory.resolve("region2d");
        Path part3d = directory.resolve("region3d");
        Files.createDirectories(part2d);
        Files.createDirectories(part3d);

        SaveSection2D section2d = new SaveSection2D(
                new SharedCachedRegionProvider<>(
                        new SimpleRegionProvider<>(new EntryLocation2D.Provider(), part2d, (keyProvider, regionKey) ->
                                new Region.Builder<EntryLocation2D>()
                                        .setDirectory(part2d)
                                        .setRegionKey(regionKey)
                                        .setKeyProvider(keyProvider)
                                        .setSectorSize(512)
                                        .addHeaderEntry(new TimestampHeaderEntryProvider<>(TimeUnit.MILLISECONDS))
                                        .build(),
                                (dir, key) -> Files.exists(dir.resolve(key.getRegionKey().getName()))
                        )
                ),
                new SharedCachedRegionProvider<>(
                        new SimpleRegionProvider<>(new EntryLocation2D.Provider(), part2d,
                                (keyProvider, regionKey) -> new ExtRegion<>(part2d, Collections.emptyList(), keyProvider, regionKey),
                                (dir, key) -> Files.exists(dir.resolve(key.getRegionKey().getName() + ".ext"))
                        )
                ));
        SaveSection3D section3d = new SaveSection3D(
                new SharedCachedRegionProvider<>(
                        new SimpleRegionProvider<>(new EntryLocation3D.Provider(), part3d, (keyProvider, regionKey) ->
                                new Region.Builder<EntryLocation3D>()
                                        .setDirectory(part3d)
                                        .setRegionKey(regionKey)
                                        .setKeyProvider(keyProvider)
                                        .setSectorSize(512)
                                        .addHeaderEntry(new TimestampHeaderEntryProvider<>(TimeUnit.MILLISECONDS))
                                        .build(),
                                (dir, key) -> Files.exists(dir.resolve(key.getRegionKey().getName()))
                        )
                ),
                new SharedCachedRegionProvider<>(
                        new SimpleRegionProvider<>(new EntryLocation3D.Provider(), part3d,
                                (keyProvider, regionKey) -> new ExtRegion<>(part3d, Collections.emptyList(), keyProvider, regionKey),
                                (dir, key) -> Files.exists(dir.resolve(key.getRegionKey().getName() + ".ext"))
                        )
                ));
        return new SaveCubeColumns(section2d, section3d);
    }
}
