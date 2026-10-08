package io.github.opencubicchunks.cubicchunks.client.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * Light for a cubic level on the client, worked out there from the cubes it holds (the server sends cubes without light). The client's own
 * vanilla light engine does the propagation; it reads the level through {@link CubicLightColumn}s, whose blocks, light sources and sky sources
 * come from the held cubes. Sky light enters each column below the lowest opaque edge under the highest held cube: what lies above the held
 * cubes, or in cubes not held, counts as open sky.
 * <p>
 * Everything here runs on the client thread, from the level's light-update queue or from block changes.
 */
public final class CubicClientLight {
    private final ClientLevel level;
    private final ClientCubeCache cubes;
    private final Long2ObjectOpenHashMap<CubicLightColumn> columns = new Long2ObjectOpenHashMap<>();

    public CubicClientLight(ClientLevel level, ClientCubeCache cubes) {
        this.level = level;
        this.cubes = cubes;
    }

    ClientLevel level() {
        return this.level;
    }

    @Nullable LevelCube cube(int cubeX, int cubeY, int cubeZ) {
        return this.cubes.cc_getCube(cubeX, cubeY, cubeZ, false);
    }

    int topCubeY() {
        return this.cubes.cc_cubeViewCenterY() + this.cubes.cc_cubeViewRadius();
    }

    int bottomCubeY() {
        return this.cubes.cc_cubeViewCenterY() - this.cubes.cc_cubeViewRadius();
    }

    public CubicLightColumn column(int chunkX, int chunkZ) {
        return this.columns.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> new CubicLightColumn(this, chunkX, chunkZ));
    }

    /**
     * A cube has arrived: its sections join the light engine's storage, and the sky and block light of the columns it spans is spread again
     * (as vanilla does for a chunk). Where the cube roofs over sky that was lit before, the light below is taken back.
     */
    public void onCubeLoaded(LevelCube cube) {
        LevelLightEngine engine = this.level.getLightEngine();
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
                engine.propagateLightSources(new ChunkPos(chunkX, chunkZ));
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
            }
        }
    }

    /** A cube has left: its sections' light goes, and the sky over its columns is worked out again when next needed. */
    public void onCubeUnloaded(LevelCube cube) {
        LevelLightEngine engine = this.level.getLightEngine();
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
        this.level.getLightEngine().checkBlock(pos);
    }
}
