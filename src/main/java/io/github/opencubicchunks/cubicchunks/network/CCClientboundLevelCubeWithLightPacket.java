package io.github.opencubicchunks.cubicchunks.network;

import static io.github.opencubicchunks.cubicchunks.network.MiscStreamCodecs.CUBE_POS_STREAM_CODEC;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.player.Player;

// TODO (P2) the name is currently a lie; no light data :)
public record CCClientboundLevelCubeWithLightPacket(CubePos pos, CCClientboundLevelCubePacketData cubeData) implements CustomPacketPayload {
    public static final Type<CCClientboundLevelCubeWithLightPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CubicChunks.MODID, "level_cube_with_light"));

    public static final StreamCodec<FriendlyByteBuf, CCClientboundLevelCubeWithLightPacket> STREAM_CODEC = StreamCodec.composite(
            CUBE_POS_STREAM_CODEC, CCClientboundLevelCubeWithLightPacket::pos, CCClientboundLevelCubePacketData.STREAM_CODEC,
            CCClientboundLevelCubeWithLightPacket::cubeData, CCClientboundLevelCubeWithLightPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public CCClientboundLevelCubeWithLightPacket(LevelCube cube) {
        this(cube.cc_getCloPos().cubePos(), new CCClientboundLevelCubePacketData(cube));
    }

    public static class Handler implements CCPayloadHandler<CCClientboundLevelCubeWithLightPacket> {
        @Override public void handle(CCClientboundLevelCubeWithLightPacket payload, Player player) {
            int x = payload.pos.getX();
            int y = payload.pos.getY();
            int z = payload.pos.getZ();
            this.updateLevelCube(player.level(), x, y, z, payload);
        }

        private void updateLevelCube(Level level, int x, int y, int z, CCClientboundLevelCubeWithLightPacket payload) {
            // TODO P2 :: The empty map should contain heightmap data
            Map<Heightmap.Types, long[]> heightmaps = new HashMap<>();

            // TODO P2 :: No block entity tags consumer
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> entityTagConsumer = (a) -> {};

            ((ClientCubeCache) (level.getChunkSource())).cc_replaceWithPacketData(x, y, z, payload.cubeData.getReadBuffer(), heightmaps,
                    entityTagConsumer);

            // TODO P2 :: light on the server (vanilla sends it with the chunk); until then the client lights cubes itself
            ((ClientLevel) level).queueLightUpdate(() -> {
                LevelCube levelCube = ((CubeSource) level.getChunkSource()).cc_getCube(x, y, z, false);
                if (levelCube != null) {
                    // the server sends no cube light: the client works it out (see CubicClientLight)
                    CubicLight light = ((CubeSource) level.getChunkSource()).cc_cubicLight();
                    if (light != null) {
                        light.onCubeLoaded(levelCube, false);
                    }
                    // as 26.3's enableChunkLight: the cube's sections and their neighbours are dirty now that it can render
                    int minSectionX = Coords.cubeToSection(x, 0);
                    int minSectionY = Coords.cubeToSection(y, 0);
                    int minSectionZ = Coords.cubeToSection(z, 0);
                    int maxOffset = CubicConstants.DIAMETER_IN_SECTIONS;
                    ((ClientLevel) level).setSectionRangeDirty(minSectionX - 1, minSectionY - 1, minSectionZ - 1,
                            minSectionX + maxOffset, minSectionY + maxOffset, minSectionZ + maxOffset);
                }
            });
        }
    }
}
