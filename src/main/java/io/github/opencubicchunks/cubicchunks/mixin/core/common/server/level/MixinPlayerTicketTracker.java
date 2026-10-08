package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import io.github.opencubicchunks.cc_core.utils.Coords;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.DistanceManagerAccess;
import io.github.opencubicchunks.cubicchunks.server.level.CloTaskDispatcher;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DistanceManager.PlayerTicketTracker.class)
public abstract class MixinPlayerTicketTracker extends MixinFixedPlayerDistanceChunkTracker {
    @SuppressWarnings("target")
    @Shadow @Final DistanceManager this$0;

    /** The view distance in cubes (-1 until the server sets one); the tracker reaches no farther (see cc_maxCubeLevel). */
    @Unique private int cc_cubeViewDistance = -1;

    @Override protected int cc_maxCubeLevel() {
        return cc_isCubic && this.cc_cubeViewDistance >= 0 ? this.cc_cubeViewDistance : super.cc_maxCubeLevel();
    }

    /**
     * Vanilla gives a player loading tickets to the chunks within the view distance; a cube is two chunks wide, so in a cubic level the
     * distance is counted in cubes as the cubes sent to the player are (Coords.sectionToCubeRenderDistance): before, a view distance of 10
     * loaded cubes 10 cubes away in every direction while the player was sent those 5 away. The tracker reaches as far as that and no
     * farther; when the distance changes (a singleplayer render distance), the cubes past the new edge are dropped, or those on the old edge
     * spread out to the new one.
     */
    @ModifyVariable(method = "updateViewDistance", at = @At("HEAD"), argsOnly = true)
    private int cc_viewDistanceInCubes(int viewDistance) {
        if (!cc_isCubic) {
            return viewDistance;
        }
        int cubes = Coords.sectionToCubeRenderDistance(viewDistance);
        int old = this.cc_cubeViewDistance;
        if (cubes != old) {
            this.cc_cubeViewDistance = cubes;
            if (old >= 0) {
                for (long node : this.chunks.keySet().toLongArray()) {
                    int level = this.chunks.get(node);
                    if (!CloPos.isCube(node)) {
                        continue;
                    }
                    if (level > cubes) {
                        this.checkNode(node);
                    } else if (cubes > old && level == old) {
                        this.checkNeighborsAfterUpdate(node, level, true);
                    }
                }
            }
        }
        return cubes;
    }

    /**
     * This modifies the lambda inside Distance.this.ticketDispatcher.onLevelChange to use a CloPos instead of a ChunkPos.
     */
    @WrapWithCondition(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ThrottlingChunkTaskDispatcher;onLevelChange(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/IntSupplier;ILjava/util/function/IntConsumer;)V"))
    private boolean cc_onRunAllUpdates(
            ThrottlingChunkTaskDispatcher instance, ChunkPos chunkPos, IntSupplier intSupplier, int i, IntConsumer intConsumer
    ) {
        if (!cc_isCubic) {
            return true;
        }
        ((CloTaskDispatcher) ((DistanceManagerAccess) this$0).cc_ticketDispatcher()).cc_onLevelChange(CloPos.fromLong(chunkPos.pack()), intSupplier,
                i, intConsumer);
        return false;
    }
}
