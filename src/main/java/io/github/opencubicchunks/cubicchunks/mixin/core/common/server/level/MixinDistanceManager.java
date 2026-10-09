package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import io.github.opencubicchunks.cubicchunks.server.level.CubeYRange;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.world.level.CubicTicketStorage;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.SimulationChunkTracker;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link DistanceManager} contains the main ticket hashmap and stores all the chunks that are loaded.
 * A ticket inside {@link DistanceManager} means that something is either requested to be loaded or is already loaded and needs to stay loaded.
 * It informs {@link ChunkMap} of what chunks it needs to generate/load/unload to satisfy the tickets.
 * <br>
 * <br>
 * This mixin mostly just replaces calls to ChunkPos with CloPos.
 */
@Dasm(value = ChunkToCloSet.class, target = @Ref(DistanceManager.class))
@Mixin(DistanceManager.class)
public abstract class MixinDistanceManager implements MarkableAsCubic, CubeYRange {
    protected boolean cc_isCubic;

    @Shadow @Final private LoadingChunkTracker loadingChunkTracker;
    @Shadow @Final private SimulationChunkTracker simulationChunkTracker;
    @Shadow @Final private DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter;
    @Shadow @Final private DistanceManager.PlayerTicketTracker playerTicketManager;

    @Override public void cc_setCubic() {
        cc_isCubic = true;
        ((MarkableAsCubic) this.loadingChunkTracker).cc_setCubic();
        ((MarkableAsCubic) this.simulationChunkTracker).cc_setCubic();
        ((MarkableAsCubic) this.naturalSpawnChunkCounter).cc_setCubic();
        ((MarkableAsCubic) this.playerTicketManager).cc_setCubic();
    }

    @Override public boolean cc_isCubic() {
        return cc_isCubic;
    }

    /** Only the loading tracker needs the level's heights: it decides which cubes load, whatever the other trackers give them. */
    @Override public void cc_setCubeYRange(int minCubeY, int maxCubeY) {
        ((CubeYRange) this.loadingChunkTracker).cc_setCubeYRange(minCubeY, maxCubeY);
    }

    /**
     * Mob caps scale with the number of chunks near players (vanilla: within 8 chunks, 289 for one player). In a cubic level the counter holds
     * cubes, which would multiply the caps by the cubes up and down; each cube column counts once instead, as the chunks it covers, so the
     * caps keep vanilla's mobs per area (a player's 4 cubes, 128 blocks, cover 18 by 18 chunks: 324, a global monster cap of 78). The
     * columns the counter also holds spread on past the cubes, so they are not counted.
     */
    @WrapOperation(method = "getNaturalSpawnChunkCount", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;size()I"))
    private int cc_columnsNearPlayers(Long2ByteMap tracked, Operation<Integer> original) {
        if (!cc_isCubic) {
            return original.call(tracked);
        }
        LongSet cubeColumns = new LongOpenHashSet();
        for (long clo : tracked.keySet()) {
            if (CloPos.isCube(clo)) {
                cubeColumns.add(ChunkPos.pack(CloPos.extractX(clo), CloPos.extractZ(clo)));
            }
        }
        return cubeColumns.size() * CubicConstants.DIAMETER_IN_SECTIONS * CubicConstants.DIAMETER_IN_SECTIONS;
    }

    /**
     * This function replaces the addTicket call with a cubic version instead.
     * This requires replacing the ChunkPos with a CloPos.
     */
    @WrapWithCondition(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/TicketStorage;addTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V"))
    private boolean cc_replaceTicketTypeOnAddPlayer(TicketStorage instance, Ticket ticket, ChunkPos chunkPos, SectionPos sectionPos) {
        if (!cc_isCubic) {
            return true;
        }
        CloPos cloPos = CloPos.section(sectionPos);
        ((CubicTicketStorage) instance).cc_addTicket(ticket, cloPos);
        return false;
    }

    /**
     * This function replaces the removeTicket call with a cubic version instead.
     * This requires replacing ChunkPos with a CloPos.
     */
    @WrapWithCondition(method = "removePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/TicketStorage;removeTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V"))
    private boolean cc_replaceTicketTypeOnRemovePlayer(TicketStorage instance, Ticket ticket, ChunkPos chunkPos, SectionPos sectionPos) {
        if (!cc_isCubic) {
            return true;
        }
        CloPos cloPos = CloPos.section(sectionPos);
        ((CubicTicketStorage) instance).cc_removeTicket(ticket, cloPos);
        return false;
    }

    /**
     * The original function expects chunkPos.toLong(), but we need to replace it with cloPos.toLong() instead.
     */
    @WrapOperation(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack()J"))
    private long cc_replaceTicketTypeOnAddPlayer(ChunkPos chunkPos, Operation<Long> original, SectionPos sectionPos) {
        if (!cc_isCubic) {
            return original.call(chunkPos);
        }
        return CloPos.section(sectionPos).toLong();
    }

    /**
     * The original function expects chunkPos.toLong(), but we need to replace it with cloPos.toLong() instead.
     */
    @WrapOperation(method = "removePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack()J"))
    private long cc_replaceTicketTypeOnRemovePlayer(ChunkPos chunkPos, Operation<Long> original, SectionPos sectionPos) {
        if (!cc_isCubic) {
            return original.call(chunkPos);
        }
        return CloPos.section(sectionPos).toLong();
    }

    @Shadow private int simulationDistance;

    /**
     * The players' simulation ticket level in a cubic level. Vanilla's (entity ticking minus the simulation distance) reaches that many
     * positions, and a cubic level's tracker counts cubes, in three dimensions: a simulation distance of 10 ticked every cube within 10
     * cubes, 320 blocks each way and up and down (some 9,000 cubes, ticked every tick: the play test's server lagged seconds at a time).
     * Counted in cubes the distance covers the same blocks across as vanilla's chunks (10 chunks: 5 cubes) and as far up and down.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "getPlayerTicketLevel", at = @At("HEAD"), cancellable = true)
    private void cc_cubeSimulationLevel(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if (cc_isCubic) {
            cir.setReturnValue(Math.max(0, net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING)
                    - io.github.opencubicchunks.cc_core.utils.Coords.sectionToCubeCeil(this.simulationDistance)));
        }
    }

    @Shadow @Final private LongSet ticketsToRelease;
    @Shadow protected abstract net.minecraft.server.level.ChunkHolder getChunk(long node);
    @Shadow @Final private net.minecraft.server.level.ThrottlingChunkTaskDispatcher ticketDispatcher;

    /**
     * A player's view reaches cubes beyond a cubic level's heights, and gives them loading tickets; the loading tracker keeps such cubes
     * past {@link io.github.opencubicchunks.cubicchunks.server.level.CubeYRange#CUBES_BEYOND} from loading at all (no holder). Vanilla
     * expects every player-ticketed position to have a holder as it releases the ticket's throttle (and throws otherwise): those positions
     * are released here first, as the throttle's other path does for a ticket that no longer applies.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;isEmpty()Z"))
    private void cc_releaseTicketsWithoutHolders(ChunkMap scheduler, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!cc_isCubic || this.ticketsToRelease.isEmpty()) {
            return;
        }
        it.unimi.dsi.fastutil.longs.LongIterator positions = this.ticketsToRelease.iterator();
        while (positions.hasNext()) {
            long pos = positions.nextLong();
            if (this.getChunk(pos) == null) {
                positions.remove();
                this.ticketDispatcher.release(pos, () -> { }, false);
            }
        }
    }

    // TODO how does hasPlayersNearby work?
}
