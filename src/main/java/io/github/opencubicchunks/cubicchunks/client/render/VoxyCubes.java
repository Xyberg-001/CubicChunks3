package io.github.opencubicchunks.cubicchunks.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSections;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * Hands a cubic client's cubes to Voxy, which builds its distant terrain from the chunks a client loads and unloads: a cubic client holds
 * no chunks, so its cubes go in section by section instead, as Sodium starts rendering them (their light is in by then), as the client
 * drops them (their latest state) and as their blocks change. Voxy is called through its public static methods, looked up once (it is not on a maven to compile
 * against): {@code WorldIdentifier.of(Level)} and {@code VoxelIngestService.rawIngest(WorldIdentifier, LevelChunkSection, sectionX,
 * sectionY, sectionZ, blockLight, skyLight)}. If they are not there (no Voxy, or a version without them) nothing is handed over.
 * <p>
 * Voxy keeps a section's Y in few bits (its 32-block sections reach Y -4096..4095 in 0.2.20): sections beyond are not handed over, as
 * they would land at a wrapped height. The range is found by round trips through {@code WorldEngine.getWorldSectionId} and {@code getY}.
 */
public final class VoxyCubes {
    private static final @Nullable MethodHandle WORLD_IDENTIFIER;
    private static final @Nullable MethodHandle RAW_INGEST;
    /** The chunk sections' Y Voxy holds (its own sections are two chunk sections tall). */
    private static final int MIN_SECTION_Y;
    private static final int MAX_SECTION_Y;

    static {
        MethodHandle worldIdentifier = null;
        MethodHandle rawIngest = null;
        int maxVoxySectionY = 0;
        if (FabricLoader.getInstance().isModLoaded("voxy")) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> identifier = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
                Class<?> ingest = Class.forName("me.cortex.voxy.common.world.service.VoxelIngestService");
                worldIdentifier = lookup.findStatic(identifier, "of", MethodType.methodType(identifier, Level.class));
                rawIngest = lookup.findStatic(ingest, "rawIngest", MethodType.methodType(boolean.class, identifier, LevelChunkSection.class,
                        int.class, int.class, int.class, DataLayer.class, DataLayer.class));
                Class<?> engine = Class.forName("me.cortex.voxy.common.world.WorldEngine");
                MethodHandle sectionId = lookup.findStatic(engine, "getWorldSectionId", MethodType.methodType(long.class, int.class, int.class,
                        int.class, int.class));
                MethodHandle getY = lookup.findStatic(engine, "getY", MethodType.methodType(int.class, long.class));
                // the largest 2^n - 1 whose section Y and its negative counterpart come back unchanged
                for (int bits = 1; bits < 31; bits++) {
                    int y = (1 << bits) - 1;
                    if ((int) getY.invoke((long) sectionId.invoke(0, 0, y, 0)) != y || (int) getY.invoke((long) sectionId.invoke(0, 0, -y - 1, 0)) != -y - 1) {
                        break;
                    }
                    maxVoxySectionY = y;
                }
            } catch (Throwable e) {
                CubicChunks.LOGGER.warn("Voxy is installed but its ingest methods were not found; it gets no terrain from cubic worlds", e);
                worldIdentifier = null;
                rawIngest = null;
            }
        }
        WORLD_IDENTIFIER = worldIdentifier;
        RAW_INGEST = maxVoxySectionY > 0 ? rawIngest : null;
        MIN_SECTION_Y = (-maxVoxySectionY - 1) * 2;
        MAX_SECTION_Y = maxVoxySectionY * 2 + 1;
        if (RAW_INGEST != null) {
            CubicChunks.LOGGER.info("Voxy gets cubic worlds' terrain from Y {} to {}", MIN_SECTION_Y * 16, MAX_SECTION_Y * 16 + 15);
        }
    }

    private VoxyCubes() {
    }

    public static boolean active() {
        return RAW_INGEST != null;
    }

    /** The lowest chunk section Y Voxy holds. */
    public static int minSectionY() {
        return MIN_SECTION_Y;
    }

    /** The highest chunk section Y Voxy holds. */
    public static int maxSectionY() {
        return MAX_SECTION_Y;
    }

    /** Hands each of the cube's sections, with its light, to Voxy (client thread). */
    public static void ingest(Level level, LevelCube cube) {
        if (RAW_INGEST == null || WORLD_IDENTIFIER == null) {
            return;
        }
        try {
            Object world = WORLD_IDENTIFIER.invoke(level);
            if (world == null) {
                return;
            }
            CubePos cubePos = cube.cc_getCubePos();
            LevelChunkSection[] sections = cube.getSections();
            for (int i = 0; i < sections.length; i++) {
                ingest(world, level, sections[i], CubeSections.sectionPosOf(cubePos, i));
            }
        } catch (Throwable e) {
            CubicChunks.LOGGER.error("Voxy did not take cube {}", cube.cc_getCubePos(), e);
        }
    }

    /**
     * Hands the section holding a changed block to Voxy (client thread). Voxy does this itself on a block change, reading the section from
     * the block's chunk, which a cubic client does not hold; this reads it from the cube.
     */
    public static void ingestBlockChange(Level level, BlockPos pos) {
        if (RAW_INGEST == null || WORLD_IDENTIFIER == null) {
            return;
        }
        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        LevelCube cube = SodiumCubes.cubeOfSection(level, sectionX, sectionY, sectionZ);
        if (cube == null) {
            return;
        }
        try {
            Object world = WORLD_IDENTIFIER.invoke(level);
            if (world != null) {
                LevelChunkSection section = cube.getSections()[Coords.sectionToIndex(Coords.cubeLocalSection(sectionX), Coords.cubeLocalSection(sectionY),
                        Coords.cubeLocalSection(sectionZ))];
                ingest(world, level, section, SectionPos.of(sectionX, sectionY, sectionZ));
            }
        } catch (Throwable e) {
            CubicChunks.LOGGER.error("Voxy did not take the change at {}", pos, e);
        }
    }

    /** One section, with its light, if Voxy holds its height. */
    private static void ingest(Object world, Level level, LevelChunkSection section, SectionPos pos) throws Throwable {
        if (pos.y() < MIN_SECTION_Y || pos.y() > MAX_SECTION_Y) {
            return;
        }
        LevelLightEngine light = level.getLightEngine();
        DataLayer blockLight = light.getLayerListener(LightLayer.BLOCK).getDataLayerData(pos);
        DataLayer skyLight = light.getLayerListener(LightLayer.SKY).getDataLayerData(pos);
        boolean ignored = (boolean) RAW_INGEST.invoke(world, section, pos.x(), pos.y(), pos.z(), blockLight, skyLight);
    }
}
