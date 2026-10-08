package io.github.opencubicchunks.cubicchunks.client.multiplayer;

import static io.github.notstirred.dasm.api.annotations.transform.Visibility.PUBLIC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.levelgen.Heightmap;

public interface ClientCubeCache extends CubeSource {
    // TODO (P2) we might want a version of the vanilla replaceWithPacketData with a different signature for handling chunks, since we only need
    // heightmap data with CC

    void cc_drop(CubePos chunkPos);

    void cc_replaceBiomes(int x, int y, int z, FriendlyByteBuf buffer);

    @Nullable LevelCube cc_replaceWithPacketData(
            int x, int y, int z, FriendlyByteBuf buffer, Map<Heightmap.Types, long[]> map,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    );

    void cc_updateViewCenter(int x, int y, int z);

    void cc_updateViewRadius(int viewDistance);

    /** Whether this cache serves a cubic level (it then holds cubes, and columns do not render). */
    boolean cc_isCubic();

    /**
     * Reports a cube arriving or leaving to the renderer, through the sets 26.3's client chunk storage hands it each frame: the cube's packed
     * position joins or leaves the loaded set, and each of its sections is marked empty or not.
     */
    void cc_trackCube(LevelCube cube, boolean loaded);

    /** The cube Y the held cubes are centred on, and how many cubes they reach from it. */
    int cc_cubeViewCenterY();

    int cc_cubeViewRadius();

    // Fields and methods on this are public so they can be accessed from MixinClientChunkCache and tests; they should not be used anywhere else
    // (This has to be here since we can't add inner classes with mixin)
    @Dasm(ChunkToCubeSet.class)
    final class Storage {
        public final AtomicReferenceArray<LevelCube> chunks;
        public final int cubeRadius;
        private final int viewRange;
        public volatile int viewCenterX;
        public volatile int viewCenterY;
        public volatile int viewCenterZ;
        public int chunkCount;
        // Field added since we can't get it off ClientChunkCache since this is no longer an inner class
        final ClientLevel level;

        public Storage(int chunkRadius, ClientLevel clientLevel) {
            this.cubeRadius = chunkRadius;
            this.viewRange = chunkRadius * 2 + 1;
            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange * this.viewRange);
            this.level = clientLevel;
        }

        public int getIndex(int x, int y, int z) {
            return Math.floorMod(z, this.viewRange) * this.viewRange * this.viewRange + Math.floorMod(y, this.viewRange) * this.viewRange
                    + Math.floorMod(x, this.viewRange);
        }

        public void replace(int chunkIndex, @Nullable LevelCube chunk) {
            LevelCube levelchunk = this.chunks.getAndSet(chunkIndex, chunk);
            if (levelchunk != null) {
                --this.chunkCount;
                this.tracking().cc_trackCube(levelchunk, false);
                ((CubicClientLevel) this.level).cc_onCubeUnloaded(levelchunk);
            }

            if (chunk != null) {
                ++this.chunkCount;
                this.tracking().cc_trackCube(chunk, true);
            }
        }

        public void drop(int chunkIndex, LevelCube chunk) {
            if (this.chunks.compareAndSet(chunkIndex, chunk, null)) {
                this.chunkCount--;
                this.tracking().cc_trackCube(chunk, false);
                ((CubicClientLevel) this.level).cc_onCubeUnloaded(chunk);
            }
        }

        /** New packet data for a cube already held: its sections may have filled or emptied. */
        public void refreshEmptySections(LevelCube chunk) {
            this.tracking().cc_trackCube(chunk, true);
        }

        private ClientCubeCache tracking() {
            return (ClientCubeCache) this.level.getChunkSource();
        }

        public boolean inRange(int x, int y, int z) {
            return Math.abs(x - this.viewCenterX) <= this.cubeRadius && Math.abs(y - this.viewCenterY) <= this.cubeRadius
                    && Math.abs(z - this.viewCenterZ) <= this.cubeRadius;
        }

        @TransformFromMethod(owner = @Ref(ClientChunkCache.Storage.class), value = "getChunk(I)Lnet/minecraft/world/level/chunk/LevelChunk;", visibility = PUBLIC)
        public native @Nullable LevelCube getChunk(int chunkIndex);

        public void dumpChunks(String filePath) {
            // TODO reimplement debug code
        }
    }
}
