package io.github.opencubicchunks.cubicchunks.mixin.core.client.gui.components.debug;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.server.level.CubicInhabitedTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryLocalDifficulty;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The F3 screen's local difficulty in a cubic level: the inhabited time of the cube at the camera's feet (see CubicInhabitedTime). */
@Mixin(DebugEntryLocalDifficulty.class)
public abstract class MixinDebugEntryLocalDifficulty {
    @WrapOperation(method = "display", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getInhabitedTime()J"))
    private long cc_cubeInhabitedTime(LevelChunk serverChunk, Operation<Long> original, DebugScreenDisplayer displayer, Level serverOrClientLevel,
            LevelChunk clientChunk, LevelChunk serverChunkArg) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (serverOrClientLevel instanceof ServerLevel serverLevel && ((CanBeCubic) serverLevel).cc_isCubic() && camera != null) {
            return CubicInhabitedTime.at(serverLevel, camera.blockPosition());
        }
        return original.call(serverChunk);
    }
}
