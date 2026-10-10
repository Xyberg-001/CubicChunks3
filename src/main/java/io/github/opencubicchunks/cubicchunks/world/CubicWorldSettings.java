package io.github.opencubicchunks.cubicchunks.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.CommonConfig;
import io.github.opencubicchunks.cubicchunks.config.WorldConfig;
import io.github.opencubicchunks.cubicchunks.server.level.CubeYRange;
import io.github.opencubicchunks.cubicchunks.world.level.CubicHeight;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * Whether a world uses cubic chunks, the heights its cubic levels hold, and which of its dimensions are cubic: the overworld (the
 * Nether and the End, and other mods' dimensions, stay as vanilla makes them: their generators make chunks, not cubes, and the End's
 * sea level at Y 0 is a cubic overworld's too). Chosen when the world is made (the world creation screen's Cubic Chunks tab, or the config for a server's new world) and saved in its folder (see WorldConfig), so
 * a world never changes with the config: a world made without cubic chunks stays vanilla with the mod installed.
 * <p>
 * The server decides as it creates its levels ({@link #decideForServer}); the client is told in the configuration phase of joining
 * (CCClientboundWorldSettingsPacket), before it makes its levels. Each level reads the settings of its side as it is constructed
 * (MixinLevel).
 */
public record CubicWorldSettings(boolean cubic, int minY, int maxY, List<String> dimensions) {
    /** The dimensions a new cubic world makes cubic. */
    public static final List<String> NEW_WORLD_DIMENSIONS = List.of(Level.OVERWORLD.identifier().toString());
    public static final CubicWorldSettings VANILLA = new CubicWorldSettings(false, 0, 0);

    public CubicWorldSettings {
        dimensions = List.copyOf(dimensions);
    }

    /** A new world's settings: only the overworld cubic. */
    public CubicWorldSettings(boolean cubic, int minY, int maxY) {
        this(cubic, minY, maxY, NEW_WORLD_DIMENSIONS);
    }

    /** Whether a level of this dimension is cubic in this world. */
    public boolean cubicIn(ResourceKey<Level> dimension) {
        return this.cubic && this.dimensions.contains(dimension.identifier().toString());
    }
    /** Cube worlds made before worlds kept their own settings get the config's default heights. */
    private static final CubicWorldSettings OLD_CUBIC = new CubicWorldSettings(true, CommonConfig.DEFAULT_NEW_WORLD_MIN_Y,
            CommonConfig.DEFAULT_NEW_WORLD_MAX_Y);

    private static volatile @Nullable CubicWorldSettings server;
    private static volatile @Nullable CubicWorldSettings client;
    /** What the world creation screen chose for the world it is about to make; taken by the server that starts next. */
    private static volatile @Nullable CubicWorldSettings pendingNewWorld;

    /** A new world's settings from the config. */
    public static CubicWorldSettings fromConfig() {
        CommonConfig config = CubicChunks.config();
        return new CubicWorldSettings(config.shouldGenerateNewWorldsAsCC(), config.getNewWorldMinY(), config.getNewWorldMaxY());
    }

    /**
     * The lowest Y a cubic world can be made to hold: a little above the lowest a block position holds, as a few cubes beyond a world's
     * heights load (see CubeYRange) and their blocks must fit in a block position too.
     */
    public static int lowestY() {
        return CubicHeight.minY() + Coords.cubeToMinBlock(CubeYRange.CUBES_BEYOND + 1);
    }

    /** The highest Y a cubic world can be made to hold (see {@link #lowestY}). */
    public static int highestY() {
        return CubicHeight.maxY() - Coords.cubeToMinBlock(CubeYRange.CUBES_BEYOND + 1);
    }

    /** Why this game can't run a world with these settings, or null if it can. */
    public @Nullable String problem() {
        if (!this.cubic) {
            return null;
        }
        if (this.minY >= this.maxY) {
            return "the world's lowest Y (" + this.minY + ") is not below its highest (" + this.maxY + ")";
        }
        if (this.minY < lowestY() || this.maxY > highestY()) {
            return "the world holds Y " + this.minY + " to " + this.maxY + ", beyond what this game can (Y " + lowestY() + " to "
                    + highestY() + "): set heightLimit = " + CommonConfig.HEIGHT_LIMIT_TALL + " in config/cubicchunks_common.toml and restart";
        }
        return null;
    }

    /** These settings with the heights brought within what the game can hold (and the lowest below the highest). */
    public CubicWorldSettings clamped() {
        int min = Math.clamp(this.minY, lowestY(), highestY() - 1);
        int max = Math.clamp(this.maxY, min + 1, highestY());
        return new CubicWorldSettings(this.cubic, min, max, this.dimensions);
    }

    /**
     * The settings of a level a side is about to make (a client level: the server it joined; a server level: its world's), or {@link #VANILLA}
     * for a dimension the world does not make cubic.
     */
    public static CubicWorldSettings forNewLevel(boolean clientSide, ResourceKey<Level> dimension) {
        CubicWorldSettings settings = clientSide ? client : server;
        return settings == null || !settings.cubicIn(dimension) ? VANILLA : settings;
    }

    /** The world the running server has loaded. */
    public static CubicWorldSettings server() {
        CubicWorldSettings settings = server;
        return settings == null ? VANILLA : settings;
    }

    /** The settings the server this client joined sent, or null if it sent none (yet). */
    public static @Nullable CubicWorldSettings client() {
        return client;
    }

    public static void setClient(@Nullable CubicWorldSettings settings) {
        client = settings;
    }

    public static void setPendingNewWorld(@Nullable CubicWorldSettings settings) {
        pendingNewWorld = settings;
    }

    /**
     * The settings of the world a server is starting: those saved with it; for a world from before worlds kept them, cubic if cubes were
     * saved, vanilla if chunks were; for a new world, what the world creation screen chose or else the config. Saved with the world if they
     * weren't already. Throws if this game can't hold the world (see {@link #problem}).
     */
    public static CubicWorldSettings decideForServer(LevelStorageSource.LevelStorageAccess storage) {
        Path worldFolder = storage.getLevelPath(LevelResource.ROOT);
        Path overworld = storage.getDimensionPath(Level.OVERWORLD);
        CubicWorldSettings pending = pendingNewWorld;
        pendingNewWorld = null;

        Optional<CubicWorldSettings> saved = WorldConfig.read(worldFolder);
        CubicWorldSettings settings;
        String source;
        if (saved.isPresent()) {
            settings = saved.get();
            source = "saved with the world";
        } else if (Files.isDirectory(overworld.resolve("region3d"))) {
            settings = OLD_CUBIC;
            source = "a cube world from before worlds kept their settings";
        } else if (hasFiles(overworld.resolve("region"))) {
            settings = VANILLA;
            source = "a world made without cubic chunks";
        } else if (pending != null) {
            settings = pending;
            source = "a new world, as chosen when it was created";
        } else {
            settings = fromConfig();
            source = "a new world, from the config";
        }
        if (settings.cubic && saved.isPresent() && !WorldConfig.hasDimensions(worldFolder)) {
            // saved before worlds said which of their dimensions are cubic (all were): the overworld, and any other that holds cubes
            List<String> dimensions = new java.util.ArrayList<>(NEW_WORLD_DIMENSIONS);
            for (ResourceKey<Level> other : List.of(Level.NETHER, Level.END)) {
                if (hasFiles(storage.getDimensionPath(other).resolve("region3d"))) {
                    dimensions.add(other.identifier().toString());
                }
            }
            settings = new CubicWorldSettings(settings.cubic, settings.minY, settings.maxY, dimensions);
            WorldConfig.write(worldFolder, settings);
        }
        String problem = settings.problem();
        if (problem != null) {
            throw new IllegalStateException("Cubic Chunks can't load this world: " + problem);
        }
        if (saved.isEmpty()) {
            WorldConfig.write(worldFolder, settings);
        }
        CubicChunks.LOGGER.info("World {}: {} ({})", storage.getLevelId(),
                settings.cubic ? "cubic, Y " + settings.minY + " to " + settings.maxY + " in " + settings.dimensions : "not cubic", source);
        server = settings;
        return settings;
    }

    public static void onServerStopped() {
        server = null;
    }

    private static boolean hasFiles(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.findAny().isPresent();
        } catch (IOException e) {
            return true; // can't tell: treat it as an existing world, which only keeps it vanilla
        }
    }
}
