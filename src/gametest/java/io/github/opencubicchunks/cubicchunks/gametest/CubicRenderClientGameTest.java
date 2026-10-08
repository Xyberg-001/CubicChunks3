package io.github.opencubicchunks.cubicchunks.gametest;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.world.level.LightLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Renders a cubic singleplayer world at the old surface and well outside the old height range, logging what the client holds and taking a
 * screenshot at each spot. The player is a spectator, so it neither falls nor collides.
 */
public class CubicRenderClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogManager.getLogger("cc-gametest");
    private static final int LOAD_TICKS = 200;
    private static final int SPAWN_TICKS = 600;
    private static final int THUNDER_TICKS = 1200;

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            boolean cubic = context.computeOnClient(mc -> ((CanBeCubic) mc.level).cc_isCubic());
            LOGGER.info("[cc-gametest] client level cubic: {}", cubic);
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("time set noon");

            if (stage("loading")) loadingScreen(context);
            if (stage("night")) night(context, world);
            if (stage("ground")) visit(context, world, "ground", 0, 40, 0, null);
            if (stage("y1000")) visit(context, world, "y1000", 0, 1010, 0,
                    new String[] { "fill -10 1000 -10 10 1000 10 minecraft:diamond_block", "summon minecraft:pig 3 1001 3" });
            if (stage("y-300")) visit(context, world, "y-300", 0, -290, 0,
                    new String[] { "fill -10 -299 -10 10 -280 10 minecraft:air", "fill -10 -300 -10 10 -300 10 minecraft:emerald_block",
                            "setblock 0 -295 3 minecraft:glowstone" });
            if (stage("ticks")) randomTicks(context, world, "ticks-y1000", 1000, true);
            if (stage("ticks")) randomTicks(context, world, "ticks-y-300", -300, false);
            if (stage("thunder")) thunder(context, world);
            if (stage("scheduled")) scheduledTicks(context, world);
            if (stage("audit")) lightAudit(context, world);
            if (stage("difficulty")) regionalDifficulty(context, world);
            if (stage("cliententities")) clientEntities(context, world);
            if (stage("tint")) tintCaches(context);
            if (stage("lightsync")) lightSync(context, world);
        }
    }

    /** Whether to run a stage: all of them, or those named in the CC_GT_ONLY environment variable (comma separated). */
    private static boolean stage(String name) {
        String only = System.getenv("CC_GT_ONLY");
        return only == null || only.isBlank() || java.util.Arrays.asList(only.split(",")).contains(name);
    }

    /** Natural spawning at night around a survival player standing on the cubic terrain: what spawned, and where. */
    private static void night(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
        world.getServer().runCommand("gamerule spawn_mobs true"); // test worlds are made with mob spawning off
        world.getServer().runCommand("gamerule spawn_monsters true");
        // after the rules, a difficulty change updates the level's spawning flags (set while the test world had spawning off)
        world.getServer().runCommand("difficulty peaceful");
        world.getServer().runCommand("difficulty normal");
        world.getServer().runCommand("time set midnight");
        world.getServer().runCommand("tp @a 0 40 0");
        context.waitTicks(SPAWN_TICKS);
        String report = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            var player = level.players().get(0);
            var mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(128));
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (var mob : mobs) {
                counts.merge(mob.getType().getCategory().getName(), 1, Integer::sum);
                minY = Math.min(minY, mob.blockPosition().getY());
                maxY = Math.max(maxY, mob.blockPosition().getY());
            }
            var state = level.getChunkSource().getLastSpawnState();
            return "player at " + player.blockPosition() + ", " + mobs.size() + " mobs within 128 " + counts + (mobs.isEmpty() ? "" : " at Y " + minY + ".." + maxY)
                    + ", spawnable chunk count " + (state == null ? "?" : state.getSpawnableChunkCount() + " counts " + state.getMobCategoryCounts());
        });
        LOGGER.info("[cc-gametest] night: {}", report);
        context.takeScreenshot("cc-night");
        world.getServer().runCommand("gamemode spectator @a");
        world.getServer().runCommand("time set noon");
    }

    /**
     * Random ticks in cubes outside the old height range, raining, at a high tick speed: grass spreading over dirt, wheat growing, and (in the
     * open sky at Y 1000, cold that high) water freezing and snow settling; under a glowstone ceiling at Y -300 there should be no snow or ice.
     */
    private static void randomTicks(ClientGameTestContext context, TestSingleplayerContext world, String name, int y, boolean sky) {
        int x = 40;
        int z = 40;
        world.getServer().runCommand("tp @a " + (x + 4) + " " + (y + 12) + " " + (z + 4) + " 0 90");
        context.waitTicks(LOAD_TICKS);
        String[] build = {
            fill(x - 2, y - 1, z - 2, x + 18, y + 6, z + 18, "air"),
            fill(x - 2, y - 1, z - 2, x + 18, y - 1, z + 18, "stone"),
            fill(x, y, z, x + 8, y, z + 8, "dirt"),
            "setblock " + (x + 4) + " " + y + " " + (z + 4) + " minecraft:grass_block",
            fill(x + 10, y - 1, z, x + 16, y - 1, z + 6, "water"), // a pool in the floor
            fill(x, y, z + 10, x + 8, y, z + 10, "farmland"),
            sky ? "say open sky" : fill(x - 2, y + 2, z - 2, x + 18, y + 2, z + 18, "glowstone"),
        };
        for (String command : build) {
            world.getServer().runCommand(command);
        }
        context.waitTicks(20); // the ceiling's light first: wheat placed in the dark breaks at once
        world.getServer().runCommand(fill(x, y + 1, z + 10, x + 8, y + 1, z + 10, "wheat"));
        world.getServer().runCommand("weather rain");
        world.getServer().runCommand("time set noon");
        world.getServer().runCommand("gamerule random_tick_speed 500");
        context.waitTicks(300);
        world.getServer().runCommand("gamerule random_tick_speed 3");
        world.getServer().runCommand("weather clear");
        String report = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            int grass = 0;
            int snow = 0;
            int ice = 0;
            int water = 0;
            int wheat = 0;
            int wheatAges = 0;
            int wheatRipe = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx <= 16; dx++) {
                for (int dz = 0; dz <= 10; dz++) {
                    var state = level.getBlockState(pos.set(x + dx, y, z + dz));
                    var pool = level.getBlockState(pos.set(x + dx, y - 1, z + dz));
                    var above = level.getBlockState(pos.set(x + dx, y + 1, z + dz));
                    if (state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) grass++;
                    if (pool.is(net.minecraft.world.level.block.Blocks.ICE)) ice++;
                    if (pool.is(net.minecraft.world.level.block.Blocks.WATER)) water++;
                    if (above.is(net.minecraft.world.level.block.Blocks.SNOW)) snow++;
                    if (above.is(net.minecraft.world.level.block.Blocks.WHEAT)) {
                        int age = above.getValue(net.minecraft.world.level.block.CropBlock.AGE);
                        wheat++;
                        wheatAges += age;
                        if (age == 7) wheatRipe++;
                    }
                }
            }
            return "grass " + grass + "/81, wheat " + wheat + "/9, ripe " + wheatRipe + " (age sum " + wheatAges + "), ice " + ice + " water " + water + " of 49, snow on top "
                    + snow + ", light at wheat " + level.getMaxLocalRawBrightness(new BlockPos(x + 4, y + 1, z + 10));
        });
        LOGGER.info("[cc-gametest] {}: {}", name, report);
        context.takeScreenshot("cc-" + name);
    }

    private static final java.util.List<BlockPos> BOLTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean countingBolts;

    static {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (countingBolts && entity instanceof net.minecraft.world.entity.LightningBolt) {
                BOLTS.add(entity.blockPosition());
            }
        });
    }

    /**
     * A thunderstorm over the old surface: where natural strikes land (on top of the ground, under the open sky). Then a lightning rod on
     * the ground draws a strike from a few blocks away.
     */
    private static void thunder(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0 60 0 0 60");
        context.waitTicks(LOAD_TICKS);
        // thunder, like spawning, only rolls near players who aren't spectators
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
        world.getServer().runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
        world.getServer().runCommand("weather thunder");
        BOLTS.clear();
        countingBolts = true;
        context.waitTicks(THUNDER_TICKS);
        countingBolts = false;
        String natural = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            StringBuilder out = new StringBuilder(BOLTS.size() + " natural strikes in " + THUNDER_TICKS + " ticks:");
            for (BlockPos bolt : BOLTS) {
                out.append(" ").append(bolt.toShortString()).append(" on ").append(level.getBlockState(bolt.below()).getBlock().getName().getString())
                        .append(level.canSeeSky(bolt) ? " (sky)" : " (no sky)");
            }
            return out.toString();
        });
        LOGGER.info("[cc-gametest] thunder: {}", natural);

        String columns = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            StringBuilder out = new StringBuilder();
            int[][] spots = { { 10, 30 }, { -25, 12 }, { 33, -18 }, { -40, -40 }, { 5, 45 } };
            for (int[] spot : spots) {
                int x = spot[0];
                int z = spot[1];
                java.util.List<String> strikes = new java.util.ArrayList<>();
                for (int cubeY = -4; cubeY <= 6; cubeY++) {
                    var cube = ((io.github.opencubicchunks.cubicchunks.world.level.CubicLevel) level).cc_getCube(
                            io.github.opencubicchunks.cc_core.utils.Coords.blockToCube(x), cubeY, io.github.opencubicchunks.cc_core.utils.Coords.blockToCube(z));
                    var bolt = io.github.opencubicchunks.cubicchunks.world.level.CubicThunder.strikeIn(level, cube, x, z);
                    if (bolt != null) {
                        strikes.add("cube " + cubeY + " -> " + bolt.blockPosition().toShortString());
                    }
                }
                out.append(" [").append(x).append(",").append(z).append(" ground top ").append(surface(level, x, z).getY()).append(": ")
                        .append(strikes).append("]");
            }
            return out.toString();
        });
        LOGGER.info("[cc-gametest] thunder columns:{}", columns);

        String rod = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            BlockPos rodPos = surface(level, 20, 20);
            level.setBlockAndUpdate(rodPos, net.minecraft.world.level.block.Blocks.LIGHTNING_ROD.asList().get(0).defaultBlockState());
            BlockPos from = surface(level, 26, 24);
            var bolt = io.github.opencubicchunks.cubicchunks.world.level.CubicThunder.strikeNear(level, from);
            return "rod at " + rodPos.toShortString() + ", strike from " + from.toShortString() + " landed at "
                    + (bolt == null ? "nothing" : bolt.blockPosition().toShortString());
        });
        LOGGER.info("[cc-gametest] thunder rod: {}", rod);
        context.waitTicks(10);
        context.takeScreenshot("cc-thunder");
        world.getServer().runCommand("weather clear");
        world.getServer().runCommand("gamemode spectator @a");
    }

    /**
     * Scheduled ticks in cubes at Y 1000: water spreading (fluid ticks), sand falling (block ticks), and a tick that waits while its cube is
     * unloaded and saved: a lit redstone lamp with nothing powering it, given a tick 200 ticks off that switches it off.
     */
    private static void scheduledTicks(ClientGameTestContext context, TestSingleplayerContext world) {
        int x = -60;
        int y = 1000;
        int z = -60;
        BlockPos lamp = new BlockPos(x - 3, y, z - 3);
        world.getServer().runCommand("tp @a " + x + " " + (y + 10) + " " + z + " 0 90");
        context.waitTicks(LOAD_TICKS);
        world.getServer().runCommand(fill(x - 6, y - 1, z - 6, x + 6, y - 1, z + 6, "stone"));
        // lit, so the source can't freeze this high up before it spreads (water freezes under block light 10)
        world.getServer().runCommand("setblock " + x + " " + (y + 3) + " " + z + " minecraft:glowstone");
        world.getServer().runCommand("setblock " + x + " " + y + " " + z + " minecraft:water");
        world.getServer().runCommand("setblock " + (x + 3) + " " + (y + 4) + " " + (z + 3) + " minecraft:sand");
        world.getServer().runCommand("setblock " + lamp.getX() + " " + lamp.getY() + " " + lamp.getZ() + " minecraft:redstone_lamp[lit=true]");
        world.getServer().runOnServer(server -> server.overworld().scheduleTick(lamp, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP, 200));
        context.waitTicks(60);
        String first = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            int water = 0;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (level.getFluidState(new BlockPos(x + dx, y, z + dz)).is(net.minecraft.tags.FluidTags.WATER)) water++;
                }
            }
            return "water on " + water + " blocks (source now " + level.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString() + "), sand at the floor " + level.getBlockState(new BlockPos(x + 3, y, z + 3)).is(net.minecraft.world.level.block.Blocks.SAND)
                    + ", lamp lit " + level.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)
                    + ", ticks pending " + level.getBlockTicks().count() + " block / " + level.getFluidTicks().count() + " fluid";
        });
        LOGGER.info("[cc-gametest] scheduled: {}", first);

        world.getServer().runCommand("tp @a 0 -290 0");
        context.waitTicks(300);
        String away = world.getServer().computeOnServer(server -> {
            var cube = ((io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader) server.overworld()).cc_getCube(
                    io.github.opencubicchunks.cc_core.utils.Coords.blockToCube(lamp.getX()), io.github.opencubicchunks.cc_core.utils.Coords.blockToCube(lamp.getY()),
                    io.github.opencubicchunks.cc_core.utils.Coords.blockToCube(lamp.getZ()), net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            return "lamp's cube loaded while away: " + (cube != null);
        });
        LOGGER.info("[cc-gametest] scheduled: {}", away);
        world.getServer().runCommand("tp @a " + x + " " + (y + 10) + " " + z + " 0 90");
        context.waitTicks(LOAD_TICKS + 100);
        String back = world.getServer().computeOnServer(server -> "back: lamp lit " + server.overworld().getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT));
        LOGGER.info("[cc-gametest] scheduled: {}", back);
        context.takeScreenshot("cc-scheduled");
    }

    /**
     * Back on the old surface after the trips up and down: no block that stops light may hold full sky light (vanilla never stores 15 in
     * one; bad sky sources once wrote it into the bottom row of cubes loaded back). Counts them on the server around the player.
     */
    private static void lightAudit(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0 60 0");
        context.waitTicks(LOAD_TICKS);
        LOGGER.info("[cc-gametest] light audit: {}", auditSky(world));
        context.waitTicks(400);
        LOGGER.info("[cc-gametest] light audit 400 ticks later: {}", auditSky(world));
    }

    private static String auditSky(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            var sky = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY);
            java.util.List<String> examples = new java.util.ArrayList<>();
            int badWithData = 0;
            int badNoData = 0;
            int checked = 0;
            int bad = 0;
            java.util.Map<Integer, Integer> badByY = new java.util.TreeMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = -96; x <= 96; x++) {
                for (int z = -96; z <= 96; z++) {
                    for (int y = -64; y <= 64; y++) {
                        var state = level.getBlockState(pos.set(x, y, z));
                        if (state.getLightDampening() < 15) {
                            continue;
                        }
                        checked++;
                        if (level.getBrightness(LightLayer.SKY, pos) == 15) {
                            bad++;
                            badByY.merge(y, 1, Integer::sum);
                            boolean hasData = sky.getDataLayerData(SectionPos.of(pos)) != null;
                            if (hasData) badWithData++; else badNoData++;
                            if (examples.size() < 6 && y == 0) {
                                StringBuilder e = new StringBuilder(pos.toShortString() + " sections");
                                for (int sy = -3; sy <= 2; sy++) {
                                    e.append(" ").append(sy).append(sky.getDataLayerData(SectionPos.of(x >> 4, sy, z >> 4)) != null ? "+" : "-");
                                }
                                var light = ((io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource) level.getChunkSource()).cc_cubicLight();
                                e.append(" ").append(light == null ? "" : light.describeSky(x, z));
                                examples.add(e.toString());
                            }
                        }
                    }
                }
            }
            return bad + " light-stopping blocks with sky 15 of " + checked + " checked (" + badWithData + " in sections with sky data, " + badNoData
                    + " without), by Y " + badByY + "; at Y 0: " + examples;
        });
    }

    /**
     * Regional difficulty reads the inhabited time of the cube at a place: it grows while a player is near, and is still there after the
     * cube was unloaded and saved while the player was away.
     */
    private static void regionalDifficulty(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockPos spot = new BlockPos(0, 60, 0);
        world.getServer().runCommand("difficulty normal");
        // inhabited time, like spawning, counts only players who aren't spectators
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
        world.getServer().runCommand("tp @a 0 60 0");
        context.waitTicks(LOAD_TICKS);
        LOGGER.info("[cc-gametest] difficulty: before leaving {}", difficultyAt(world, spot));
        world.getServer().runCommand("tp @a 0 1010 0");
        world.getServer().runCommand("setblock 0 1009 0 minecraft:stone"); // to stand on while away
        context.waitTicks(300);
        String loaded = world.getServer().computeOnServer(server ->
                ((io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache) server.overworld().getChunkSource())
                        .cc_getFullCubeNow(io.github.opencubicchunks.cc_core.api.CubePos.from(spot)) != null ? "yes" : "no");
        LOGGER.info("[cc-gametest] difficulty: away, cube loaded {}", loaded);
        world.getServer().runCommand("tp @a 0 60 0");
        context.waitTicks(LOAD_TICKS);
        LOGGER.info("[cc-gametest] difficulty: back {}", difficultyAt(world, spot));
        // a cube lived in for 50 hours (vanilla's cap for inhabited time): Normal's local difficulty goes from 1.5 to 3.0
        world.getServer().runOnServer(server -> ((io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache) server.overworld().getChunkSource())
                .cc_getFullCubeNow(io.github.opencubicchunks.cc_core.api.CubePos.from(spot)).setInhabitedTime(3_600_000L));
        LOGGER.info("[cc-gametest] difficulty: lived in {}", difficultyAt(world, spot));
        world.getServer().runCommand("gamemode spectator @a");
    }

    private static String difficultyAt(TestSingleplayerContext world, BlockPos pos) {
        return world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            var difficulty = level.getCurrentDifficultyAt(pos);
            return "inhabited " + io.github.opencubicchunks.cubicchunks.server.level.CubicInhabitedTime.at(level, pos) + " ticks, local difficulty "
                    + String.format(java.util.Locale.ROOT, "%.3f", difficulty.getEffectiveDifficulty()) + " (game time " + level.getGameTime() + ")";
        });
    }

    /**
     * Entities tick on the client where it holds their cube (before, a cubic client held no columns and ticked no entity but the player):
     * a pig on a platform at Y 1000 ticks on the client while the player is up there, is not sent while the player is on the ground below,
     * and ticks again once the player is back. Client entities count ticks only when ticked.
     */
    private static void clientEntities(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0 1010 0");
        context.waitTicks(LOAD_TICKS);
        world.getServer().runCommand("fill 20 999 20 26 999 26 minecraft:stone");
        world.getServer().runCommand("summon minecraft:pig 23 1000 23 {NoAI:1b,PersistenceRequired:1b}");
        context.waitTicks(LOAD_TICKS / 2);
        LOGGER.info("[cc-gametest] client entities: up {}", pigOnClient(context));
        world.getServer().runCommand("tp @a 23 40 23");
        context.waitTicks(LOAD_TICKS);
        LOGGER.info("[cc-gametest] client entities: on the ground {}", pigOnClient(context));
        world.getServer().runCommand("tp @a 23 1010 23");
        context.waitTicks(LOAD_TICKS);
        LOGGER.info("[cc-gametest] client entities: up again {}", pigOnClient(context));
    }

    private static String pigOnClient(ClientGameTestContext context) {
        int[] before = context.computeOnClient(mc -> {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity.getType() == net.minecraft.world.entity.EntityTypes.PIG && entity.getY() > 990) {
                    return new int[] { entity.getId(), entity.tickCount };
                }
            }
            return null;
        });
        if (before == null) {
            return "pig not on the client; " + context.computeOnClient(mc -> mc.level.gatherChunkSourceStats());
        }
        context.waitTicks(20);
        return context.computeOnClient(mc -> {
            var pig = mc.level.getEntity(before[0]);
            if (pig == null) {
                return "pig gone from the client";
            }
            var cubes = (io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource) mc.level.getChunkSource();
            var cubePos = io.github.opencubicchunks.cc_core.api.CubePos.from(pig.blockPosition());
            boolean held = cubes.cc_getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null;
            return "pig at " + pig.blockPosition().toShortString() + ", its cube held " + held + ", ticked " + (pig.tickCount - before[1]) + " times in 20 ticks; "
                    + mc.level.gatherChunkSourceStats();
        });
    }

    /**
     * Biome colours cached on the client are dropped where an arriving cube can change them: a marker put in the cache stays at a spot
     * far above the cube and three columns away, and gives way to the real colour in the cube.
     */
    private static void tintCaches(ClientGameTestContext context) {
        String report = context.computeOnClient(mc -> {
            try {
                var level = mc.level;
                var resolver = net.minecraft.client.renderer.BiomeColors.GRASS_COLOR_RESOLVER;
                BlockPos inCube = new BlockPos(8, 40, 8);
                BlockPos farAbove = new BlockPos(8, 40 + 200, 8);
                BlockPos aside = new BlockPos(8 + 48, 40, 8);
                int marker = 0x123456;
                StringBuilder out = new StringBuilder();
                for (BlockPos pos : new BlockPos[] { inCube, farAbove, aside }) {
                    level.getBlockTint(pos, resolver);
                    putInTintCache(level, resolver, pos, marker);
                    out.append(pos.toShortString()).append(" cached ").append(level.getBlockTint(pos, resolver) == marker ? "marker" : "?").append("; ");
                }
                ((io.github.opencubicchunks.cubicchunks.client.multiplayer.CubicClientLevel) level).cc_onCubeLoaded(
                        io.github.opencubicchunks.cc_core.api.CubePos.from(inCube));
                out.append("after the cube arrives:");
                for (BlockPos pos : new BlockPos[] { inCube, farAbove, aside }) {
                    int color = level.getBlockTint(pos, resolver);
                    out.append(" ").append(pos.toShortString()).append(color == marker ? " marker" : String.format(" #%06x", color & 0xFFFFFF));
                }
                return out.toString();
            } catch (ReflectiveOperationException e) {
                return "reflection failed: " + e;
            }
        });
        LOGGER.info("[cc-gametest] tint: {}", report);
    }

    /** Test only: writes a colour straight into the client's tint cache layer for pos. */
    private static void putInTintCache(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.level.ColorResolver resolver, BlockPos pos,
            int color) throws ReflectiveOperationException {
        var cachesField = net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredField("tintCaches");
        cachesField.setAccessible(true);
        Object tintCache = ((java.util.Map<?, ?>) cachesField.get(level)).get(resolver);
        var columnsField = net.minecraft.client.color.block.BlockTintCache.class.getDeclaredField("cache");
        columnsField.setAccessible(true);
        Object column = ((it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) columnsField.get(tintCache))
                .get(net.minecraft.world.level.ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        var getLayer = column.getClass().getDeclaredMethod("getLayer", int.class);
        getLayer.setAccessible(true);
        int[] layer = (int[]) getLayer.invoke(column, pos.getY());
        layer[(pos.getZ() & 15) << 4 | (pos.getX() & 15)] = color;
    }

    /**
     * The client's light is the server's: compared block by block around the player in the cubes the client holds, after arriving, after a
     * shaft is dug with glowstone at the bottom (the client works that out too), and after a roof appears high above (which the client may
     * not hold: it has only the server's word for the sky going dark below).
     */
    private static void lightSync(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @a 0 40 0");
        context.waitTicks(LOAD_TICKS);
        LOGGER.info("[cc-gametest] light sync: arrived {}", compareLight(context, world, new BlockPos(0, 10, 0)));
        world.getServer().runCommand("fill 5 -12 5 9 30 9 minecraft:air");
        world.getServer().runCommand("setblock 7 -12 7 minecraft:glowstone");
        context.waitTicks(60);
        LOGGER.info("[cc-gametest] light sync: shaft {}", compareLight(context, world, new BlockPos(7, 0, 7)));
        // a column no other stage builds over (their platforms at Y 1000 would roof it already)
        BlockPos under = new BlockPos(-32, 30, 32);
        int skyBefore = world.getServer().computeOnServer(server -> server.overworld().getBrightness(LightLayer.SKY, under));
        world.getServer().runCommand("fill -40 200 24 -24 200 40 minecraft:stone");
        context.waitTicks(100);
        int skyAfter = world.getServer().computeOnServer(server -> server.overworld().getBrightness(LightLayer.SKY, under));
        String clientSide = context.computeOnClient(mc -> "client sky " + mc.level.getBrightness(LightLayer.SKY, under) + ", client holds the roof's cube "
                + (((io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource) mc.level.getChunkSource()).cc_getCube(-2, 6, 1,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null));
        LOGGER.info("[cc-gametest] light sync: sky under the roof (-32 30 32) on the server {} before the roof, {} after; {}", skyBefore, skyAfter, clientSide);
        LOGGER.info("[cc-gametest] light sync: roof at Y 200 {}", compareLight(context, world, new BlockPos(-32, 10, 32)));
    }

    private static String compareLight(ClientGameTestContext context, TestSingleplayerContext world, BlockPos center) {
        int r = 24;
        int size = 2 * r + 1;
        byte[][] server = world.getServer().computeOnServer(s -> readLight(s.overworld(), center, r));
        byte[][] client = context.computeOnClient(mc -> readLight(mc.level, center, r));
        int compared = 0;
        int skyOff = 0;
        int blockOff = 0;
        java.util.List<String> examples = new java.util.ArrayList<>();
        for (int i = 0; i < server[0].length; i++) {
            if (client[0][i] < 0) {
                continue; // the client does not hold that cube
            }
            compared++;
            boolean sky = server[0][i] != client[0][i];
            boolean block = server[1][i] != client[1][i];
            if (sky) skyOff++;
            if (block) blockOff++;
            if ((sky || block) && examples.size() < 5) {
                int dx = i % size - r;
                int dz = (i / size) % size - r;
                int dy = i / (size * size) - r;
                examples.add(center.offset(dx, dy, dz).toShortString() + " sky " + server[0][i] + "/" + client[0][i] + " block " + server[1][i] + "/" + client[1][i]);
            }
        }
        return compared + " blocks compared, sky differs at " + skyOff + ", block light at " + blockOff + (examples.isEmpty() ? "" : " (server/client: " + examples + ")");
    }

    /** Sky and block light around center, -1 where the level holds no cube. */
    private static byte[][] readLight(net.minecraft.world.level.Level level, BlockPos center, int r) {
        int size = 2 * r + 1;
        byte[][] out = new byte[2][size * size * size];
        var cubes = (io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource) level.getChunkSource();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++, i++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    var cubePos = io.github.opencubicchunks.cc_core.api.CubePos.from(pos);
                    if (cubes.cc_getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) {
                        out[0][i] = -1;
                        out[1][i] = -1;
                        continue;
                    }
                    out[0][i] = (byte) level.getBrightness(LightLayer.SKY, pos);
                    out[1][i] = (byte) level.getBrightness(LightLayer.BLOCK, pos);
                }
            }
        }
        return out;
    }

    /** The air block above the highest block of x, z below Y 200. */
    private static BlockPos surface(net.minecraft.server.level.ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 200, z);
        while (level.getBlockState(pos).isAir() && pos.getY() > -200) {
            pos.move(0, -1, 0);
        }
        return pos.above();
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
    }

    /** The loading screen's map over the loaded world: a status view from the integrated server, focused where the player is. */
    private static void loadingScreen(ClientGameTestContext context) {
        context.waitTicks(LOAD_TICKS);
        context.setScreen(() -> {
            Minecraft mc = Minecraft.getInstance();
            int radius = Math.max(5, 3) + ChunkLevel.RADIUS_AROUND_FULL_CHUNK + 1; // as Minecraft.doWorldLoad
            ChunkLoadStatusView view = mc.getSingleplayerServer().createChunkLoadStatusView(radius);
            view.moveTo(mc.level.dimension(), mc.player.chunkPosition());
            LevelLoadTracker tracker = new LevelLoadTracker();
            tracker.setServerChunkStatusView(view);
            LOGGER.info("[cc-gametest] loading screen view: {}", view.getClass().getSimpleName());
            return new LevelLoadingScreen(tracker, LevelLoadingScreen.Reason.OTHER);
        });
        context.waitTicks(5);
        context.takeScreenshot("cc-loading-screen");
        context.setScreen(() -> null);
    }

    private static void visit(ClientGameTestContext context, TestSingleplayerContext world, String name, int x, int y, int z, String[] build) {
        world.getServer().runCommand("tp @a " + x + " " + y + " " + z + " 0 50");
        context.waitTicks(LOAD_TICKS);
        if (build != null) {
            for (String command : build) {
                world.getServer().runCommand(command);
            }
            context.waitTicks(LOAD_TICKS / 2);
        }
        BlockPos below = new BlockPos(x, y - 10, z);
        String stats = context.computeOnClient(mc -> stats(mc, below));
        LOGGER.info("[cc-gametest] {}: {}", name, stats);
        context.takeScreenshot("cc-" + name);
    }

    private static String light(Minecraft mc, BlockPos pos) {
        var engine = mc.level.getLightEngine();
        SectionPos section = SectionPos.of(pos);
        return "light at " + pos.toShortString() + ": sky " + mc.level.getBrightness(LightLayer.SKY, pos) + " (data " + (engine.getLayerListener(LightLayer.SKY)
                .getDataLayerData(section) != null) + "), block " + mc.level.getBrightness(LightLayer.BLOCK, pos) + " (data "
                + (engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(section) != null) + "), raw " + mc.level.getMaxLocalRawBrightness(pos);
    }

    private static String stats(Minecraft mc, BlockPos below) {
        int cubes = ((CubeSource) mc.level.getChunkSource()).cc_getLoadedCubeCount();
        int visible = 0;
        int compiled = 0;
        for (SectionRenderDispatcher.RenderSection section : mc.levelRenderer.visibleSections()) {
            visible++;
            if (section.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED) {
                compiled++;
            }
        }
        int pigs = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity.getType() == net.minecraft.world.entity.EntityTypes.PIG) {
                pigs++;
            }
        }
        return "player at " + mc.player.blockPosition() + ", " + cubes + " cubes loaded, " + pigs + " pigs seen, " + visible + " visible sections (" + compiled
                + " compiled), section 10 below the player compiled and visible: " + mc.levelRenderer.isSectionCompiledAndVisible(below, 0)
                + ", block 10 below: " + mc.level.getBlockState(below) + ", " + light(mc, below.above()) + "; at the player: " + light(mc, mc.player.blockPosition());
    }
}
