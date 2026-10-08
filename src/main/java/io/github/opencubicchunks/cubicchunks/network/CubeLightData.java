package io.github.opencubicchunks.cubicchunks.network;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSections;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * The server's light for some of a cube's sections, as vanilla's ClientboundLightUpdatePacketData is for a chunk's. Each section of the mask
 * carries, per layer, nothing (the server holds no light there), one value for the whole section (common: open sky, solid ground), or the
 * full 2048 bytes. Vanilla sends a section with no stored nibbles as "empty", which the client reads as 0; a sky section held as 15
 * throughout without nibbles would arrive dark, so the value is sent instead.
 */
public record CubeLightData(int sectionMask, @Nullable DataLayer[] sky, @Nullable DataLayer[] block) {
    public static final int ALL_SECTIONS = (1 << CubicConstants.SECTION_COUNT) - 1;

    private static final byte NONE = 0;
    private static final byte UNIFORM = 1;
    private static final byte FULL = 2;

    public static final StreamCodec<FriendlyByteBuf, CubeLightData> STREAM_CODEC = StreamCodec.of(CubeLightData::write, CubeLightData::read);

    /** The light engine's layers for the cube's sections in the mask (from the server thread, as vanilla reads them for its packets). */
    public static CubeLightData of(LevelLightEngine engine, CubePos cubePos, int sectionMask) {
        DataLayer[] sky = new DataLayer[CubicConstants.SECTION_COUNT];
        DataLayer[] block = new DataLayer[CubicConstants.SECTION_COUNT];
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            if ((sectionMask & (1 << i)) != 0) {
                SectionPos sectionPos = CubeSections.sectionPosOf(cubePos, i);
                sky[i] = copy(engine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos));
                block[i] = copy(engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos));
            }
        }
        return new CubeLightData(sectionMask, sky, block);
    }

    private static @Nullable DataLayer copy(@Nullable DataLayer layer) {
        return layer == null ? null : layer.copy();
    }

    /** Hands the layers to the client's light engine, as vanilla's ClientPacketListener.readSectionList does. */
    public void queueTo(LevelLightEngine engine, CubePos cubePos) {
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            if ((this.sectionMask & (1 << i)) != 0) {
                SectionPos sectionPos = CubeSections.sectionPosOf(cubePos, i);
                if (this.sky[i] != null) {
                    engine.queueSectionData(LightLayer.SKY, sectionPos, this.sky[i]);
                }
                if (this.block[i] != null) {
                    engine.queueSectionData(LightLayer.BLOCK, sectionPos, this.block[i]);
                }
            }
        }
    }

    private static void write(FriendlyByteBuf buf, CubeLightData data) {
        buf.writeByte(data.sectionMask);
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            if ((data.sectionMask & (1 << i)) != 0) {
                writeLayer(buf, data.sky[i]);
                writeLayer(buf, data.block[i]);
            }
        }
    }

    private static CubeLightData read(FriendlyByteBuf buf) {
        int mask = buf.readUnsignedByte();
        DataLayer[] sky = new DataLayer[CubicConstants.SECTION_COUNT];
        DataLayer[] block = new DataLayer[CubicConstants.SECTION_COUNT];
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            if ((mask & (1 << i)) != 0) {
                sky[i] = readLayer(buf);
                block[i] = readLayer(buf);
            }
        }
        return new CubeLightData(mask, sky, block);
    }

    private static void writeLayer(FriendlyByteBuf buf, @Nullable DataLayer layer) {
        if (layer == null) {
            buf.writeByte(NONE);
        } else if (layer.isDefinitelyHomogenous()) {
            buf.writeByte(UNIFORM);
            buf.writeByte(layer.get(0, 0, 0));
        } else {
            buf.writeByte(FULL);
            buf.writeBytes(layer.getData());
        }
    }

    private static @Nullable DataLayer readLayer(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        if (kind == UNIFORM) {
            return new DataLayer(buf.readUnsignedByte());
        }
        if (kind == FULL) {
            byte[] data = new byte[DataLayer.SIZE];
            buf.readBytes(data);
            return new DataLayer(data);
        }
        return null;
    }
}
