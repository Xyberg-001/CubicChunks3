package io.github.opencubicchunks.cubicchunks.api;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Generates a cubic level's cubes (registered through {@link CubicApi#registerCubeGenerator}): the terrain (blocks and biomes) at the step
 * where vanilla fills a chunk from noise, then the decoration (trees, buildings, entities) at the step where vanilla places features.
 */
@FunctionalInterface
public interface CubeGenerator {
    /**
     * Fills the cube (see {@link CubeTerrain}). Called on a world generation thread; the cube may be finished later, when the future
     * completes (on any thread), for instance after map data has been downloaded. Must not touch the level's other cubes.
     */
    CompletableFuture<?> generate(CubeTerrain cube);

    /**
     * Decorates the cube, at the features step: the cubes around it have their terrain by then. Only the cube itself is written to, as in
     * {@link #generate}, but block entities and entities can be added too. Something that crosses cubes (a tree on a cube's edge) is made
     * by each cube it touches keeping its own part, so a generator decorating whole areas must give every cube the same answer. Called on
     * a world generation thread; may finish later, as {@link #generate} may.
     */
    default CompletableFuture<?> decorate(CubeTerrain cube) {
        return CompletableFuture.completedFuture(null);
    }

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
