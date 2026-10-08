package io.github.opencubicchunks.cubicchunks.mixin.core.client.multiplayer;

import io.github.opencubicchunks.cubicchunks.client.color.block.CubicBlockTintCache;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel extends MixinLevel implements CubicClientLevel {
    @Shadow @Final private ClientChunkCache chunkSource;
    @Shadow @Final private TransientEntitySectionManager<Entity> entityStorage;
    @Shadow @Final private Object2ObjectArrayMap<ColorResolver, BlockTintCache> tintCaches;

    @Override public boolean cc_hasCube(int cubeX, int cubeY, int cubeZ) {
        return true;
    }

    /** The client's entities tick cube by cube in a cubic level (see MixinTransientEntitySectionManager). */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cc_cubicEntities(CallbackInfo ci) {
        if (this.cc_isCubic) {
            ((CubicEntitySections.Manager) this.entityStorage).cc_makeCubic();
        }
    }

    /** As vanilla's onChunkLoaded: biome colours worked out before the cube's biomes arrived go (see CubicBlockTintCache). */
    @Override public void cc_onCubeLoaded(CubePos cubePos) {
        this.tintCaches.forEach((resolver, cache) -> ((CubicBlockTintCache) cache).cc_invalidateForCube(cubePos));
        ((CubicEntitySections.Manager) this.entityStorage).cc_updateCubeStatus(cubePos, Visibility.TICKING);
    }

    @Override public void cc_onCubeUnloaded(LevelCube cube) {
        cube.clearAllBlockEntities();
        ((CubicEntitySections.Manager) this.entityStorage).cc_updateCubeStatus(cube.cc_getCubePos(), Visibility.TRACKED);
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
