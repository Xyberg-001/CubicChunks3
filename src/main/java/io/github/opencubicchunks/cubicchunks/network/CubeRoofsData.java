package io.github.opencubicchunks.cubicchunks.network;

import java.util.ArrayList;
import java.util.List;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyRoofs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * The sky roofs the server knows over a cube's columns, higher than the cube (see {@link SkyRoofs}): sent with the cube, so the client,
 * which may hold none of the cubes above, works out sky light under them as the server does when blocks change near it.
 */
public record CubeRoofsData(List<Entry> entries) {
    /** One roof: which of the cube's 16x16 columns ({@code dx + dz * DIAMETER_IN_SECTIONS}), the roofing cube's Y and its top occluders. */
    public record Entry(int column, int cubeY, byte[] top) {
    }

    public static final CubeRoofsData NONE = new CubeRoofsData(List.of());

    public static final StreamCodec<FriendlyByteBuf, CubeRoofsData> STREAM_CODEC = StreamCodec.of((buf, data) -> {
        buf.writeVarInt(data.entries.size());
        for (Entry entry : data.entries) {
            buf.writeByte(entry.column);
            buf.writeVarInt(entry.cubeY);
            buf.writeBytes(entry.top);
        }
    }, buf -> {
        int count = buf.readVarInt();
        if (count < 0 || count > CubicConstants.DIAMETER_IN_SECTIONS * CubicConstants.DIAMETER_IN_SECTIONS * 256) {
            throw new IllegalArgumentException("Too many sky roofs: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int column = buf.readUnsignedByte();
            int cubeY = buf.readVarInt();
            byte[] top = new byte[256];
            buf.readBytes(top);
            entries.add(new Entry(column, cubeY, top));
        }
        return new CubeRoofsData(entries);
    });

    /** The server's roofs over the cube's columns, above it. */
    public static CubeRoofsData of(@Nullable SkyRoofs roofs, CubePos pos) {
        if (roofs == null) {
            return NONE;
        }
        List<Entry> entries = new ArrayList<>();
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                for (SkyRoofs.Roof roof : roofs.column(Coords.cubeToSection(pos.getX(), dx), Coords.cubeToSection(pos.getZ(), dz))) {
                    if (roof.cubeY() > pos.getY()) {
                        entries.add(new Entry(dx + dz * CubicConstants.DIAMETER_IN_SECTIONS, roof.cubeY(), roof.top()));
                    }
                }
            }
        }
        return entries.isEmpty() ? NONE : new CubeRoofsData(entries);
    }

    /** Puts them in the client's roofs: over each of the cube's columns, the roofs above the cube become these. */
    public void applyTo(SkyRoofs roofs, CubePos pos) {
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                int column = dx + dz * CubicConstants.DIAMETER_IN_SECTIONS;
                List<SkyRoofs.Roof> above = new ArrayList<>();
                for (Entry entry : this.entries) {
                    if (entry.column == column) {
                        above.add(new SkyRoofs.Roof(entry.cubeY, entry.top));
                    }
                }
                roofs.replaceAbove(Coords.cubeToSection(pos.getX(), dx), Coords.cubeToSection(pos.getZ(), dz), pos.getY(), above);
            }
        }
    }
}
