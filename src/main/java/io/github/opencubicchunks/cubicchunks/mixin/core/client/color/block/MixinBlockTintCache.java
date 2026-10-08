package io.github.opencubicchunks.cubicchunks.mixin.core.client.color.block;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.client.color.block.CubicBlockTintCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** See {@link CubicBlockTintCache}: as vanilla's invalidateForChunk, for the layers a cube can change. */
@Mixin(BlockTintCache.class)
public abstract class MixinBlockTintCache implements CubicBlockTintCache {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<?> cache;
    @Shadow @Final private ReentrantReadWriteLock lock;

    @Override public void cc_invalidateForCube(CubePos cubePos) {
        int minY = cubePos.minCubeY() - BIOME_REACH;
        int maxY = cubePos.maxCubeY() + BIOME_REACH;
        int minChunkX = Coords.cubeToSection(cubePos.getX(), 0) - 1;
        int minChunkZ = Coords.cubeToSection(cubePos.getZ(), 0) - 1;
        int maxChunkX = Coords.cubeToSection(cubePos.getX(), 0) + (CubicConstants.DIAMETER_IN_SECTIONS - 1) + 1;
        int maxChunkZ = Coords.cubeToSection(cubePos.getZ(), 0) + (CubicConstants.DIAMETER_IN_SECTIONS - 1) + 1;
        this.lock.readLock().lock();
        try {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    Object column = this.cache.get(ChunkPos.pack(chunkX, chunkZ));
                    if (column != null) {
                        ((CubicBlockTintCache.Column) column).cc_removeLayers(minY, maxY);
                    }
                }
            }
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
