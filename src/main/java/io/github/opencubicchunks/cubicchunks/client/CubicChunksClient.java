package io.github.opencubicchunks.cubicchunks.client;

import io.github.opencubicchunks.cubicchunks.client.network.CCClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;

/** The mod's client entry point. */
public class CubicChunksClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        CCClientNetworkHandler.register();
    }
}
