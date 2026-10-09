package io.github.opencubicchunks.cubicchunks.config;

import java.io.File;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.fabricmc.loader.api.FabricLoader;

public class CommonConfig extends BaseConfig {
    private static final String FILE_NAME = "cubicchunks_common.toml";
    // TODO forge/fabric-agnostic method for getting config directory
    // Note that this relies on IS_IN_TEST being set before this class is classloaded
    private static final File FILE_PATH = CubicChunks.IS_IN_TEST ? null : new File(FabricLoader.getInstance().getConfigDir().toFile(), FILE_NAME);

    private static final String KEY_GENERAL = "general";
    private static final String KEY_VERTICAL_VIEW_DISTANCE = KEY_GENERAL + ".verticalViewDistance";
    private static final int DEFAULT_VERTICAL_VIEW_DISTANCE = 8;
    private static final String KEY_HEIGHT_LIMIT = KEY_GENERAL + ".heightLimit";
    private static final String KEY_GENERATE_NEW_WORLDS_AS_CC = KEY_GENERAL + ".generateNewWorldsAsCC";
    private static final String KEY_NEW_WORLD_MIN_Y = KEY_GENERAL + ".newWorldMinY";
    private static final String KEY_NEW_WORLD_MAX_Y = KEY_GENERAL + ".newWorldMaxY";
    private static final String KEY_DISTANT_HORIZONS_MIN_Y = KEY_GENERAL + ".distantHorizonsMinY";
    /** How tall a world Distant Horizons can hold (its data points keep a height in 12 bits). */
    public static final int DISTANT_HORIZONS_HEIGHT = 4096;

    /** The height limits the game can run with (see {@link #getHeightLimit}). */
    public static final int HEIGHT_LIMIT_NORMAL = 8192;
    public static final int HEIGHT_LIMIT_TALL = 32768;
    /** A new cubic world's heights unless chosen otherwise: within the normal limit, less the few hundred blocks it keeps free at each end. */
    public static final int DEFAULT_NEW_WORLD_MIN_Y = -7500;
    public static final int DEFAULT_NEW_WORLD_MAX_Y = 7500;

    private final CommentedConfig config;

    private CommonConfig(CommentedConfig config) {
        this.config = config;
    }

    private static CommentedConfig createDefaultConfig() {
        Config.setInsertionOrderPreserved(true);
        var config = CommentedConfig.inMemory();
        config.set(KEY_VERTICAL_VIEW_DISTANCE, DEFAULT_VERTICAL_VIEW_DISTANCE);
        // TODO more detailed config comment?
        config.setComment(KEY_VERTICAL_VIEW_DISTANCE, """
                 The vertical view distance for players in Cubic Chunks dimensions (similar to vanilla render distance for the horizontal axes).\
                """);
        config.set(KEY_HEIGHT_LIMIT, HEIGHT_LIMIT_NORMAL);
        config.setComment(KEY_HEIGHT_LIMIT, """
                 How high and deep any cubic world can reach, read when the game starts (a change needs a restart). Block positions are packed into
                 64 bits everywhere, so height and horizontal reach trade off:
                     8192  - cubic worlds can hold up to about Y -7900 to 7900, and x and z reach about 16.7 million blocks from the centre.
                     32768 - cubic worlds can hold up to about Y -32400 to 32400, and x and z reach about 8.4 million blocks from the centre.
                 (A few hundred blocks at each end are kept free: cubes just beyond a world's heights load too.) A server and the players joining it
                 must use the same value; a world made for 32768 needs it to load.\
                """);
        config.set(KEY_GENERATE_NEW_WORLDS_AS_CC, false);
        config.setComment(KEY_GENERATE_NEW_WORLDS_AS_CC, """
                 Whether a new world uses Cubic Chunks: on a server, the world it makes when there is none; on the client, the default of the
                 switch in the world creation screen's Cubic Chunks tab. Each world keeps the choice it was made with (cubicchunks/world.toml in its
                 folder), so changing this never changes an existing world.\
                """);
        config.set(KEY_NEW_WORLD_MIN_Y, DEFAULT_NEW_WORLD_MIN_Y);
        config.set(KEY_NEW_WORLD_MAX_Y, DEFAULT_NEW_WORLD_MAX_Y);
        config.setComment(KEY_NEW_WORLD_MIN_Y, """
                 The lowest and highest Y a new cubic world holds (within heightLimit): the default for the world creation screen, and what a
                 server's new world gets.\
                """);
        config.set(KEY_DISTANT_HORIZONS_MIN_Y, -2048);
        config.setComment(KEY_DISTANT_HORIZONS_MIN_Y, """
                 With Distant Horizons installed: the lowest Y of the 4096 blocks of a cubic world it shows (it can hold no more than 4096 blocks
                 of height). The default shows Y -2048 to 2047.\
                """);
        return config;
    }

    // TODO save on game exit, etc, instead of every time config is marked dirty
    public void markDirty() {
        write(FILE_PATH, config);
    }

    // TODO do we want config values in fields on this class, instead of doing a get() each time?
    public int getVerticalViewDistance() {
        return config.getInt(KEY_VERTICAL_VIEW_DISTANCE);
    }

    /** {@link #HEIGHT_LIMIT_NORMAL} or {@link #HEIGHT_LIMIT_TALL}: any other value is read as the normal limit. */
    public int getHeightLimit() {
        return config.getInt(KEY_HEIGHT_LIMIT) == HEIGHT_LIMIT_TALL ? HEIGHT_LIMIT_TALL : HEIGHT_LIMIT_NORMAL;
    }

    public boolean shouldGenerateNewWorldsAsCC() {
        return config.get(KEY_GENERATE_NEW_WORLDS_AS_CC);
    }

    public int getNewWorldMinY() {
        return config.getInt(KEY_NEW_WORLD_MIN_Y);
    }

    public int getNewWorldMaxY() {
        return config.getInt(KEY_NEW_WORLD_MAX_Y);
    }

    public int getDistantHorizonsMinY() {
        return config.getInt(KEY_DISTANT_HORIZONS_MIN_Y);
    }

    public void setVerticalViewDistance(int verticalViewDistance) {
        config.set(KEY_VERTICAL_VIEW_DISTANCE, verticalViewDistance);
    }

    public void setGenerateNewWorldsAsCC(boolean generateNewWorldsAsCC) {
        config.set(KEY_GENERATE_NEW_WORLDS_AS_CC, generateNewWorldsAsCC);
    }

    public static CommonConfig getConfig() {
        var config = createDefaultConfig();
        if (CubicChunks.IS_IN_TEST) {
            // Skip file access when running in a test environment; tests should manually update relevant config values before running game code.
            return new CommonConfig(config);
        }
        // Read existing values to the config
        if (FILE_PATH.exists()) {
            read(FILE_PATH, config);
        }
        var commonConfig = new CommonConfig(config);
        // Write the config again even if we loaded an existing file, in case any keys were missing or invalid
        write(FILE_PATH, config);
        return commonConfig;
    }
}
