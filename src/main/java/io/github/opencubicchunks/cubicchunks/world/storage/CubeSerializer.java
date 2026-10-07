package io.github.opencubicchunks.cubicchunks.world.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ImposterProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.ProtoCube;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

/**
 * A cube to and from NBT, the cubic counterpart of vanilla's SerializableChunkData: a {@link Snapshot} is taken on the server thread and
 * written to NBT off it; saved NBT is {@link #parse parsed} off the server thread and {@link Parsed#read read} into a cube on it.
 * <p>
 * Saved: the sections' blocks and biomes, block entities, block and fluid ticks, post-processing, structure starts and references, the
 * generation status, inhabited time, and for cubes still generating their pending entities and carving mask. Not yet: light and heightmaps
 * (cubes have neither yet), and entities of finished cubes (they need cubic entity storage).
 */
public final class CubeSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Version of this format, so later changes can read cubes saved before them. */
    public static final int FORMAT_VERSION = 1;
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    private static final Codec<List<SavedTick<Block>>> BLOCK_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf();
    private static final Codec<List<SavedTick<Fluid>>> FLUID_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf();

    private CubeSerializer() {
    }

    /** The generation status a saved cube's NBT records ({@link ChunkStatus#EMPTY} without one). */
    public static ChunkStatus statusOf(@Nullable CompoundTag tag) {
        return tag != null ? tag.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY) : ChunkStatus.EMPTY;
    }

    /** What is saved of a cube, copied on the server thread so it can be written to NBT on another. */
    public record Snapshot(
            Registry<Biome> biomeRegistry, CubePos pos, long lastUpdate, long inhabitedTime, ChunkStatus status, LevelChunkSection[] sections,
            List<CompoundTag> blockEntities, List<CompoundTag> entities, @Nullable long[] carvingMask, ChunkAccess.PackedTicks ticks,
            ShortList[] postProcessing, boolean lightCorrect, CompoundTag structures
    ) {
        public CompoundTag write() {
            CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
            tag.putInt("CubicChunksVersion", FORMAT_VERSION);
            tag.putInt("xPos", pos.getX());
            tag.putInt("yPos", pos.getY());
            tag.putInt("zPos", pos.getZ());
            tag.putLong("LastUpdate", lastUpdate);
            tag.putLong("InhabitedTime", inhabitedTime);
            tag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(status).toString());

            Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = biomeCodec(biomeRegistry);
            ListTag sectionList = new ListTag();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section == null) {
                    continue;
                }
                CompoundTag sectionTag = new CompoundTag();
                sectionTag.putByte("i", (byte) i);
                sectionTag.store("block_states", BLOCK_STATE_CODEC, section.getStates());
                sectionTag.store("biomes", biomeCodec, section.getBiomes());
                sectionList.add(sectionTag);
            }
            tag.put("sections", sectionList);
            if (lightCorrect) {
                tag.putBoolean("isLightOn", true);
            }

            ListTag blockEntityList = new ListTag();
            blockEntityList.addAll(blockEntities);
            tag.put("block_entities", blockEntityList);
            if (status.getChunkType() == ChunkType.PROTOCHUNK) {
                ListTag entityList = new ListTag();
                entityList.addAll(entities);
                tag.put("entities", entityList);
                if (carvingMask != null) {
                    tag.putLongArray("carving_mask", carvingMask);
                }
            }

            tag.store("block_ticks", BLOCK_TICKS_CODEC, ticks.blocks());
            tag.store("fluid_ticks", FLUID_TICKS_CODEC, ticks.fluids());
            tag.put("PostProcessing", packOffsets(postProcessing));
            tag.put("structures", structures);
            return tag;
        }
    }

    /** A snapshot of the cube, taken on the server thread. */
    public static Snapshot copyOf(ServerLevel level, CubeAccess cube) {
        if (!cube.canBeSerialized()) {
            throw new IllegalArgumentException("Cube can't be serialized: " + cube.cc_getCubePos());
        }
        CubePos pos = cube.cc_getCubePos();
        LevelChunkSection[] sections = new LevelChunkSection[cube.getSections().length];
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = cube.getSections()[i];
            sections[i] = section == null ? null : section.copy();
        }

        List<CompoundTag> blockEntities = new ArrayList<>(cube.getBlockEntitiesPos().size());
        for (BlockPos blockPos : cube.getBlockEntitiesPos()) {
            CompoundTag blockEntityTag = cube.getBlockEntityNbtForSaving(blockPos, level.registryAccess());
            if (blockEntityTag != null) {
                blockEntities.add(blockEntityTag);
            }
        }

        ChunkStatus status = cube.getPersistedStatus();
        List<CompoundTag> entities = new ArrayList<>();
        long[] carvingMask = null;
        if (status.getChunkType() == ChunkType.PROTOCHUNK && cube instanceof ProtoCube protoCube) {
            entities.addAll(protoCube.getEntities());
            CarvingMask mask = protoCube.getCarvingMask();
            if (mask != null) {
                carvingMask = mask.toArray();
            }
        }

        ShortList[] postProcessing = Arrays.stream(cube.getPostProcessing())
                .map(list -> list != null ? new ShortArrayList(list) : null)
                .toArray(ShortList[]::new);
        CompoundTag structures = packStructures(StructurePieceSerializationContext.fromLevel(level), chunkPosOf(pos), cube.getAllStarts(),
                cube.getAllReferences());

        return new Snapshot(level.registryAccess().lookupOrThrow(Registries.BIOME), pos, level.getGameTime(), cube.getInhabitedTime(), status,
                sections, blockEntities, entities, carvingMask, cube.getTicksForSerialization(level.getGameTime()), postProcessing,
                cube.isLightCorrect(), structures);
    }

    /** A saved cube read from NBT, ready to be turned into a cube on the server thread. */
    public record Parsed(
            CubePos pos, long inhabitedTime, ChunkStatus status, LevelChunkSection[] sections, List<CompoundTag> blockEntities,
            List<CompoundTag> entities, @Nullable long[] carvingMask, ChunkAccess.PackedTicks ticks, ShortList[] postProcessing,
            boolean lightCorrect, CompoundTag structures
    ) {
        /**
         * The cube, made on the server thread: a {@link ProtoCube} if it was still generating, else an {@link ImposterProtoCube} wrapping
         * the finished {@link LevelCube}, as vanilla does for chunks.
         */
        public ProtoCube read(ServerLevel level, CubePos expected) {
            if (!pos.equals(expected)) {
                LOGGER.error("Cube saved at {} says it is {}; loading it at {}", expected, pos, expected);
            }
            Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            Map<Structure, StructureStart> starts = unpackStructureStarts(StructurePieceSerializationContext.fromLevel(level), structures,
                    level.getSeed());
            Map<Structure, LongSet> references = unpackStructureReferences(level.registryAccess(), structures);

            if (status.getChunkType() == ChunkType.LEVELCHUNK) {
                LevelCube cube = new LevelCube(level, expected, UpgradeData.EMPTY, new LevelChunkTicks<>(ticks.blocks()),
                        new LevelChunkTicks<>(ticks.fluids()), inhabitedTime, sections, postLoad(level, blockEntities), null);
                cube.setLightCorrect(lightCorrect);
                cube.setAllStarts(starts);
                cube.setAllReferences(references);
                addPostProcessing(cube);
                return new ImposterProtoCube(cube, false);
            }

            ProtoCube cube = new ProtoCube(expected, UpgradeData.EMPTY, sections, ProtoChunkTicks.load(ticks.blocks()),
                    ProtoChunkTicks.load(ticks.fluids()), level, biomeRegistry, null);
            cube.setInhabitedTime(inhabitedTime);
            cube.setPersistedStatus(status);
            if (status.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                cube.setLightEngine(level.getChunkSource().getLightEngine());
            }
            cube.setLightCorrect(lightCorrect);
            cube.setAllStarts(starts);
            cube.setAllReferences(references);
            addPostProcessing(cube);
            for (CompoundTag entity : entities) {
                cube.addEntity(entity);
            }
            for (CompoundTag blockEntity : blockEntities) {
                cube.setBlockEntityNbt(blockEntity);
            }
            if (carvingMask != null) {
                cube.setCarvingMask(new CarvingMask(carvingMask, cube.getMinY()));
            }
            return cube;
        }

        private void addPostProcessing(CubeAccess cube) {
            for (int i = 0; i < postProcessing.length && i < CubicConstants.SECTION_COUNT; i++) {
                if (postProcessing[i] != null && !postProcessing[i].isEmpty()) {
                    cube.addPackedPostProcess(postProcessing[i], i);
                }
            }
        }
    }

    /** Reads saved NBT (off the server thread); null when it holds no cube. */
    public static @Nullable Parsed parse(RegistryAccess registries, CompoundTag tag) {
        if (tag.getString("Status").isEmpty()) {
            return null;
        }
        CubePos pos = CubePos.of(tag.getIntOr("xPos", 0), tag.getIntOr("yPos", 0), tag.getIntOr("zPos", 0));
        ChunkStatus status = statusOf(tag);
        Registry<Biome> biomeRegistry = registries.lookupOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = biomeCodec(biomeRegistry);

        LevelChunkSection[] sections = new LevelChunkSection[CubicConstants.SECTION_COUNT];
        ListTag sectionList = tag.getListOrEmpty("sections");
        for (int n = 0; n < sectionList.size(); n++) {
            CompoundTag sectionTag = sectionList.getCompoundOrEmpty(n);
            int i = sectionTag.getByteOr("i", (byte) -1);
            if (i < 0 || i >= sections.length) {
                LOGGER.error("Cube {} has a section with index {}, outside 0..{}; skipping it", pos, i, sections.length - 1);
                continue;
            }
            PalettedContainer<BlockState> states = sectionTag.getCompound("block_states")
                    .map(states1 -> BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, states1)
                            .promotePartial(error -> logErrors(pos, i, error))
                            .getOrThrow(CubeReadException::new))
                    .orElseGet(() -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                            PalettedContainer.Strategy.SECTION_STATES));
            PalettedContainerRO<Holder<Biome>> biomes = sectionTag.getCompound("biomes")
                    .map(biomes1 -> biomeCodec.parse(NbtOps.INSTANCE, biomes1)
                            .promotePartial(error -> logErrors(pos, i, error))
                            .getOrThrow(CubeReadException::new))
                    .orElseGet(() -> new PalettedContainer<>(biomeRegistry.asHolderIdMap(), biomeRegistry.getOrThrow(Biomes.PLAINS),
                            PalettedContainer.Strategy.SECTION_BIOMES));
            sections[i] = new LevelChunkSection(states, biomes);
        }

        ListTag postProcessingList = tag.getListOrEmpty("PostProcessing");
        ShortList[] postProcessing = new ShortList[postProcessingList.size()];
        for (int i = 0; i < postProcessingList.size(); i++) {
            ListTag offsets = postProcessingList.getListOrEmpty(i);
            ShortList list = new ShortArrayList(offsets.size());
            for (int j = 0; j < offsets.size(); j++) {
                list.add(offsets.getShortOr(j, (short) 0));
            }
            postProcessing[i] = list;
        }

        return new Parsed(pos, tag.getLongOr("InhabitedTime", 0L), status, sections,
                tag.getListOrEmpty("block_entities").compoundStream().toList(),
                tag.getListOrEmpty("entities").compoundStream().toList(),
                tag.getLongArray("carving_mask").orElse(null),
                new ChunkAccess.PackedTicks(tag.read("block_ticks", BLOCK_TICKS_CODEC).orElse(List.of()),
                        tag.read("fluid_ticks", FLUID_TICKS_CODEC).orElse(List.of())),
                postProcessing, tag.getBooleanOr("isLightOn", false), tag.getCompoundOrEmpty("structures"));
    }

    private static @Nullable LevelCube.PostLoadProcessor postLoad(ServerLevel level, List<CompoundTag> blockEntities) {
        if (blockEntities.isEmpty()) {
            return null;
        }
        return cube -> {
            for (CompoundTag tag : blockEntities) {
                if (tag.getBooleanOr("keepPacked", false)) {
                    cube.setBlockEntityNbt(tag);
                } else {
                    BlockPos pos = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
                    BlockEntity blockEntity = BlockEntity.loadStatic(pos, cube.getBlockState(pos), tag, level.registryAccess());
                    if (blockEntity != null) {
                        cube.setBlockEntity(blockEntity);
                    }
                }
            }
        };
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES,
                biomeRegistry.getOrThrow(Biomes.PLAINS));
    }

    private static ChunkPos chunkPosOf(CubePos pos) {
        return new ChunkPos(Coords.cubeToSection(pos.getX(), 0), Coords.cubeToSection(pos.getZ(), 0));
    }

    private static CompoundTag packStructures(
            StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> starts, Map<Structure, LongSet> references
    ) {
        CompoundTag tag = new CompoundTag();
        CompoundTag startsTag = new CompoundTag();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
            ResourceLocation key = registry.getKey(entry.getKey());
            startsTag.put(key.toString(), entry.getValue().createTag(context, pos));
        }
        tag.put("starts", startsTag);
        CompoundTag referencesTag = new CompoundTag();
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                referencesTag.putLongArray(registry.getKey(entry.getKey()).toString(), entry.getValue().toLongArray());
            }
        }
        tag.put("References", referencesTag);
        return tag;
    }

    private static Map<Structure, StructureStart> unpackStructureStarts(StructurePieceSerializationContext context, CompoundTag tag, long seed) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        CompoundTag startsTag = tag.getCompoundOrEmpty("starts");
        for (String key : startsTag.keySet()) {
            Structure structure = registry.getValue(ResourceLocation.tryParse(key));
            if (structure == null) {
                LOGGER.error("Unknown structure start: {}", key);
                continue;
            }
            StructureStart start = StructureStart.loadStaticStart(context, startsTag.getCompoundOrEmpty(key), seed);
            if (start != null) {
                map.put(structure, start);
            }
        }
        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess registries, CompoundTag tag) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> registry = registries.lookupOrThrow(Registries.STRUCTURE);
        CompoundTag referencesTag = tag.getCompoundOrEmpty("References");
        for (String key : referencesTag.keySet()) {
            Structure structure = registry.getValue(ResourceLocation.tryParse(key));
            if (structure == null) {
                LOGGER.warn("Found reference to unknown structure '{}' in a cube, discarding", key);
                continue;
            }
            referencesTag.getLongArray(key).ifPresent(longs -> map.put(structure, new LongOpenHashSet(longs)));
        }
        return map;
    }

    private static ListTag packOffsets(ShortList[] offsets) {
        ListTag list = new ListTag();
        for (ShortList shorts : offsets) {
            ListTag inner = new ListTag();
            if (shorts != null) {
                for (int i = 0; i < shorts.size(); i++) {
                    inner.add(ShortTag.valueOf(shorts.getShort(i)));
                }
            }
            list.add(inner);
        }
        return list;
    }

    private static void logErrors(CubePos pos, int sectionIndex, String error) {
        LOGGER.error("Recoverable errors when loading section {} of cube {}: {}", sectionIndex, pos, error);
    }

    /** Saved cube data that cannot be read. */
    public static class CubeReadException extends net.minecraft.nbt.NbtException {
        public CubeReadException(String message) {
            super(message);
        }
    }
}
