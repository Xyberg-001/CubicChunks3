package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cubicchunks.server.level.CloHolder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3 counts the chunks a level load waits for by their packed position; in a cubic level the holders are cube holders, which have no
 * chunk position, so they are keyed by their packed clo position instead (cube and chunk keys never collide).
 */
@Mixin(ChunkLoadCounter.class)
public class MixinChunkLoadCounter {
    @WrapOperation(method = "lambda$track$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack()J"))
    private static long cc_packLoadedHolder(ChunkPos pos, Operation<Long> original, @Local(argsOnly = true) ChunkHolder holder) {
        return pos != null ? original.call(pos) : ((CloHolder) holder).cc_getCloPos().asLong();
    }

    @WrapOperation(method = "lambda$track$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack()J"))
    private long cc_packPendingHolder(ChunkPos pos, Operation<Long> original, @Local(argsOnly = true) ChunkHolder holder) {
        return pos != null ? original.call(pos) : ((CloHolder) holder).cc_getCloPos().asLong();
    }
}
