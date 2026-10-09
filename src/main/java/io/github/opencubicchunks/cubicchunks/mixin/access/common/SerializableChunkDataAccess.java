package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's structure data format for a chunk, which a cubic level's columns are saved in (see world/storage/ColumnSerializer). */
@Mixin(SerializableChunkData.class)
public interface SerializableChunkDataAccess {
    @Invoker("packStructureData")
    static CompoundTag cc_packStructureData(StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> starts,
            Map<Structure, LongSet> references) {
        throw new AssertionError();
    }

    @Invoker("unpackStructureStart")
    static Map<Structure, StructureStart> cc_unpackStructureStart(StructurePieceSerializationContext context, CompoundTag tag, long seed) {
        throw new AssertionError();
    }

    @Invoker("unpackStructureReferences")
    static Map<Structure, LongSet> cc_unpackStructureReferences(RegistryAccess registryAccess, ChunkPos pos, CompoundTag tag) {
        throw new AssertionError();
    }
}
