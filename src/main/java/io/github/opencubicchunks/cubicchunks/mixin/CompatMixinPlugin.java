package io.github.opencubicchunks.cubicchunks.mixin;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The compatibility mixins for another mod apply only when that mod is installed, as they target its classes: those in mixin/sodium with
 * Sodium, those in mixin/voxy with Voxy.
 */
public class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    private static final boolean VOXY = FabricLoader.getInstance().isModLoaded("voxy");

    @Override public void onLoad(String mixinPackage) {
    }

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mixin.voxy.")) {
            return VOXY;
        }
        return mixinClassName.contains(".mixin.sodium.") && SODIUM;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override public List<String> getMixins() {
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
