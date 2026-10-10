package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.lighting;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.SkyDataLayerStorageMapAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sky light where no section holds light data above: vanilla takes it for open sky (15), the topmost section with data being the top of its
 * chunk. In a cubic column there may be a roof above in cubes not loaded (see SkyRoofs): there, only where the sky enters the block column
 * (its lowest source, which knows those roofs) and above is open; below is dark. The same for a data layer made at the top of the column
 * under such a roof, which vanilla fills with 15 (under a loaded roof it stays vanilla's: the roof's own darkness spreads down from it).
 */
@Mixin(SkyLightSectionStorage.class)
public abstract class MixinSkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
    protected MixinSkyLightSectionStorage(LightLayer layer, LightChunkGetter chunkSource, SkyLightSectionStorage.SkyDataLayerStorageMap initialMap) {
        super(layer, chunkSource, initialMap);
    }

    /** Where full sky light starts in the block column, if the level is cubic (else null: vanilla's open sky). */
    @Unique private Integer cc_skyStartsAt(int x, int z) {
        LightChunk column = this.chunkSource.getChunkForLighting(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        if (column instanceof CubicLightColumn cubic) {
            return cubic.getSkyLightSources().getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
        }
        return null;
    }

    @Inject(method = "getLightValue(JZ)I", at = @At("HEAD"), cancellable = true)
    private void cc_roofedSky(long blockNode, boolean updating, CallbackInfoReturnable<Integer> cir) {
        int x = BlockPos.getX(blockNode);
        int y = BlockPos.getY(blockNode);
        int z = BlockPos.getZ(blockNode);
        Integer skyStartsAt = null;
        boolean cubic = false;
        long sectionNode = SectionPos.blockToSection(blockNode);
        int sectionY = SectionPos.y(sectionNode);
        SkyLightSectionStorage.SkyDataLayerStorageMap sections = updating ? this.updatingSectionData : this.visibleSectionData;
        SkyDataLayerStorageMapAccess access = (SkyDataLayerStorageMapAccess) (Object) sections;
        int topSection = access.cc_topSections().get(SectionPos.getZeroNode(sectionNode));
        if (topSection != access.cc_currentLowestY() && sectionY < topSection) {
            DataLayer layer = this.getDataLayer(sections, sectionNode);
            if (layer != null) {
                return; // vanilla reads it
            }
            long above = sectionNode;
            for (int aboveY = sectionY + 1; aboveY < topSection; aboveY++) {
                above = SectionPos.offset(above, Direction.UP);
                if (this.getDataLayer(sections, above) != null) {
                    return; // vanilla takes the bottom of that layer
                }
            }
        } else if (updating && !this.lightOnInSection(sectionNode)) {
            return; // vanilla's 0
        }
        // no data above: open sky in vanilla
        skyStartsAt = this.cc_skyStartsAt(x, z);
        if (skyStartsAt != null) {
            cir.setReturnValue(y >= skyStartsAt ? 15 : 0);
        }
    }

    @Inject(method = "createDataLayer", at = @At("HEAD"), cancellable = true)
    private void cc_roofedNewLayer(long sectionNode, CallbackInfoReturnable<DataLayer> cir) {
        if (this.queuedSections.get(sectionNode) != null) {
            return;
        }
        SkyDataLayerStorageMapAccess access = (SkyDataLayerStorageMapAccess) (Object) this.updatingSectionData;
        int topSection = access.cc_topSections().get(SectionPos.getZeroNode(sectionNode));
        if ((topSection != access.cc_currentLowestY() && SectionPos.y(sectionNode) < topSection) || !this.lightOnInSection(sectionNode)) {
            return; // vanilla copies the layer above, or makes it dark
        }
        int minX = SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode));
        int minY = SectionPos.sectionToBlockCoord(SectionPos.y(sectionNode));
        int minZ = SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode));
        LightChunk column = this.chunkSource.getChunkForLighting(SectionPos.x(sectionNode), SectionPos.z(sectionNode));
        if (!(column instanceof CubicLightColumn cubic)) {
            return;
        }
        // only under a roof in cubes not loaded: a loaded roof's darkness spreads down from its own layer, made at 15 as vanilla makes it
        var sources = (io.github.opencubicchunks.cubicchunks.world.lighting.CubicSkyLightSources) cubic.getSkyLightSources();
        DataLayer layer = null;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int skyStartsAt = sources.getUnloadedRoofSourceY(localX, localZ);
                if (skyStartsAt <= minY) {
                    continue;
                }
                if (layer == null) {
                    layer = new DataLayer(15);
                }
                for (int localY = 0; localY < 16 && minY + localY < skyStartsAt; localY++) {
                    layer.set(localX, localY, localZ, 0);
                }
            }
        }
        if (layer != null) {
            cir.setReturnValue(layer);
        }
    }
}
