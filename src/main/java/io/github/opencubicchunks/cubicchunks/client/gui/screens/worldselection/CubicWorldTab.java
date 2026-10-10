package io.github.opencubicchunks.cubicchunks.client.gui.screens.worldselection;

import io.github.opencubicchunks.cubicchunks.world.CubicWorldSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

/**
 * The world creation screen's Cubic Chunks tab: whether the new world is cubic, and the lowest and highest Y it holds (within what the game's
 * heightLimit config allows, which the tab says). The choices are saved with the world when it is made (see CubicWorldSettings).
 */
public class CubicWorldTab extends GridLayoutTab {
    private static final Component TITLE = Component.translatable("createWorld.tab.cubicchunks.title");
    private static final Component CUBIC = Component.translatable("selectWorld.cubicChunks");
    private static final Component CUBIC_INFO = Component.translatable("selectWorld.cubicChunks.info");
    private static final Component MIN_Y = Component.translatable("selectWorld.cubicChunks.minY");
    private static final Component MAX_Y = Component.translatable("selectWorld.cubicChunks.maxY");
    private static final int WIDTH = 310;
    private static final int HALF_WIDTH = 150;

    public CubicWorldTab(WorldCreationUiState uiState) {
        super(TITLE);
        CubicWorldCreation choices = (CubicWorldCreation) uiState;
        Font font = Minecraft.getInstance().font;
        GridLayout.RowHelper helper = this.layout.columnSpacing(10).rowSpacing(8).createRowHelper(2);

        CycleButton<Boolean> cubicButton = helper.addChild(CycleButton.onOffBuilder(choices.cc_isCubic())
                .withTooltip(value -> Tooltip.create(CUBIC_INFO))
                .create(0, 0, WIDTH, 20, CUBIC, (button, value) -> choices.cc_setCubic(value)), 2);

        EditBox minY = heightBox(font, MIN_Y, choices.cc_getMinY(), choices::cc_setMinY);
        EditBox maxY = heightBox(font, MAX_Y, choices.cc_getMaxY(), choices::cc_setMaxY);
        helper.addChild(CommonLayouts.labeledElement(font, minY, MIN_Y));
        helper.addChild(CommonLayouts.labeledElement(font, maxY, MAX_Y));

        helper.addChild(new MultiLineTextWidget(Component.translatable("selectWorld.cubicChunks.range",
                CubicWorldSettings.lowestY(), CubicWorldSettings.highestY(), String.format(java.util.Locale.ROOT, "%,d",
                io.github.opencubicchunks.cubicchunks.world.level.CubicHeight.borderLimit())), font).setMaxWidth(WIDTH), 2);

        uiState.addListener(state -> {
            boolean cubic = choices.cc_isCubic();
            cubicButton.setValue(cubic);
            minY.active = cubic;
            minY.setEditable(cubic);
            maxY.active = cubic;
            maxY.setEditable(cubic);
        });
    }

    /** A box for a whole number of blocks, which hands on each value it can read (the heights are clamped when the world is made). */
    private static EditBox heightBox(Font font, Component label, int value, java.util.function.IntConsumer onChange) {
        EditBox box = new EditBox(font, HALF_WIDTH - 2, 20, label);
        box.setMaxLength(7);
        box.setValue(Integer.toString(value));
        box.setResponder(text -> {
            try {
                onChange.accept(Integer.parseInt(text));
            } catch (NumberFormatException e) {
                // a lone "-" or nothing yet
            }
        });
        return box;
    }
}
