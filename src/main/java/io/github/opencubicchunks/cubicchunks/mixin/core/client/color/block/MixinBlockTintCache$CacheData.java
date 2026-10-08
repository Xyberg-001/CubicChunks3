package io.github.opencubicchunks.cubicchunks.mixin.core.client.color.block;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.github.opencubicchunks.cubicchunks.client.color.block.CubicBlockTintCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A column's cached layers: those in a height range are dropped, to be worked out again when next asked for. */
@Mixin(targets = "net.minecraft.client.color.block.BlockTintCache$CacheData")
public abstract class MixinBlockTintCache$CacheData implements CubicBlockTintCache.Column {
    @Shadow @Final private Int2ObjectArrayMap<int[]> cache;
    @Shadow @Final private ReentrantReadWriteLock lock;

    @Override public void cc_removeLayers(int minY, int maxY) {
        this.lock.writeLock().lock();
        try {
            for (IntIterator it = this.cache.keySet().iterator(); it.hasNext(); ) {
                int y = it.nextInt();
                if (y >= minY && y <= maxY) {
                    it.remove();
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }
}
