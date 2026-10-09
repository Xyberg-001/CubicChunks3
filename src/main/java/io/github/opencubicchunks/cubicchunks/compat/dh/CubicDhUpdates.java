package io.github.opencubicchunks.cubicchunks.compat.dh;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Keeps Distant Horizons' copy of a cubic level current: block changes in the level's cubes (within its height window) mark their cube
 * column, and every {@link #FLUSH_TICKS} ticks the marked columns are rebuilt off the server thread, as the world generator builds them
 * (CubicDhWorldGenerator), and merged into its data (the same way it merges a chunk that changed; on a server, that also sends them on to
 * players with Distant Horizons). Distant Horizons' own updates come from chunk columns, which hold no blocks in a cubic level.
 */
final class CubicDhUpdates {
    static final int FLUSH_TICKS = 100;
    /** One thread for all levels: updates are few and can wait. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CubicChunks-DH-updates");
        thread.setDaemon(true);
        return thread;
    });

    private final CubicDhWorldGenerator generator;
    private final ILevelWrapper levelWrapper;
    private final LongSet changedColumns = new LongOpenHashSet();
    private int ticks;

    CubicDhUpdates(CubicDhWorldGenerator generator, ILevelWrapper levelWrapper) {
        this.generator = generator;
        this.levelWrapper = levelWrapper;
    }

    /** A block changed (server thread). */
    void blockChanged(BlockPos pos) {
        if (pos.getY() >= DhWindow.minY() && pos.getY() < DhWindow.minY() + DhWindow.height()) {
            this.changedColumns.add(ChunkPos.pack(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getZ())));
        }
    }

    /** Called each tick of the level (server thread). */
    void tick() {
        if (++this.ticks < FLUSH_TICKS || this.changedColumns.isEmpty()) {
            return;
        }
        this.ticks = 0;
        long[] columns = this.changedColumns.toLongArray();
        this.changedColumns.clear();
        EXECUTOR.execute(() -> this.send(columns));
    }

    private void send(long[] cubeColumns) {
        AbstractDhWorld world = SharedApi.getAbstractDhWorld();
        IDhLevel dhLevel = world == null ? null : world.getLevel(this.levelWrapper);
        if (dhLevel == null) {
            return;
        }
        Map<Long, LevelChunkSection[]> cubes = new HashMap<>();
        for (long column : cubeColumns) {
            int cubeX = ChunkPos.getX(column);
            int cubeZ = ChunkPos.getZ(column);
            for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    try {
                        DhApiChunk chunk = this.generator.buildChunk(Coords.cubeToSection(cubeX, dx), Coords.cubeToSection(cubeZ, dz), false, cubes);
                        if (chunk == null) {
                            continue;
                        }
                        FullDataSourceV2 data = LodDataBuilder.createFromApiChunkData(chunk, true);
                        if (data != null) {
                            dhLevel.updateDataSourcesAsync(data).whenComplete((done, error) -> data.close());
                        }
                    } catch (Exception e) {
                        CubicChunks.LOGGER.warn("Distant Horizons did not take the changes in cube column {}, {}: {}", cubeX, cubeZ, e.toString());
                    }
                }
            }
        }
    }
}
