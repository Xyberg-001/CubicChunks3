package io.github.opencubicchunks.cubicchunks.compat.dh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.server.level.CubicChunkMap;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.PlaceholderTerrain;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Distant Horizons' world generator for a cubic level: when it wants a chunk's columns it does not have, they are built here from the cubes
 * over its height window (DhWindow), top down: a cube the server has loaded, else one saved, else (unless Distant Horizons only wants what
 * already exists) the terrain the world generates (PlaceholderTerrain until there is a real generator). Columns are runs of the same block,
 * with sky light down to the first block that is not air; cubes and sections of only air are passed over whole.
 */
public final class CubicDhWorldGenerator implements IDhApiWorldGenerator {
    /** A cube neither loaded nor saved (kept in a request's cache as such, so it is looked up once). */
    private static final LevelChunkSection[] NO_CUBE = new LevelChunkSection[0];

    private final ServerLevel level;
    private final IDhApiLevelWrapper levelWrapper;
    private final Map<BlockState, IDhApiBlockStateWrapper> blockWrappers = new ConcurrentHashMap<>();
    private final Map<Holder<Biome>, IDhApiBiomeWrapper> biomeWrappers = new ConcurrentHashMap<>();
    private final Holder<Biome> defaultBiome;
    private final BlockState air = Blocks.AIR.defaultBlockState();

    public CubicDhWorldGenerator(ServerLevel level, IDhApiLevelWrapper levelWrapper) {
        this.level = level;
        this.levelWrapper = levelWrapper;
        this.defaultBiome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    }

    @Override public EDhApiWorldGeneratorReturnType getReturnType() {
        return EDhApiWorldGeneratorReturnType.API_CHUNKS;
    }

    @Override public CompletableFuture<Void> generateApiChunks(
            int chunkPosMinX, int chunkPosMinZ, int generationRequestChunkWidthCount, byte targetDataDetail, EDhApiDistantGeneratorMode generatorMode,
            ExecutorService worldGeneratorThreadPool, Consumer<DhApiChunk> resultConsumer
    ) {
        boolean existingOnly = generatorMode == EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY;
        return CompletableFuture.runAsync(() -> {
            Map<Long, LevelChunkSection[]> cubes = new HashMap<>();
            for (int chunkX = chunkPosMinX; chunkX < chunkPosMinX + generationRequestChunkWidthCount; chunkX++) {
                for (int chunkZ = chunkPosMinZ; chunkZ < chunkPosMinZ + generationRequestChunkWidthCount; chunkZ++) {
                    DhApiChunk chunk = this.buildChunk(chunkX, chunkZ, existingOnly, cubes);
                    if (chunk != null) {
                        resultConsumer.accept(chunk);
                    }
                }
            }
        }, worldGeneratorThreadPool);
    }

    /** The chunk's columns, or null if Distant Horizons only wants what exists and no cube over its window does. */
    private @Nullable DhApiChunk buildChunk(int chunkX, int chunkZ, boolean existingOnly, Map<Long, LevelChunkSection[]> cache) {
        int minY = DhWindow.minY();
        int topY = minY + DhWindow.height(); // exclusive
        int cubeX = Coords.sectionToCube(chunkX);
        int cubeZ = Coords.sectionToCube(chunkZ);
        int minCubeY = Coords.blockToCube(minY);
        int maxCubeY = Coords.blockToCube(topY - 1);
        LevelChunkSection[][] column = new LevelChunkSection[maxCubeY - minCubeY + 1][];
        boolean anyExists = false;
        for (int cubeY = maxCubeY; cubeY >= minCubeY; cubeY--) {
            LevelChunkSection[] sections = cache.computeIfAbsent(CubePos.asLong(cubeX, cubeY, cubeZ), key -> this.sectionsOf(CubePos.from(key)));
            column[maxCubeY - cubeY] = sections == NO_CUBE ? null : sections;
            anyExists |= sections != NO_CUBE;
        }
        if (existingOnly && !anyExists) {
            return null;
        }

        DhApiChunk chunk = DhApiChunk.create(chunkX, chunkZ, minY, topY);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setDataPoints(x, z, this.buildColumn(Coords.sectionToMinBlock(chunkX) + x, Coords.sectionToMinBlock(chunkZ) + z, minY, topY,
                        maxCubeY, column, existingOnly));
            }
        }
        return chunk;
    }

    private List<DhApiTerrainDataPoint> buildColumn(
            int blockX, int blockZ, int minY, int topY, int maxCubeY, LevelChunkSection[][] column, boolean existingOnly
    ) {
        Runs runs = new Runs(topY);
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        int sectionInCubeX = Coords.cubeLocalSection(Coords.blockToSection(blockX));
        int sectionInCubeZ = Coords.cubeLocalSection(Coords.blockToSection(blockZ));
        for (int i = 0; i < column.length; i++) {
            int cubeY = maxCubeY - i;
            int cubeTop = Math.min(topY, Coords.cubeToMinBlock(cubeY + 1)); // exclusive
            int cubeBottom = Math.max(minY, Coords.cubeToMinBlock(cubeY));
            LevelChunkSection[] sections = column[i];
            if (sections == null) {
                if (existingOnly) {
                    runs.add(this.air, this.defaultBiome, cubeBottom);
                } else {
                    // no cube here: the world's terrain, solid up to its surface
                    int surface = PlaceholderTerrain.surfaceY(blockX, blockZ);
                    if (surface + 1 < cubeTop) {
                        runs.add(this.air, this.defaultBiome, Math.max(cubeBottom, surface + 1));
                    }
                    if (surface >= cubeBottom) {
                        runs.add(PlaceholderTerrain.block(), this.defaultBiome, cubeBottom);
                    }
                }
                continue;
            }
            // a cube: section by section from the top, blocks one by one where a section has any
            for (int sectionTop = cubeTop; sectionTop > cubeBottom; ) {
                int sectionY = Coords.blockToSection(sectionTop - 1);
                int sectionBottom = Math.max(cubeBottom, Coords.sectionToMinBlock(sectionY));
                LevelChunkSection section = sections[Coords.sectionToIndex(sectionInCubeX, Coords.cubeLocalSection(sectionY), sectionInCubeZ)];
                if (section == null || section.hasOnlyAir()) {
                    runs.add(this.air, section == null ? this.defaultBiome : section.getNoiseBiome(localX >> 2, 0, localZ >> 2), sectionBottom);
                } else {
                    for (int y = sectionTop - 1; y >= sectionBottom; y--) {
                        runs.add(section.getBlockState(localX, y & 15, localZ), section.getNoiseBiome(localX >> 2, (y & 15) >> 2, localZ >> 2), y);
                    }
                }
                sectionTop = sectionBottom;
            }
        }
        return runs.finish();
    }

    /** The cube's sections: loaded on the server, else saved, else {@link #NO_CUBE}. */
    private LevelChunkSection[] sectionsOf(CubePos pos) {
        CubeAccess loaded = ((ServerCubeCache) this.level.getChunkSource()).cc_getFullCubeNow(pos);
        if (loaded != null) {
            return loaded.getSections();
        }
        try {
            Optional<CompoundTag> saved = ((CubicChunkMap) this.level.getChunkSource().chunkMap).cc_readSavedCube(pos).join();
            if (saved.isPresent()) {
                CubeSerializer.Parsed parsed = CubeSerializer.parse(this.level.registryAccess(), saved.get());
                return parsed == null ? NO_CUBE : parsed.sections();
            }
        } catch (RuntimeException e) {
            CubicChunks.LOGGER.warn("Distant Horizons could not read cube {}: {}", pos, e.toString());
        }
        return NO_CUBE;
    }

    private IDhApiBlockStateWrapper wrap(BlockState state) {
        return this.blockWrappers.computeIfAbsent(state, s -> s.isAir() ? DhApi.Delayed.wrapperFactory.getAirBlockStateWrapper()
                : DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[] { s }, this.levelWrapper));
    }

    private IDhApiBiomeWrapper wrap(Holder<Biome> biome) {
        return this.biomeWrappers.computeIfAbsent(biome, b -> DhApi.Delayed.wrapperFactory.getBiomeWrapper(new Object[] { b }, this.levelWrapper));
    }

    @Override public void preGeneratorTaskStart() {
    }

    @Override public void close() {
    }

    /**
     * A column as Distant Horizons takes it: runs of one block, top down and without gaps. Sky light is full down to the first block that is
     * not air, none below; block light is not kept.
     */
    private final class Runs {
        private final List<DhApiTerrainDataPoint> points = new ArrayList<>();
        private @Nullable BlockState state;
        private @Nullable Holder<Biome> biome;
        private int runTop;
        private int runBottom;
        private boolean underground;

        Runs(int topY) {
            this.runTop = topY;
            this.runBottom = topY;
        }

        /** Extends the column down to bottomY (inclusive) with the block. */
        void add(BlockState blockState, Holder<Biome> blockBiome, int bottomY) {
            if (blockState != this.state) {
                this.flush();
                this.state = blockState;
                this.biome = blockBiome;
                this.runTop = this.runBottom;
            }
            this.runBottom = bottomY;
        }

        private void flush() {
            if (this.state != null && this.runTop > this.runBottom) {
                this.points.add(DhApiTerrainDataPoint.create((byte) 0, 0, this.underground ? 0 : 15, this.runBottom, this.runTop,
                        CubicDhWorldGenerator.this.wrap(this.state), CubicDhWorldGenerator.this.wrap(this.biome)));
                if (!this.state.isAir()) {
                    this.underground = true;
                }
            }
        }

        List<DhApiTerrainDataPoint> finish() {
            this.flush();
            return this.points;
        }
    }
}
