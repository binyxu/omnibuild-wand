package com.buildingwand;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BuildingWandSettingsScreen extends Screen {

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 150;

    private int smartScanLimit;
    private int smartBlockLimit;

    public BuildingWandSettingsScreen() {
        super(Component.translatable("screen.buildingwand.settings.title"));
        this.smartScanLimit = BuildingWandConfig.smartScanLimit();
        this.smartBlockLimit = BuildingWandConfig.smartBlockLimit();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
    }

    @Override
    protected void init() {
        super.init();
        int x0 = (width - BG_WIDTH) / 2;
        int y0 = (height - BG_HEIGHT) / 2;
        int row1 = y0 + 46;
        int row2 = y0 + 82;

        addRenderableWidget(Button.builder(Component.literal("-5000"),
                btn -> { smartScanLimit = clampScan(smartScanLimit - 5_000); rebuildWidgets(); })
                .bounds(x0 + 18, row1, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+5000"),
                btn -> { smartScanLimit = clampScan(smartScanLimit + 5_000); rebuildWidgets(); })
                .bounds(x0 + BG_WIDTH - 74, row1, 56, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-1000"),
                btn -> { smartBlockLimit = clampBlocks(smartBlockLimit - 1_000); rebuildWidgets(); })
                .bounds(x0 + 18, row2, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1000"),
                btn -> { smartBlockLimit = clampBlocks(smartBlockLimit + 1_000); rebuildWidgets(); })
                .bounds(x0 + BG_WIDTH - 74, row2, 56, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.buildingwand.settings.default"),
                btn -> {
                    smartScanLimit = 80_000;
                    smartBlockLimit = 25_000;
                    rebuildWidgets();
                }).bounds(x0 + 18, y0 + BG_HEIGHT - 28, 60, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.buildingwand.settings.save"),
                btn -> {
                    BuildingWandConfig.setSmartScanLimit(smartScanLimit);
                    BuildingWandConfig.setSmartBlockLimit(smartBlockLimit);
                    BuildingWandConfig.save();
                    Minecraft.getInstance().setScreenAndShow(null);
                }).bounds(x0 + BG_WIDTH - 78, y0 + BG_HEIGHT - 28, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int x0 = (width - BG_WIDTH) / 2;
        int y0 = (height - BG_HEIGHT) / 2;

        g.fill(x0, y0, x0 + BG_WIDTH, y0 + BG_HEIGHT, 0xD0000000);
        g.fill(x0, y0, x0 + BG_WIDTH, y0 + 1, 0xFFAAAAAA);
        g.fill(x0, y0 + BG_HEIGHT - 1, x0 + BG_WIDTH, y0 + BG_HEIGHT, 0xFFAAAAAA);
        g.fill(x0, y0, x0 + 1, y0 + BG_HEIGHT, 0xFFAAAAAA);
        g.fill(x0 + BG_WIDTH - 1, y0, x0 + BG_WIDTH, y0 + BG_HEIGHT, 0xFFAAAAAA);

        g.centeredText(font, Component.translatable("screen.buildingwand.settings.title").withStyle(ChatFormatting.YELLOW), width / 2, y0 + 10, -1);

        g.text(font, Component.translatable("screen.buildingwand.settings.scan_limit").withStyle(ChatFormatting.YELLOW), x0 + 90, y0 + 34, -1);
        g.centeredText(font, Component.literal(Integer.toString(smartScanLimit)).withStyle(ChatFormatting.AQUA), width / 2, y0 + 52, -1);

        g.text(font, Component.translatable("screen.buildingwand.settings.block_limit").withStyle(ChatFormatting.YELLOW), x0 + 90, y0 + 70, -1);
        g.centeredText(font, Component.literal(Integer.toString(smartBlockLimit)).withStyle(ChatFormatting.GREEN), width / 2, y0 + 88, -1);

        g.text(font, Component.translatable("screen.buildingwand.settings.scan_hint").withStyle(ChatFormatting.GRAY), x0 + 18, y0 + 114, -1);
        g.text(font, Component.translatable("screen.buildingwand.settings.block_hint").withStyle(ChatFormatting.GRAY), x0 + 18, y0 + 126, -1);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int clampScan(int value) {
        return Math.max(BuildingWandConfig.minSmartScanLimit(), Math.min(BuildingWandConfig.maxSmartScanLimit(), value));
    }

    private static int clampBlocks(int value) {
        return Math.max(BuildingWandConfig.minSmartBlockLimit(), Math.min(BuildingWandConfig.maxSmartBlockLimit(), value));
    }
}
