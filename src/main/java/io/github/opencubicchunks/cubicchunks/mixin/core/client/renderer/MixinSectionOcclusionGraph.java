package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer;

import javax.annotation.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicRenderSections;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In a cubic level the graph waits for the cube a section belongs to, not its column: the loaded set it checks holds packed cube positions
 * (see MixinClientChunkCache). Vertical distance is already limited by vanilla's getRelativeFrom, and with cubic storage the camera's
 * section always exists, so the rest of the graph needs no change.
 */
@Mixin(SectionOcclusionGraph.class)
public abstract class MixinSectionOcclusionGraph {
    @Shadow private @Nullable ViewArea viewArea;

    @WrapOperation(method = "runUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;fromSectionNode(J)J"))
    private long cc_cubeOfSection(long sectionNode, Operation<Long> original) {
        if (this.viewArea == null || !((CubicRenderSections.Area) this.viewArea).cc_isCubic()) {
            return original.call(sectionNode);
        }
        return CubePos.asLong(Coords.sectionToCube(SectionPos.x(sectionNode)), Coords.sectionToCube(SectionPos.y(sectionNode)),
                Coords.sectionToCube(SectionPos.z(sectionNode)));
    }
}
