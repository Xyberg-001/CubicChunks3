package io.github.opencubicchunks.cubicchunks.server.commands;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLight;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /cubicchunks forceload add|remove <pos> [<radius>]}: keeps the cubes around a block loaded, the cubic counterpart of vanilla's
 * column-based {@code /forceload}. 26.x has no spawn chunks, so without players this is what holds cubes in memory. The tickets are vanilla
 * FORCED tickets keyed by the cube, so they persist with the level's other tickets. {@code /cubicchunks loaded} reports how many holders are
 * loaded, and {@code /cubicchunks light <pos>} the server's light at a block.
 */
public final class CubicChunksCommand {
    private static final int MAX_RADIUS = 4;
    private static final SimpleCommandExceptionType ERROR_NOT_CUBIC = new SimpleCommandExceptionType(
            Component.literal("This dimension does not use cubic chunks; use /forceload"));

    private CubicChunksCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cubicchunks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("loaded").executes(c -> loaded(c.getSource())))
                .then(Commands.literal("column").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> column(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                .then(Commands.literal("light").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> light(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))))
                .then(Commands.literal("forceload")
                        .then(Commands.literal("add").then(position(true)))
                        .then(Commands.literal("remove").then(position(false)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> position(boolean add) {
        return Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(c -> change(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"), 0, add))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                        .executes(c -> change(c.getSource(), BlockPosArgument.getBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "radius"), add)));
    }

    /** How many cube and column holders the level has (vanilla's loaded-chunk count, which counts both in a cubic level). */
    private static int loaded(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int holders = level.getChunkSource().getLoadedChunksCount();
        source.sendSuccess(() -> Component.literal(holders + " cube and column holders loaded in " + level.dimension().identifier()), false);
        return holders;
    }

    /**
     * The column at x, z top down, from the cubes the server has loaded (none is loaded for this): its runs of the same block from the
     * highest that is not air, eight at most (runs are joined across cubes that are not loaded).
     */
    private static int column(CommandSourceStack source, int x, int z) {
        ServerLevel level = source.getLevel();
        ServerCubeCache cubes = (ServerCubeCache) level.getChunkSource();
        List<String> runs = new ArrayList<>();
        BlockState run = null;
        String biome = null;
        int runTop = 0;
        int runBottom = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cubeY = Coords.blockToCube(CubicHeight.maxY(level)); cubeY >= Coords.blockToCube(CubicHeight.minY(level)) && runs.size() < 8; cubeY--) {
            CubeAccess cube = cubes.cc_getFullCubeNow(CubePos.of(Coords.blockToCube(x), cubeY, Coords.blockToCube(z)));
            if (cube == null) {
                continue;
            }
            for (int y = Coords.cubeToMaxBlock(cubeY); y >= Coords.cubeToMinBlock(cubeY) && runs.size() < 8; y--) {
                BlockState state = cube.getBlockState(pos.set(x, y, z));
                if (biome == null && !state.isAir()) {
                    biome = cube.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                }
                if (state != run) {
                    addRun(runs, run, runTop, runBottom);
                    run = state;
                    runTop = y;
                }
                runBottom = y;
            }
        }
        addRun(runs, run, runTop, runBottom);
        String text = "Column " + x + ", " + z + (biome == null ? "" : " (" + biome + ")") + ":"
                + (runs.isEmpty() ? " nothing but air in the loaded cubes" : " " + String.join(", ", runs));
        source.sendSuccess(() -> Component.literal(text), false);
        return runs.size();
    }

    /** A run for the column command: air only once something solid is above it (the sky is left out). */
    private static void addRun(List<String> runs, BlockState state, int top, int bottom) {
        if (state == null || runs.size() >= 8 || (state.isAir() && runs.isEmpty())) {
            return;
        }
        runs.add((top == bottom ? String.valueOf(top) : top + ".." + bottom) + " " + state.getBlock().getName().getString());
    }

    /** The server's sky and block light at a position (the client works out its own; see CubicLight). */
    private static int light(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        CubicLight light = ((CubeSource) level.getChunkSource()).cc_cubicLight();
        String column = light == null ? "" : "; " + light.describeSky(pos.getX(), pos.getZ());
        source.sendSuccess(() -> Component.literal("Light at " + pos.toShortString() + ": sky " + sky + ", block " + block + " ("
                + level.getBlockState(pos).getBlock().getName().getString() + ")" + column), false);
        return Math.max(sky, block);
    }

    private static int change(CommandSourceStack source, BlockPos pos, int radius, boolean add) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        if (!((CanBeCubic) level).cc_isCubic()) {
            throw ERROR_NOT_CUBIC.create();
        }
        CubePos center = CubePos.from(pos);
        CubeSource cubes = (CubeSource) level.getChunkSource();
        int changed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (cubes.cc_updateCubeForced(CubePos.of(center.getX() + dx, center.getY() + dy, center.getZ() + dz), add)) {
                        changed++;
                    }
                }
            }
        }
        int count = changed;
        source.sendSuccess(() -> Component.literal((add ? "Force loading " : "No longer force loading ") + count + " cube(s) around cube "
                + center.getX() + " " + center.getY() + " " + center.getZ() + " in " + level.dimension().identifier()), true);
        return count;
    }
}
