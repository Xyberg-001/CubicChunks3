package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ServerCubeCache extends CubeSource {
    CompletableFuture<ChunkResult<CubeAccess>> cc_getCubeFuture(int pX, int pY, int pZ, ChunkStatus pChunkStatus, boolean pLoad);

    void cc_blockChanged(BlockPos pos);

    void cc_onLightUpdate(LightLayer pType, SectionPos pPos);

    void cc_addTicket(Ticket ticket, CloPos cloPos);

    void cc_addTicketWithRadius(TicketType ticket, CloPos cloPos, int radius);

    void cc_removeTicketWithRadius(TicketType ticket, CloPos cloPos, int radius);

    boolean cc_updateCloForced(CloPos pPos, boolean pAdd);

    /** The full cube there if it is loaded, else null; never loads it, and may be asked from any thread (it reads the visible holders). */
    @Nullable CubeAccess cc_getFullCubeNow(CubePos pos);

    /** Whether the cube is loaded and in block-ticking range (vanilla asks the distance manager of a chunk). */
    boolean cc_isCubeBlockTicking(CubePos pos);
}
