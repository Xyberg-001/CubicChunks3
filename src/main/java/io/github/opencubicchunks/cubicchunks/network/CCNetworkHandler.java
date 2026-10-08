package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The mod's packets: their types and codecs, registered on both sides (the client handlers in CCClientNetworkHandler). */
public class CCNetworkHandler {
    private CCNetworkHandler() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundLevelCubeWithLightPacket.TYPE, CCClientboundLevelCubeWithLightPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundLevelChunkPacket.TYPE, CCClientboundLevelChunkPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundForgetLevelCloPacket.TYPE, CCClientboundForgetLevelCloPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundSetCubeCacheCenterPacket.TYPE, CCClientboundSetCubeCacheCenterPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CCClientboundCubeLightUpdatePacket.TYPE, CCClientboundCubeLightUpdatePacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(CCClientboundWorldSettingsPacket.TYPE, CCClientboundWorldSettingsPacket.STREAM_CODEC);

        // a joining player learns whether the world is cubic before their client makes its levels. The mod packs block positions
        // differently in every world (see MixinBlockPos), so a client without it would read them wrongly, even in a world that isn't cubic.
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, CCClientboundWorldSettingsPacket.TYPE)) {
                ServerConfigurationNetworking.send(handler, new CCClientboundWorldSettingsPacket(BlockPos.PACKED_Y_LENGTH, CubicWorldSettings.server()));
            } else {
                handler.disconnect(Component.literal("This server runs the Cubic Chunks mod, which changes how block positions are sent: "
                        + "join with Cubic Chunks installed."));
            }
        });
    }
}
