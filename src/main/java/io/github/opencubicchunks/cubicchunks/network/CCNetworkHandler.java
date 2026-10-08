package io.github.opencubicchunks.cubicchunks.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** The mod's packets: their types and codecs, registered on both sides (the client handlers in CCClientNetworkHandler). */
public class CCNetworkHandler {
    private CCNetworkHandler() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundLevelCubeWithLightPacket.TYPE, CCClientboundLevelCubeWithLightPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundLevelChunkPacket.TYPE, CCClientboundLevelChunkPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundForgetLevelCloPacket.TYPE, CCClientboundForgetLevelCloPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundSetCubeCacheCenterPacket.TYPE, CCClientboundSetCubeCacheCenterPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundCubeLightUpdatePacket.TYPE, CCClientboundCubeLightUpdatePacket.STREAM_CODEC);
    }
}
