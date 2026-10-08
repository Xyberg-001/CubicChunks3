package io.github.opencubicchunks.cubicchunks.server.level.progress;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The integrated server's view of chunk loading for the loading screen (vanilla's, which it wraps, reads chunk statuses around the level's
 * focus), with the cubes around it as well when the level is cubic. The focus Y is the level's spawn height: vanilla only gives a column.
 * Read from the client thread, through the chunk map's visible holders, as vanilla's view is.
 */
public final class CubicChunkLoadStatusView implements ChunkLoadStatusView {
    private final MinecraftServer server;
    private final ChunkLoadStatusView vanilla;
    private volatile @Nullable ServerLevel level;
    private volatile int centerCubeX;
    private volatile int centerCubeY;
    private volatile int centerCubeZ;

    public CubicChunkLoadStatusView(MinecraftServer server, ChunkLoadStatusView vanilla) {
        this.server = server;
        this.vanilla = vanilla;
    }

    @Override public void moveTo(ResourceKey<Level> dimension, ChunkPos centerChunk) {
        this.vanilla.moveTo(dimension, centerChunk);
        ServerLevel level = this.server.getLevel(dimension);
        this.level = level;
        this.centerCubeX = Coords.sectionToCube(centerChunk.x());
        this.centerCubeZ = Coords.sectionToCube(centerChunk.z());
        this.centerCubeY = level == null ? 0 : Coords.blockToCube(level.getRespawnData().globalPos().pos().getY());
    }

    @Override public @Nullable ChunkStatus get(int x, int z) {
        return this.vanilla.get(x, z);
    }

    @Override public int radius() {
        return this.vanilla.radius();
    }

    public boolean cc_isCubic() {
        ServerLevel level = this.level;
        return level != null && ((CanBeCubic) level).cc_isCubic();
    }

    /** How many cubes the view reaches from its centre, horizontally (covering vanilla's radius in chunks). */
    public int cc_cubeRadius() {
        return (this.vanilla.radius() + 1) / 2;
    }

    /** The status of the cube at an offset from the centre cube, or null where none is held yet. */
    public @Nullable ChunkStatus cc_status(int dx, int dy, int dz) {
        ServerLevel level = this.level;
        if (level == null) {
            return null;
        }
        return level.getChunkSource().chunkMap.getLatestStatus(CloPos.cubeAsLong(this.centerCubeX + dx, this.centerCubeY + dy, this.centerCubeZ + dz));
    }
}
