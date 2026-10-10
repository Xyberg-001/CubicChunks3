package io.github.opencubicchunks.cubicchunks.mixin.core.client.multiplayer;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.client.color.block.CubicBlockTintCache;
import io.github.opencubicchunks.cubicchunks.client.render.CubeRenderReadiness;
import io.github.opencubicchunks.cubicchunks.client.render.SodiumCubes;
import io.github.opencubicchunks.cubicchunks.client.render.VoxyCubes;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.world.level.ColorResolver;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.entity.CubicEntitySections;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.CubicClientLevel;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.MixinLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel extends MixinLevel implements CubicClientLevel {
    @Shadow @Final private ClientChunkCache chunkSource;
    @Shadow @Final private TransientEntitySectionManager<Entity> entityStorage;
    @Shadow @Final private Object2ObjectArrayMap<ColorResolver, BlockTintCache> tintCaches;

    @Override public boolean cc_hasCube(int cubeX, int cubeY, int cubeZ) {
        return true;
    }

    /** The client's entities tick cube by cube in a cubic level (see MixinTransientEntitySectionManager). */
    @Unique private @Nullable CubeRenderReadiness cc_renderReadiness;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cc_cubicEntities(CallbackInfo ci) {
        if (this.cc_isCubic) {
            ((CubicEntitySections.Manager) this.entityStorage).cc_makeCubic();
            if (SodiumCubes.SODIUM) {
                this.cc_renderReadiness = new CubeRenderReadiness();
            }
        }
    }

    /** Voxy refreshes its copy of a section when a block in it changes; in a cubic level the section comes from its cube (see VoxyCubes). */
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void cc_voxyBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (this.cc_isCubic && VoxyCubes.active()) {
            VoxyCubes.ingestBlockChange((ClientLevel) (Object) this, pos);
        }
    }

    @Override public @Nullable CubeRenderReadiness cc_renderReadiness() {
        return this.cc_renderReadiness;
    }

    /** As vanilla's onChunkLoaded: biome colours worked out before the cube's biomes arrived go (see CubicBlockTintCache). */
    @Override public void cc_onCubeLoaded(CubePos cubePos) {
        this.tintCaches.forEach((resolver, cache) -> ((CubicBlockTintCache) cache).cc_invalidateForCube(cubePos));
        ((CubicEntitySections.Manager) this.entityStorage).cc_updateCubeStatus(cubePos, Visibility.TICKING);
        if (this.cc_renderReadiness != null) {
            this.cc_renderReadiness.onCubeHeld(cubePos.getX(), cubePos.getY(), cubePos.getZ());
        }
    }

    @Override public void cc_onCubeUnloaded(LevelCube cube) {
        VoxyCubes.ingestOnUnload((ClientLevel) (Object) this, cube); // its latest state, light included (the light leaves later, queued)
        cube.clearAllBlockEntities();
        ((CubicEntitySections.Manager) this.entityStorage).cc_updateCubeStatus(cube.cc_getCubePos(), Visibility.TRACKED);
        if (this.cc_renderReadiness != null) {
            this.cc_renderReadiness.onCubeDropped(cube.cc_getCubePos().getX(), cube.cc_getCubePos().getY(), cube.cc_getCubePos().getZ());
        }
    }

    @Override public ClientCubeCache cc_getCubeSource() {
        return ((ClientCubeCache) this.chunkSource);
    }

    // TODO: comments below don't account for 1.20.4->1.21.5 changes; will need to check for other methods that need CC changes

    // unload
    // TODO: Phase 2 - this interacts with the lighting engine and will need to change to support cubes

    // onCubeLoaded
    // TODO: Phase 3 - we will need to interact with entityStorage correctly at a per-cube level
}
