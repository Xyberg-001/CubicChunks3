package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.util.Set;
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
    /** Columns whose queued light the engine keeps while cubes with saved light load into them, and how many such cubes are loading. */
    private final ConcurrentHashMap<Long, Integer> retainedColumns = new ConcurrentHashMap<>();
    private final Set<Long> retainedCubes = ConcurrentHashMap.newKeySet();

    public CubicLight(CubeLightView view, Supplier<CubeLightEngine> immediate, Supplier<CubeLightEngine> queued) {
        this.view = view;
        this.immediate = immediate;
        this.queued = queued;
    }

    public CubicLightColumn column(int chunkX, int chunkZ) {
        return this.columns.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> new CubicLightColumn(this.view, chunkX, chunkZ));
    }

    /**
     * A cube is ready for light (it arrived, or reached its light step). Its non-empty sections join the light engine's storage (the server
     * did that in the cube's initializeLight step already), and then:
     * <ul>
     * <li>the sky light under any column the cube roofs over is taken back (lit while the cube was not there, as open to the sky; see
     * {@link SkySourceRemoval});</li>
     * <li>a cube with saved light ({@code lighted}) keeps it, as vanilla keeps a chunk's: the light was worked out when what lay around and
     * above the cube was there, which may not be loaded now, so its columns are only switched on;</li>
     * <li>otherwise the sky and block light of the four columns it spans is spread again, as vanilla does for a chunk.</li>
     * </ul>
     */
    public void onCubeLoaded(CubeAccess cube, boolean lighted) {
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
        int cubeMinY = cubePos.minCubeY();
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                int chunkX = Coords.cubeToSection(cubePos.getX(), dx);
                int chunkZ = Coords.cubeToSection(cubePos.getZ(), dz);
                CubicSkyLightSources sources = this.column(chunkX, chunkZ).sources();
                sources.forgetAll();
                int minX = SectionPos.sectionToBlockCoord(chunkX);
                int minZ = SectionPos.sectionToBlockCoord(chunkZ);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (sources.getLowestSourceY(x, z) >= cubeMinY) {
                            // the sky now stops in this cube (or at its top): whatever below was lit as open sky is not
                            engine.removeSkySourcesBelow(minX + x, minZ + z, cubeMinY - 1);
                        }
                    }
                }
                if (lighted) {
                    engine.setLightEnabled(new ChunkPos(chunkX, chunkZ), true);
                } else {
                    engine.propagateLightSources(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
    }

    /**
     * A cube's saved light is about to be queued (as vanilla's SerializableChunkData.read does for a chunk): until the cube's light is
     * initialised, the engine keeps queued light for its columns even where a section drops out of storage meanwhile.
     */
    public void retainForLoad(CubePos cubePos) {
        if (!this.retainedCubes.add(cubePos.asLong())) {
            return;
        }
        forEachColumn(cubePos, (chunkX, chunkZ) -> {
            if (this.retainedColumns.merge(ChunkPos.pack(chunkX, chunkZ), 1, Integer::sum) == 1) {
                this.queued.get().retainData(new ChunkPos(chunkX, chunkZ), true);
            }
        });
    }

    /** The cube's light is initialised (its initializeLight step, on the light thread): its columns need not keep queued light for it. */
    public void releaseAfterLoad(CubePos cubePos) {
        this.release(cubePos, this.immediate.get());
    }

    private void release(CubePos cubePos, CubeLightEngine engine) {
        if (!this.retainedCubes.remove(cubePos.asLong())) {
            return;
        }
        forEachColumn(cubePos, (chunkX, chunkZ) -> {
            if (this.retainedColumns.merge(ChunkPos.pack(chunkX, chunkZ), -1, (a, b) -> a + b == 0 ? null : a + b) == null) {
                engine.retainData(new ChunkPos(chunkX, chunkZ), false);
            }
        });
    }

    private static void forEachColumn(CubePos cubePos, IntBiConsumer action) {
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                action.accept(Coords.cubeToSection(cubePos.getX(), dx), Coords.cubeToSection(cubePos.getZ(), dz));
            }
        }
    }

    private interface IntBiConsumer {
        void accept(int a, int b);
    }

    /** Where sky light starts in a block column, and which cube Ys it was worked out from (for /cubicchunks light). */
    public String describeSky(int x, int z) {
        int chunkX = SectionPos.blockToSectionCoord(x);
        int chunkZ = SectionPos.blockToSectionCoord(z);
        int lowest = this.column(chunkX, chunkZ).sources().getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
        StringBuilder cubes = new StringBuilder();
        for (CubeAccess cube : this.view.cubesTopDown(Coords.blockToCube(x), Coords.blockToCube(z))) {
            cubes.append(cubes.isEmpty() ? "" : ",").append(cube.cc_getCubePos().getY());
        }
        return "sky enters at " + (lowest == Integer.MIN_VALUE ? "-inf" : Integer.toString(lowest)) + " over cubes Y " + cubes;
    }

    /** A cube has left: its sections' light goes, and the sky over its columns is worked out again when next needed. */
    public void onCubeUnloaded(CubeAccess cube) {
        CubeLightEngine engine = this.queued.get();
        CubePos cubePos = cube.cc_getCubePos();
        this.release(cubePos, engine); // in case it leaves before its light was initialised
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
