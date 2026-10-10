package io.github.opencubicchunks.cubicchunks.config;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;

/** A world's own Cubic Chunks settings, kept in its folder (cubicchunks/world.toml) from when it was made: see {@link CubicWorldSettings}. */
public final class WorldConfig extends BaseConfig {
    private static final String FILE_PATH = "cubicchunks/world.toml";

    private static final String KEY_CUBIC = "cubic";
    private static final String KEY_MIN_Y = "minY";
    private static final String KEY_MAX_Y = "maxY";
    private static final String KEY_DIMENSIONS = "dimensions";

    private WorldConfig() {
    }

    private static File file(Path worldFolder) {
        return worldFolder.resolve(FILE_PATH).toFile();
    }

    private static CommentedConfig create(CubicWorldSettings settings) {
        Config.setInsertionOrderPreserved(true);
        var config = CommentedConfig.inMemory();
        config.set(KEY_CUBIC, settings.cubic());
        config.setComment(KEY_CUBIC, """
                 Whether this world uses Cubic Chunks, fixed when the world was made: changing it would leave the world's saved chunks unreadable.\
                """);
        config.set(KEY_MIN_Y, settings.minY());
        config.set(KEY_MAX_Y, settings.maxY());
        config.setComment(KEY_MIN_Y, """
                 The lowest and highest Y blocks can be placed at in a cubic world. Raising the top or lowering the bottom is safe (within the
                 game's heightLimit config); narrowing them leaves what was built beyond them out of reach.\
                """);
        config.set(KEY_DIMENSIONS, new java.util.ArrayList<>(settings.dimensions()));
        config.setComment(KEY_DIMENSIONS, """
                 The dimensions that are cubic (the rest are vanilla's chunks, as made by their own generators). Fixed like the rest: a
                 dimension switched over would leave what it saved unreadable.\
                """);
        return config;
    }

    /** The settings saved in this world's folder, if any. */
    public static Optional<CubicWorldSettings> read(Path worldFolder) {
        File file = file(worldFolder);
        if (!file.exists()) {
            return Optional.empty();
        }
        var config = create(CubicWorldSettings.VANILLA);
        read(file, config);
        java.util.List<String> dimensions = config.get(KEY_DIMENSIONS);
        return Optional.of(new CubicWorldSettings(config.get(KEY_CUBIC), config.getInt(KEY_MIN_Y), config.getInt(KEY_MAX_Y),
                dimensions == null ? CubicWorldSettings.NEW_WORLD_DIMENSIONS : dimensions));
    }

    /** Whether the world's saved settings say which dimensions are cubic (worlds saved before they did made them all cubic). */
    public static boolean hasDimensions(Path worldFolder) {
        File file = file(worldFolder);
        if (!file.exists()) {
            return false;
        }
        var config = CommentedConfig.inMemory();
        read(file, config);
        return config.contains(KEY_DIMENSIONS);
    }

    public static void write(Path worldFolder, CubicWorldSettings settings) {
        File file = file(worldFolder);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        write(file, create(settings));
    }
}
