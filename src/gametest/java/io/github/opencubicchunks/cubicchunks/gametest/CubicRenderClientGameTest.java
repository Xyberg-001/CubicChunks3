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

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            boolean cubic = context.computeOnClient(mc -> ((CanBeCubic) mc.level).cc_isCubic());
            LOGGER.info("[cc-gametest] client level cubic: {}", cubic);
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("time set noon");

            loadingScreen(context);
            night(context, world);
            visit(context, world, "ground", 0, 40, 0, null);
            visit(context, world, "y1000", 0, 1010, 0,
                    new String[] { "fill -10 1000 -10 10 1000 10 minecraft:diamond_block", "summon minecraft:pig 3 1001 3" });
            visit(context, world, "y-300", 0, -290, 0,
                    new String[] { "fill -10 -299 -10 10 -280 10 minecraft:air", "fill -10 -300 -10 10 -300 10 minecraft:emerald_block",
                            "setblock 0 -295 3 minecraft:glowstone" });
            randomTicks(context, world, "ticks-y1000", 1000, true);
            randomTicks(context, world, "ticks-y-300", -300, false);
        }
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
