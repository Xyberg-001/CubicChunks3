package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.blockscan;

import java.util.function.Predicate;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.blockscan.BlockScanUtils;
import net.minecraft.world.level.blockscan.BlockStateConsumer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x's block scans (LevelReader.findBlocksIn: the server's check for a player floating, containsAnyLiquid, mobs looking for water or land)
 * read each section from its chunk; a cubic level's chunks hold no blocks, so every scan found only air there (players standing on the
 * ground were kicked for flying). In a cubic level the sections come from the cubes, loaded as vanilla loads the chunks it reads (for a
 * level: other readers, such as a world generation region, keep vanilla's way).
 */
@Mixin(BlockScanUtils.class)
public abstract class MixinBlockScanUtils {
    @Inject(method = "sectionBasedScan", at = @At("HEAD"), cancellable = true)
    private static void cc_scanCubes(LevelReader level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Predicate<BlockState> predicate,
            BlockStateConsumer consumer, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level && level instanceof CanBeCubic cubic && cubic.cc_isCubic() && level instanceof CubicLevelReader cubes) {
            cir.setReturnValue(cc_sectionBasedScan(level, cubes, minX, minY, minZ, maxX, maxY, maxZ, predicate, consumer));
        }
    }

    /** As vanilla's sectionBasedScan, with each section taken from its cube. */
    private static boolean cc_sectionBasedScan(LevelReader level, CubicLevelReader cubes, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            Predicate<BlockState> predicate, BlockStateConsumer consumer) {
        int minSectionX = SectionPos.blockToSectionCoord(minX);
        int minSectionY = Math.max(level.getMinSectionY(), SectionPos.blockToSectionCoord(minY));
        int minSectionZ = SectionPos.blockToSectionCoord(minZ);
        int maxSectionX = SectionPos.blockToSectionCoord(maxX);
        int maxSectionY = Math.min(level.getMaxSectionY(), SectionPos.blockToSectionCoord(maxY));
        int maxSectionZ = SectionPos.blockToSectionCoord(maxZ);
        for (int x = minSectionX; x <= maxSectionX; x++) {
            for (int z = minSectionZ; z <= maxSectionZ; z++) {
                for (int y = minSectionY; y <= maxSectionY; y++) {
                    CubeAccess cube = cubes.cc_getCube(Coords.sectionToCube(x), Coords.sectionToCube(y), Coords.sectionToCube(z), ChunkStatus.FULL, true);
                    if (cube == null) {
                        continue;
                    }
                    LevelChunkSection section = cube.getSection(Coords.sectionToIndex(x, y, z));
                    if (section.maybeHas(predicate)) {
                        int sectionOriginX = SectionPos.sectionToBlockCoord(x);
                        int sectionOriginY = SectionPos.sectionToBlockCoord(y);
                        int sectionOriginZ = SectionPos.sectionToBlockCoord(z);
                        int fromX = SectionPos.sectionRelative(Math.max(sectionOriginX, minX));
                        int fromY = SectionPos.sectionRelative(Math.max(sectionOriginY, minY));
                        int fromZ = SectionPos.sectionRelative(Math.max(sectionOriginZ, minZ));
                        int toX = SectionPos.sectionRelative(Math.min(SectionPos.sectionToBlockCoord(x, 15), maxX));
                        int toY = SectionPos.sectionRelative(Math.min(SectionPos.sectionToBlockCoord(y, 15), maxY));
                        int toZ = SectionPos.sectionRelative(Math.min(SectionPos.sectionToBlockCoord(z, 15), maxZ));
                        BlockPos origin = new BlockPos(sectionOriginX, sectionOriginY, sectionOriginZ);
                        if (BlockScanUtils.findBlocksInSection(section, origin, fromX, fromY, fromZ, toX, toY, toZ, predicate, consumer)) {
                            return true;
                        }
                    }
                }
            }
        }
        boolean belowWorld = minY < level.getMinY();
        boolean aboveWorld = maxY > level.getMaxY();
        if ((belowWorld || aboveWorld) && predicate.test(Blocks.AIR.defaultBlockState())) {
            if (belowWorld) {
                for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, level.getMinY() - 1, maxZ)) {
                    if (consumer.apply(pos, Blocks.AIR.defaultBlockState()).shouldAbort()) {
                        return true;
                    }
                }
            }
            if (aboveWorld) {
                for (BlockPos pos : BlockPos.betweenClosed(minX, level.getMaxY() + 1, minZ, maxX, maxY, maxZ)) {
                    if (consumer.apply(pos, Blocks.AIR.defaultBlockState()).shouldAbort()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
