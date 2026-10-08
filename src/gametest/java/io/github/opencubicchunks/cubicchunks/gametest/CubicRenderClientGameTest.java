package io.github.opencubicchunks.cubicchunks.gametest;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            boolean cubic = context.computeOnClient(mc -> ((CanBeCubic) mc.level).cc_isCubic());
            LOGGER.info("[cc-gametest] client level cubic: {}", cubic);
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("time set noon");

            visit(context, world, "ground", 0, 40, 0, null);
            visit(context, world, "y1000", 0, 1010, 0,
                    new String[] { "fill -10 1000 -10 10 1000 10 minecraft:diamond_block" });
            visit(context, world, "y-300", 0, -290, 0,
                    new String[] { "fill -10 -299 -10 10 -280 10 minecraft:air", "fill -10 -300 -10 10 -300 10 minecraft:emerald_block",
                            "setblock 0 -295 3 minecraft:glowstone" });
        }
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
        return "player at " + mc.player.blockPosition() + ", " + cubes + " cubes loaded, " + visible + " visible sections (" + compiled
                + " compiled), section 10 below the player compiled and visible: " + mc.levelRenderer.isSectionCompiledAndVisible(below, 0)
                + ", block 10 below: " + mc.level.getBlockState(below) + ", " + light(mc, below.above()) + "; at the player: " + light(mc, mc.player.blockPosition());
    }
}
