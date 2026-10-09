package io.github.opencubicchunks.cubicchunks.world.storage;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.SerializableChunkDataAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * What a cubic level keeps of a column: its structure starts and references (vanilla's format), when its cube generator places structures
 * (CubeGenerator.usesStructures). The rest of a column holds nothing in a cubic level and is made again on load. Kept so that a structure is
 * worked out once: worked out again in another session, its place could differ a little from the cubes already built with it.
 */
public final class ColumnSerializer {
    private static final int FORMAT = 1;

    private ColumnSerializer() {
    }

    /** Whether the column has anything to keep (its structure starts are worked out). */
    public static boolean worthSaving(ChunkAccess column) {
        return column.getPersistedStatus().isOrAfter(ChunkStatus.STRUCTURE_STARTS);
    }

    public static CompoundTag write(ServerLevel level, ChunkAccess column) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ColumnFormat", FORMAT);
        ChunkStatus status = column.getPersistedStatus().isOrAfter(ChunkStatus.STRUCTURE_REFERENCES) ? ChunkStatus.STRUCTURE_REFERENCES
                : ChunkStatus.STRUCTURE_STARTS;
        tag.putString("Status", status.getName());
        tag.put("structures", SerializableChunkDataAccess.cc_packStructureData(StructurePieceSerializationContext.fromLevel(level), column.getPos(),
                column.getAllStarts(), column.getAllReferences()));
        return tag;
    }

    /** Puts a saved column's structures into the new (empty) column, which then carries on from the status they were saved at. */
    public static void read(ServerLevel level, CompoundTag tag, ProtoChunk column) {
        ChunkStatus status = ChunkStatus.byName(tag.getStringOr("Status", ChunkStatus.EMPTY.getName()));
        if (!status.isOrAfter(ChunkStatus.STRUCTURE_STARTS)) {
            return;
        }
        CompoundTag structures = tag.getCompoundOrEmpty("structures");
        column.setAllStarts(SerializableChunkDataAccess.cc_unpackStructureStart(StructurePieceSerializationContext.fromLevel(level), structures,
                level.getSeed()));
        if (status.isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) {
            column.setAllReferences(SerializableChunkDataAccess.cc_unpackStructureReferences(level.registryAccess(), column.getPos(), structures));
        }
        column.setPersistedStatus(status);
        column.tryMarkSaved(); // as read
    }
}
