package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent while a player joins (the configuration phase, before the client makes its levels): whether the server's world is cubic, its
 * heights and its cubic dimensions (see CubicWorldSettings), and how many bits the server packs block positions' Y into, which must be the client's too.
 */
public record CCClientboundWorldSettingsPacket(int packedYLength, boolean cubic, int minY, int maxY, java.util.List<String> dimensions)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CCClientboundWorldSettingsPacket> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(CubicChunks.MODID, "world_settings"));

    public static final StreamCodec<ByteBuf, CCClientboundWorldSettingsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CCClientboundWorldSettingsPacket::packedYLength,
            ByteBufCodecs.BOOL, CCClientboundWorldSettingsPacket::cubic,
            ByteBufCodecs.INT, CCClientboundWorldSettingsPacket::minY,
            ByteBufCodecs.INT, CCClientboundWorldSettingsPacket::maxY,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), CCClientboundWorldSettingsPacket::dimensions,
            CCClientboundWorldSettingsPacket::new);

    public CCClientboundWorldSettingsPacket(int packedYLength, CubicWorldSettings settings) {
        this(packedYLength, settings.cubic(), settings.minY(), settings.maxY(), settings.dimensions());
    }

    public CubicWorldSettings settings() {
        return new CubicWorldSettings(this.cubic, this.minY, this.maxY, this.dimensions);
    }

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
