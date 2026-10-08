package io.github.opencubicchunks.cubicchunks.mixin.core.client.worldselection;

import java.util.Arrays;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.client.gui.screens.worldselection.CubicWorldCreation;
import io.github.opencubicchunks.cubicchunks.client.gui.screens.worldselection.CubicWorldTab;
import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world creation screen gets a Cubic Chunks tab after vanilla's (see CubicWorldTab), and what it chose goes to the server that starts for
 * the new world, which saves it with the world (see CubicWorldSettings).
 */
@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen {
    @Shadow @Final private WorldCreationUiState uiState;

    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)"
                    + "Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;"))
    private MenuTabBar.Builder cc_addCubicChunksTab(MenuTabBar.Builder builder, Tab[] tabs, Operation<MenuTabBar.Builder> original) {
        Tab[] withCubic = Arrays.copyOf(tabs, tabs.length + 1);
        withCubic[tabs.length] = new CubicWorldTab(this.uiState);
        return original.call(builder, withCubic);
    }

    @Inject(method = "onCreate", at = @At("HEAD"))
    private void cc_rememberCubicChoice(CallbackInfo ci) {
        CubicWorldSettings.setPendingNewWorld(((CubicWorldCreation) this.uiState).cc_settings());
    }
}
