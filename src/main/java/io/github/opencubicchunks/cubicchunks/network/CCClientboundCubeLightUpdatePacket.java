package io.github.opencubicchunks.cubicchunks.network;

import static io.github.opencubicchunks.cubicchunks.network.MiscStreamCodecs.CUBE_POS_STREAM_CODEC;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSections;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Light that changed in a cube the player has (vanilla's ClientboundLightUpdatePacket, for a chunk). Vanilla sends it only to players at the
 * edge of their view, leaving the others to work changes out from the blocks they get; a cube's light can also change from far away (a cube
 * arriving high above takes the sky from the cubes under it), which a client may not hold, so it goes to every player tracking the cube.
 */
public record CCClientboundCubeLightUpdatePacket(CubePos pos, CubeLightData light) implements CustomPacketPayload {
    public static final Type<CCClientboundCubeLightUpdatePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CubicChunks.MODID, "cube_light_update"));

    public static final StreamCodec<FriendlyByteBuf, CCClientboundCubeLightUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            CUBE_POS_STREAM_CODEC, CCClientboundCubeLightUpdatePacket::pos, CubeLightData.STREAM_CODEC, CCClientboundCubeLightUpdatePacket::light,
            CCClientboundCubeLightUpdatePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class Handler implements CCPayloadHandler<CCClientboundCubeLightUpdatePacket> {
        @Override public void handle(CCClientboundCubeLightUpdatePacket payload, Player player) {
            ClientLevel level = (ClientLevel) player.level();
            // in order with the cubes' own arrival and leaving, as vanilla queues a chunk's light
            level.queueLightUpdate(() -> {
                payload.light.queueTo(level.getLightEngine(), payload.pos);
                for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
                    if ((payload.light.sectionMask() & (1 << i)) != 0) {
                        SectionPos section = CubeSections.sectionPosOf(payload.pos, i);
                        level.setSectionDirtyWithNeighbors(section.x(), section.y(), section.z());
                    }
                }
            });
        }
    }
}
