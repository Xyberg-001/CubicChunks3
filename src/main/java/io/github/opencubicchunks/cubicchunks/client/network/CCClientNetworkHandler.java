package io.github.opencubicchunks.cubicchunks.client.network;

import io.github.opencubicchunks.cubicchunks.network.CCClientboundCubeLightUpdatePacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundForgetLevelCloPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelChunkPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelCubeWithLightPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundSetCubeCacheCenterPacket;
import io.github.opencubicchunks.cubicchunks.network.CCPayloadHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client's handlers for the mod's packets (Fabric runs them on the client's main thread). */
public class CCClientNetworkHandler {
    private CCClientNetworkHandler() {}

    public static void register() {
        receive(CCClientboundLevelCubeWithLightPacket.TYPE, new CCClientboundLevelCubeWithLightPacket.Handler());
        receive(CCClientboundLevelChunkPacket.TYPE, new CCClientboundLevelChunkPacket.Handler());
        receive(CCClientboundForgetLevelCloPacket.TYPE, new CCClientboundForgetLevelCloPacket.Handler());
        receive(CCClientboundSetCubeCacheCenterPacket.TYPE, new CCClientboundSetCubeCacheCenterPacket.Handler());
        receive(CCClientboundCubeLightUpdatePacket.TYPE, new CCClientboundCubeLightUpdatePacket.Handler());
    }

    private static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, CCPayloadHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context.player()));
    }
}
