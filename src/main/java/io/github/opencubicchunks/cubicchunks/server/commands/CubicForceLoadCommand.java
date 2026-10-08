package io.github.opencubicchunks.cubicchunks.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /cubicchunks forceload add|remove <pos> [<radius>]}: keeps the cubes around a block loaded, the cubic counterpart of vanilla's
 * column-based {@code /forceload}. 26.x has no spawn chunks, so without players this is what holds cubes in memory. The tickets are vanilla
 * FORCED tickets keyed by the cube, so they persist with the level's other tickets. {@code /cubicchunks loaded} reports how many holders are
 * loaded.
 */
public final class CubicForceLoadCommand {
    private static final int MAX_RADIUS = 4;
    private static final SimpleCommandExceptionType ERROR_NOT_CUBIC = new SimpleCommandExceptionType(
            Component.literal("This dimension does not use cubic chunks; use /forceload"));

    private CubicForceLoadCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cubicchunks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("loaded").executes(c -> loaded(c.getSource())))
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
