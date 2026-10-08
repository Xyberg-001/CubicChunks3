package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The spawn state's own checks, which NaturalSpawner.spawnForChunk uses and a cube's spawning uses the same way. */
@Mixin(NaturalSpawner.SpawnState.class)
public interface NaturalSpawner$SpawnStateAccess {
    @Invoker("canSpawnForCategoryLocal") boolean cc_canSpawnForCategoryLocal(MobCategory category, ChunkPos chunkPos);

    @Invoker("canSpawn") boolean cc_canSpawn(EntityType<?> type, Level level, BlockPos pos, ChunkAccess chunk);

    @Invoker("afterSpawn") void cc_afterSpawn(Mob mob, ChunkAccess chunk);
}
