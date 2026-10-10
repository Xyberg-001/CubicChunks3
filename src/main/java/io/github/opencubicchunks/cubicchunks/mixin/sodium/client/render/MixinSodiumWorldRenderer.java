package io.github.opencubicchunks.cubicchunks.mixin.sodium.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.client.render.CubeRenderReadiness;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import io.github.opencubicchunks.cubicchunks.client.render.VoxyCubes;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium's renderer adds sections column by column, as its chunk tracker reports columns ready; a cubic client holds no columns, so its
 * sections come cube by cube from the level's CubeRenderReadiness instead: each frame the cubes that became ready get their sections added and
 * those that stopped being ready lose them, and a renderer starting over adds every ready cube's.
 */
@Mixin(SodiumWorldRenderer.class)
public abstract class MixinSodiumWorldRenderer {
    @Shadow private ClientLevel level;
    @Shadow private RenderSectionManager renderSectionManager;

    @Inject(method = "processChunkEvents", at = @At("TAIL"))
    private void cc_processCubeEvents(CallbackInfo ci) {
        VoxyCubes.ingestLit(this.level);
        CubeRenderReadiness readiness = SodiumCubes.readiness(this.level);
        if (readiness != null) {
            RenderSectionManager sections = this.renderSectionManager;
            readiness.takeChanges(
                    (cubeX, cubeY, cubeZ) -> SodiumCubes.forEachSection(cubeX, cubeY, cubeZ, sections::onSectionRemoved),
                    (cubeX, cubeY, cubeZ) -> this.cc_addSections(cubeX, cubeY, cubeZ));
        }
    }

    @Inject(method = "initRenderer", at = @At("TAIL"))
    private void cc_addReadyCubes(CallbackInfo ci) {
        CubeRenderReadiness readiness = SodiumCubes.readiness(this.level);
        if (readiness != null) {
            readiness.forEachReady(this::cc_addSections);
        }
    }

    /**
     * A ready cube's sections (Sodium reads each from the cube, so a cube no longer held is left out). The cube goes to Voxy too, if installed,
     * once its light is worked out (VoxyCubes.ingestWhenLit).
     */
    private void cc_addSections(int cubeX, int cubeY, int cubeZ) {
        LevelCube cube = SodiumCubes.cubeOfSection(this.level, Coords.cubeToSection(cubeX, 0), Coords.cubeToSection(cubeY, 0),
                Coords.cubeToSection(cubeZ, 0));
        if (cube != null) {
            SodiumCubes.forEachSection(cubeX, cubeY, cubeZ, this.renderSectionManager::onSectionAdded);
            VoxyCubes.ingestWhenLit(cube);
        }
    }

    /** Boxes outside the level's heights count as visible (nothing culls them); a cubic level's heights are its world's. */
    @WrapOperation(method = "isBoxVisible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"))
    private int cc_cubicMinY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? CubicHeight.minY(level) : original.call(level);
    }

    @WrapOperation(method = "isBoxVisible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxY()I"))
    private int cc_cubicMaxY(ClientLevel level, Operation<Integer> original) {
        return SodiumCubes.isCubic(level) ? CubicHeight.maxY(level) : original.call(level);
    }
}
