package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubeLightView;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * The cubes the server holds, for light: a cube counts once its generation has got as far as vanilla lights from (features placed, the step
 * before INITIALIZE_LIGHT), as ServerChunkCache.getChunkForLighting does for chunks. Read from the light thread, through the visible holders.
 */
public final class ServerCubeLightView implements CubeLightView {
    private static final ChunkStatus LIGHT_FROM = ChunkStatus.INITIALIZE_LIGHT.getParent();

    private final ServerLevel level;
    private final LongFunction<ChunkHolder> visibleHolders;

    public ServerCubeLightView(ServerLevel level, LongFunction<ChunkHolder> visibleHolders) {
        this.level = level;
        this.visibleHolders = visibleHolders;
    }

    @Override public @Nullable CubeAccess cube(int cubeX, int cubeY, int cubeZ) {
        ChunkHolder holder = this.visibleHolders.apply(CloPos.cubeAsLong(cubeX, cubeY, cubeZ));
        return holder == null ? null : ((GenerationCloHolder) holder).cc_getCubeIfPresentUnchecked(LIGHT_FROM);
    }

    @Override public List<CubeAccess> cubesTopDown(int cubeX, int cubeZ) {
        List<CubeAccess> column = new ArrayList<>();
        for (int cubeY = Coords.blockToCube(CubicHeight.maxY(this.level)); cubeY >= Coords.blockToCube(CubicHeight.minY(this.level)); cubeY--) {
            CubeAccess cube = this.cube(cubeX, cubeY, cubeZ);
            if (cube != null) {
                column.add(cube);
            }
        }
        return column;
    }

    @Override public LevelHeightAccessor heightAccessor() {
        return this.level;
    }
}
