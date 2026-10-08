package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

/**
 * Light for the cubes of a cubic level, on either side. The side's own vanilla light engine does the propagation; it reads the level through
 * {@link CubicLightColumn}s (blocks, light sources and sky sources from the cubes a {@link CubeLightView} gives). Sky light enters each column
 * below the lowest opaque edge under its highest cube: what lies above those cubes, or in cubes not given, counts as open sky.
 * <p>
 * {@link #onCubeLoaded} reads and resets sky sources while the engine works, so on the server it runs as a task of the light thread, with
 * engine calls applied at once ({@code immediate}); the rest may come from any thread and goes through {@code queued}. On the client both are
 * the level's engine.
 */
public final class CubicLight {
    private final CubeLightView view;
    private final Supplier<CubeLightEngine> immediate;
    private final Supplier<CubeLightEngine> queued;
    private final ConcurrentHashMap<Long, CubicLightColumn> columns = new ConcurrentHashMap<>();

    public CubicLight(CubeLightView view, Supplier<CubeLightEngine> immediate, Supplier<CubeLightEngine> queued) {
        this.view = view;
        this.immediate = immediate;
        this.queued = queued;
    }

    public CubicLightColumn column(int chunkX, int chunkZ) {
        return this.columns.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> new CubicLightColumn(this.view, chunkX, chunkZ));
    }

    /**
     * A cube is ready for light (it arrived, or reached its light step): its non-empty sections join the light engine's storage, and the sky and
     * block light of the four columns it spans is spread again, as vanilla does for a chunk. Where the cube roofs over sky that was lit before,
     * the light below is taken back.
     */
    public void onCubeLoaded(CubeAccess cube) {
        CubeLightEngine engine = this.immediate.get();
        CubePos cubePos = cube.cc_getCubePos();
        LevelChunkSection[] sections = cube.getSections();
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dy = 0; dy < CubicConstants.DIAMETER_IN_SECTIONS; dy++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    engine.updateSectionStatus(SectionPos.of(Coords.cubeToSection(cubePos.getX(), dx), Coords.cubeToSection(cubePos.getY(), dy),
                            Coords.cubeToSection(cubePos.getZ(), dz)), sections[Coords.sectionToIndex(dx, dy, dz)].hasOnlyAir());
                }
            }
        }
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                int chunkX = Coords.cubeToSection(cubePos.getX(), dx);
                int chunkZ = Coords.cubeToSection(cubePos.getZ(), dz);
                CubicSkyLightSources sources = this.column(chunkX, chunkZ).sources();
                int[] before = sources.known();
                sources.forgetAll();
                int minX = SectionPos.sectionToBlockCoord(chunkX);
                int minZ = SectionPos.sectionToBlockCoord(chunkZ);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int old = before[x + z * 16];
                        if (old == CubicSkyLightSources.UNKNOWN) {
                            continue; // never used, so nothing was lit from it
                        }
                        int now = sources.getLowestSourceY(x, z);
                        if (now > old) {
                            // the sky now starts higher: checking a block of the column takes back the sources below the new start
                            engine.checkBlock(new BlockPos(minX + x, now == ChunkSkyLightSources.NEGATIVE_INFINITY ? old : now, minZ + z));
                        }
                    }
                }
                engine.propagateLightSources(new ChunkPos(chunkX, chunkZ));
            }
        }
    }

    /** A cube has left: its sections' light goes, and the sky over its columns is worked out again when next needed. */
    public void onCubeUnloaded(CubeAccess cube) {
        CubeLightEngine engine = this.queued.get();
        CubePos cubePos = cube.cc_getCubePos();
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                int chunkX = Coords.cubeToSection(cubePos.getX(), dx);
                int chunkZ = Coords.cubeToSection(cubePos.getZ(), dz);
                for (int dy = 0; dy < CubicConstants.DIAMETER_IN_SECTIONS; dy++) {
                    SectionPos section = SectionPos.of(chunkX, Coords.cubeToSection(cubePos.getY(), dy), chunkZ);
                    engine.queueSectionData(LightLayer.BLOCK, section, null);
                    engine.queueSectionData(LightLayer.SKY, section, null);
                    engine.updateSectionStatus(section, true);
                }
                CubicLightColumn column = this.columns.get(ChunkPos.pack(chunkX, chunkZ));
                if (column != null) {
                    column.sources().forgetAll();
                }
            }
        }
    }

    /** A block changed how it passes or gives light (vanilla's LevelChunk.setBlockState does the same for a chunk). */
    public void onBlockChanged(BlockPos pos) {
        CubicLightColumn column = this.columns.get(ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
        if (column != null) {
            column.sources().forget(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getZ()));
        }
        this.queued.get().checkBlock(pos);
    }
}
