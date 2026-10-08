package io.github.opencubicchunks.cubicchunks.client.renderer;

import java.util.function.Supplier;

/**
 * 26.3 keeps render sections in a {@link net.minecraft.client.RotatingSectionStorage}, which wraps X and Z around the camera but holds a
 * fixed band of the world's section heights. In a cubic level that band would be the dimension's old height, so the storage is built
 * cubic instead: Y wraps around the camera too, with the same radius. Nothing passed to ViewArea says which level it is for, so its builder
 * (LevelRenderer) marks the construction here.
 */
public final class CubicRenderSections {
    private static final ThreadLocal<Boolean> BUILDING_CUBIC = ThreadLocal.withInitial(() -> false);

    private CubicRenderSections() {}

    public static <T> T build(boolean cubic, Supplier<T> constructor) {
        boolean previous = BUILDING_CUBIC.get();
        BUILDING_CUBIC.set(cubic);
        try {
            return constructor.get();
        } finally {
            BUILDING_CUBIC.set(previous);
        }
    }

    public static boolean buildingCubic() {
        return BUILDING_CUBIC.get();
    }

    /** A {@link net.minecraft.client.RotatingSectionStorage} that may wrap Y as well. */
    public interface Storage {
        void cc_makeCubic();

        boolean cc_isCubic();
    }

    /** A {@link net.minecraft.client.renderer.ViewArea} that knows whether it is cubic. */
    public interface Area {
        boolean cc_isCubic();
    }
}
