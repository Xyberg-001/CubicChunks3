package io.github.opencubicchunks.cubicchunks.api;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Generates the terrain of a cubic level's cubes (registered through {@link CubicApi#registerCubeGenerator}): blocks and biomes, at the
 * step where vanilla fills a chunk from noise. Features (trees, ores, structures) come later and are not part of this.
 */
@FunctionalInterface
public interface CubeGenerator {
    /**
     * Fills the cube (see {@link CubeTerrain}). Called on a world generation thread; the cube may be finished later, when the future
     * completes (on any thread), for instance after map data has been downloaded. Must not touch the level's other cubes.
     */
    CompletableFuture<?> generate(CubeTerrain cube);

    /**
     * What a column looks like from afar, for mods that draw distant terrain without the cubes (Distant Horizons): null if this generator
     * cannot tell without generating cubes (those mods then show only cubes that exist). Called on their threads; must be thread safe.
     */
    default @Nullable Surface surface(int x, int z) {
        return null;
    }

    /**
     * A column from afar: {@code block} at the surface Y and {@code fill} below it, water (or another fluid) from the surface up to
     * {@code waterTopY} if that is higher, and air above.
     */
    record Surface(int surfaceY, BlockState block, BlockState fill, int waterTopY, @Nullable BlockState water) {
    }

    /** Makes the cube generator of a cubic level whose chunk generator is the registered kind; null for none (placeholder terrain). */
    @FunctionalInterface
    interface Factory {
        @Nullable CubeGenerator create(ServerLevel level, ChunkGenerator chunkGenerator);
    }
}
