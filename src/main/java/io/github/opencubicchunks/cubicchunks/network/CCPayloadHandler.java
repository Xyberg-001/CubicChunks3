package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Handles a payload sent to the client, on the client's main thread. */
@FunctionalInterface
public interface CCPayloadHandler<T extends CustomPacketPayload> {
    void handle(T payload, Player player);
}
