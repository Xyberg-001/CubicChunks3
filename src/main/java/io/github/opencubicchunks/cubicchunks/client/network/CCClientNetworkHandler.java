package io.github.opencubicchunks.cubicchunks.client.network;

import io.github.opencubicchunks.cubicchunks.network.CCClientboundCubeLightUpdatePacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundForgetLevelCloPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelChunkPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelCubeWithLightPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundSetCubeCacheCenterPacket;
import io.github.opencubicchunks.cubicchunks.network.CCPayloadHandler;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundWorldSettingsPacket;
import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        receiveWorldSettings();
    }

    /**
     * The server's world settings, which the client's levels follow (see CubicWorldSettings), arrive as it is joined. The server and client
     * must pack block positions alike; a server without the mod packs them as vanilla, which this client can't read, so it leaves.
     */
    private static void receiveWorldSettings() {
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> CubicWorldSettings.setClient(null));
        ClientConfigurationNetworking.registerGlobalReceiver(CCClientboundWorldSettingsPacket.TYPE, (payload, context) -> {
            if (payload.packedYLength() != BlockPos.PACKED_Y_LENGTH) {
                context.responseSender().disconnect(Component.translatable("disconnect.cubicchunks.heightLimit",
                        heightLimit(payload.packedYLength()), heightLimit(BlockPos.PACKED_Y_LENGTH)));
                return;
            }
            CubicWorldSettings.setClient(payload.settings());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (CubicWorldSettings.client() == null) {
                sender.disconnect(Component.translatable("disconnect.cubicchunks.vanillaServer"));
            }
        });
    }

    /** The heightLimit config a game packing Y into this many bits runs with. */
    private static int heightLimit(int packedYLength) {
        return 1 << (packedYLength - 1);
    }

    private static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, CCPayloadHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context.player()));
    }
}
