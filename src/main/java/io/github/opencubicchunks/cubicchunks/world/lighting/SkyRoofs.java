package io.github.opencubicchunks.cubicchunks.world.lighting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.mojang.logging.LogUtils;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Where the sky is stopped in cubes that are not loaded: for each 16x16 column, the cubes that roof it over, each with the height of the
 * topmost edge vanilla calls occluded in each of its block columns, as the cube was when it was last lit or let go. A cubic column has no
 * top, and walking only the loaded cubes counted everything above them as open sky: a cave whose cubes loaded without the ones over it was
 * lit as if open (and saved so). With these, sky light starts under the highest roof known, loaded or not.
 * <p>
 * Only the cubes that roof something no higher one does are kept (one or two a column, usually). Kept beside the dimension's region
 * folders, in files of 32 x 32 columns; on the client, where the server's light is used, there are none. Any thread.
 */
public final class SkyRoofs {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FORMAT = 1;
    private static final int REGION_SHIFT = 5;
    /** A region kept in memory, clean and not asked about for this long, is let go at the next save. */
    private static final long FORGET_AFTER_MS = 5 * 60_000;
    static final byte NONE = -1;

    /** One cube's roof over one 16x16 column: the cube-local Y of the topmost occluding block in each block column ({@link #NONE} if none). */
    public record Roof(int cubeY, byte[] top) {
        /** Where sky light would start above this roof in a block column (vanilla's lowest source Y), or Integer.MIN_VALUE if it has none there. */
        int sourceY(int localX, int localZ) {
            byte y = this.top[localX + localZ * 16];
            return y == NONE ? Integer.MIN_VALUE : Coords.cubeToMinBlock(this.cubeY) + y + 1;
        }
    }

    private static final Roof[] EMPTY = new Roof[0];

    private final @Nullable Path folder;
    private final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();

    private static final class Region {
        final Long2ObjectOpenHashMap<Roof[]> columns = new Long2ObjectOpenHashMap<>();
        boolean dirty;
        long lastUsed = System.currentTimeMillis();
    }

    /** Kept in {@code dimensionFolder}/cubicchunks_sky, or only in memory without a folder. */
    public SkyRoofs(@Nullable Path dimensionFolder) {
        this.folder = dimensionFolder == null ? null : dimensionFolder.resolve("cubicchunks_sky");
    }

    /** The roofs over a 16x16 column, highest first (an array never changed after it is given out). */
    public synchronized Roof[] column(int chunkX, int chunkZ) {
        Roof[] roofs = this.region(chunkX, chunkZ).columns.get(ChunkPos.pack(chunkX, chunkZ));
        return roofs == null ? EMPTY : roofs;
    }

    /** Notes the cube's blocks as they are now (it was lit, or is leaving). */
    public void record(CubeAccess cube) {
        CubePos cubePos = cube.cc_getCubePos();
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                byte[] top = topOccluders(cube, dx, dz);
                this.put(Coords.cubeToSection(cubePos.getX(), dx), Coords.cubeToSection(cubePos.getZ(), dz), cubePos.getY(), top);
            }
        }
    }

    /** The cube-local Y of the topmost occluded edge in each block column of one of the cube's 16x16 columns, from open air above the cube. */
    private static byte[] topOccluders(CubeAccess cube, int sectionInCubeX, int sectionInCubeZ) {
        byte[] top = new byte[256];
        Arrays.fill(top, NONE);
        LevelChunkSection[] sections = cube.getSections();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                BlockState topState = Blocks.AIR.defaultBlockState();
                search:
                for (int sectionInCubeY = CubicConstants.DIAMETER_IN_SECTIONS - 1; sectionInCubeY >= 0; sectionInCubeY--) {
                    LevelChunkSection section = sections[Coords.sectionToIndex(sectionInCubeX, sectionInCubeY, sectionInCubeZ)];
                    if (section.hasOnlyAir()) {
                        topState = Blocks.AIR.defaultBlockState();
                        continue;
                    }
                    for (int y = 15; y >= 0; y--) {
                        BlockState bottomState = section.getBlockState(localX, y, localZ);
                        if (CubicSkyLightSources.isEdgeOccluded(topState, bottomState)) {
                            top[localX + localZ * 16] = (byte) (sectionInCubeY * 16 + y);
                            break search;
                        }
                        topState = bottomState;
                    }
                }
            }
        }
        return top;
    }

    /** Over a 16x16 column, the roofs above a cube Y become these (the client, from the server, with a cube it is sent). */
    public synchronized void replaceAbove(int chunkX, int chunkZ, int cubeY, List<Roof> above) {
        Region region = this.region(chunkX, chunkZ);
        long key = ChunkPos.pack(chunkX, chunkZ);
        Roof[] old = region.columns.get(key);
        List<Roof> roofs = new ArrayList<>();
        if (old != null) {
            for (Roof roof : old) {
                if (roof.cubeY() <= cubeY) {
                    roofs.add(roof);
                }
            }
        }
        for (Roof roof : above) {
            if (roof.cubeY() > cubeY && roof.top().length == 256) {
                roofs.add(roof);
            }
        }
        roofs.sort((a, b) -> Integer.compare(b.cubeY(), a.cubeY()));
        Roof[] kept = prune(roofs);
        if (kept.length == 0) {
            region.columns.remove(key);
        } else {
            region.columns.put(key, kept);
        }
        region.dirty = true;
    }

    private synchronized void put(int chunkX, int chunkZ, int cubeY, byte[] top) {
        Region region = this.region(chunkX, chunkZ);
        long key = ChunkPos.pack(chunkX, chunkZ);
        Roof[] old = region.columns.get(key);
        List<Roof> roofs = new ArrayList<>(old == null ? List.of() : Arrays.asList(old));
        boolean hasAny = false;
        for (byte y : top) {
            if (y != NONE) {
                hasAny = true;
                break;
            }
        }
        int at = -1;
        for (int i = 0; i < roofs.size(); i++) {
            if (roofs.get(i).cubeY() == cubeY) {
                at = i;
                break;
            }
        }
        if (at >= 0 && hasAny && Arrays.equals(roofs.get(at).top(), top)) {
            return; // as it was
        }
        if (at >= 0) {
            roofs.remove(at);
        } else if (!hasAny) {
            return; // nothing to forget
        }
        if (hasAny) {
            roofs.add(new Roof(cubeY, top));
            roofs.sort((a, b) -> Integer.compare(b.cubeY(), a.cubeY()));
        }
        Roof[] kept = prune(roofs);
        if (kept.length == 0) {
            region.columns.remove(key);
        } else {
            region.columns.put(key, kept);
        }
        region.dirty = true;
    }

    /** The roofs, highest first, that roof some block column no higher one does. */
    private static Roof[] prune(List<Roof> highestFirst) {
        boolean[] covered = new boolean[256];
        int coveredCount = 0;
        List<Roof> kept = new ArrayList<>();
        for (Roof roof : highestFirst) {
            if (coveredCount == 256) {
                break;
            }
            boolean adds = false;
            for (int i = 0; i < 256; i++) {
                if (roof.top()[i] != NONE && !covered[i]) {
                    covered[i] = true;
                    coveredCount++;
                    adds = true;
                }
            }
            if (adds) {
                kept.add(roof);
            }
        }
        return kept.toArray(EMPTY);
    }

    // ---- on disk ----------------------------------------------------------------------------------------------------------------------

    private Region region(int chunkX, int chunkZ) {
        int regionX = chunkX >> REGION_SHIFT;
        int regionZ = chunkZ >> REGION_SHIFT;
        long key = ChunkPos.pack(regionX, regionZ);
        Region region = this.regions.get(key);
        if (region == null) {
            region = this.read(regionX, regionZ);
            this.regions.put(key, region);
        }
        region.lastUsed = System.currentTimeMillis();
        return region;
    }

    private @Nullable Path file(int regionX, int regionZ) {
        return this.folder == null ? null : this.folder.resolve("r." + regionX + "." + regionZ + ".sky");
    }

    private Region read(int regionX, int regionZ) {
        Region region = new Region();
        Path file = this.file(regionX, regionZ);
        if (file == null || !Files.exists(file)) {
            return region;
        }
        try (InputStream raw = Files.newInputStream(file); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            int format = in.readInt();
            if (format != FORMAT) {
                LOGGER.warn("Sky roofs {} are in an unknown format {}; working them out again", file, format);
                return region;
            }
            int columns = in.readInt();
            for (int c = 0; c < columns; c++) {
                int local = in.readUnsignedShort();
                int count = in.readUnsignedShort();
                Roof[] roofs = new Roof[count];
                for (int i = 0; i < count; i++) {
                    int cubeY = in.readInt();
                    byte[] top = new byte[256];
                    in.readFully(top);
                    roofs[i] = new Roof(cubeY, top);
                }
                int chunkX = (regionX << REGION_SHIFT) + (local >> REGION_SHIFT);
                int chunkZ = (regionZ << REGION_SHIFT) + (local & ((1 << REGION_SHIFT) - 1));
                region.columns.put(ChunkPos.pack(chunkX, chunkZ), roofs);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read sky roofs {}; working them out again as cubes load", file, e);
        }
        return region;
    }

    /** Writes the regions that changed, and lets go of those not asked about for a while. */
    public synchronized void save() {
        long now = System.currentTimeMillis();
        var it = this.regions.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Region region = entry.getValue();
            if (region.dirty && this.folder != null) {
                int regionX = ChunkPos.getX(entry.getLongKey());
                int regionZ = ChunkPos.getZ(entry.getLongKey());
                try {
                    this.write(regionX, regionZ, region);
                    region.dirty = false;
                } catch (IOException | RuntimeException e) {
                    LOGGER.error("Could not save sky roofs of region {}, {}", regionX, regionZ, e);
                }
            } else if ((this.folder == null || !region.dirty) && now - region.lastUsed > FORGET_AFTER_MS) {
                it.remove();
            }
        }
    }

    private void write(int regionX, int regionZ, Region region) throws IOException {
        Path file = this.file(regionX, regionZ);
        Files.createDirectories(file.getParent());
        if (region.columns.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream raw = Files.newOutputStream(temp); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeInt(FORMAT);
            out.writeInt(region.columns.size());
            for (var entry : region.columns.long2ObjectEntrySet()) {
                int chunkX = ChunkPos.getX(entry.getLongKey());
                int chunkZ = ChunkPos.getZ(entry.getLongKey());
                int mask = (1 << REGION_SHIFT) - 1;
                out.writeShort(((chunkX & mask) << REGION_SHIFT) | (chunkZ & mask));
                Roof[] roofs = entry.getValue();
                out.writeShort(roofs.length);
                for (Roof roof : roofs) {
                    out.writeInt(roof.cubeY());
                    out.write(roof.top());
                }
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
