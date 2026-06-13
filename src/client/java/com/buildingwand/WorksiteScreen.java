package com.buildingwand;

import com.buildingwand.network.WorksiteActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class WorksiteScreen extends Screen {

    private final BlockPos worksitePos;
    private Map<String, Integer> needed;
    private Map<String, Integer> deposited;
    private boolean building;

    private final List<String> materialLines = new ArrayList<>();

    private static final int BG_WIDTH = 340;
    private static final int BG_HEIGHT = 258;
    private static final int LINE_H = 12;
    private static final int MAX_VISIBLE = 11;

    private int scrollOffset = 0;
    private String selectedMaterialId = "";

    public WorksiteScreen(BlockPos pos, Map<String, Integer> needed,
                          Map<String, Integer> deposited, boolean building) {
        super(Component.translatable("screen.buildingwand.worksite.title"));
        this.worksitePos = pos;
        this.needed = needed;
        this.deposited = deposited;
        this.building = building;
    }

    public void refresh(Map<String, Integer> needed, Map<String, Integer> deposited, boolean building) {
        this.needed = needed;
        this.deposited = deposited;
        this.building = building;
        rebuildLines();
        if (!materialLines.contains(selectedMaterialId)) selectedMaterialId = "";
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, materialLines.size() - MAX_VISIBLE)));
        init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
    }

    @Override
    protected void init() {
        super.init();
        rebuildLines();

        int cx = width / 2;
        int x0 = (width - BG_WIDTH) / 2;
        int y0 = (height - BG_HEIGHT) / 2;
        int by = y0 + BG_HEIGHT - 28;
        int byTop = by - 24;

        if (materialLines.size() > MAX_VISIBLE) {
            int scrollBtnX = x0 + BG_WIDTH - 20;
            int listTop = y0 + 36;
            addRenderableWidget(Button.builder(Component.literal("^"),
                    btn -> { if (scrollOffset > 0) scrollOffset--; }
            ).bounds(scrollBtnX, listTop, 16, 10).build());
            addRenderableWidget(Button.builder(Component.literal("v"),
                    btn -> { if (scrollOffset < materialLines.size() - MAX_VISIBLE) scrollOffset++; }
            ).bounds(scrollBtnX, listTop + MAX_VISIBLE * LINE_H - 10, 16, 10).build());
        }

        // Top row: pull from linked chests + withdraw deposited materials.
        addRenderableWidget(Button.builder(
                Component.translatable("screen.buildingwand.worksite.pull"),
                btn -> ClientPlayNetworking.send(new WorksiteActionPayload(worksitePos, 3, ""))
        ).bounds(cx - 160, byTop, 156, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.buildingwand.worksite.withdraw"),
                btn -> ClientPlayNetworking.send(new WorksiteActionPayload(worksitePos, 4, selectedMaterialId))
        ).bounds(cx + 4, byTop, 156, 20).build()).active = !building && !selectedMaterialId.isEmpty();

        addRenderableWidget(Button.builder(
                Component.translatable("screen.buildingwand.worksite.deposit"),
                btn -> ClientPlayNetworking.send(new WorksiteActionPayload(worksitePos, 0, ""))
        ).bounds(cx - 160, by, 96, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.buildingwand.worksite.replace_held"),
                btn -> ClientPlayNetworking.send(new WorksiteActionPayload(worksitePos, 2, selectedMaterialId))
        ).bounds(cx - 56, by, 112, 20).build()).active = !building && !selectedMaterialId.isEmpty();

        if (!building) {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.buildingwand.worksite.start"),
                    btn -> ClientPlayNetworking.send(new WorksiteActionPayload(worksitePos, 1, ""))
            ).bounds(cx + 64, by, 96, 20).build());
        } else {
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.buildingwand.worksite.building"),
                    btn -> {}
            ).bounds(cx + 64, by, 96, 20).build()).active = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && scrollOffset < Math.max(0, materialLines.size() - MAX_VISIBLE)) scrollOffset++;
        else if (scrollY > 0 && scrollOffset > 0) scrollOffset--;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            String clicked = materialIdAt(event.x(), event.y());
            if (clicked != null) {
                selectedMaterialId = clicked;
                init();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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

        g.centeredText(font, Component.translatable("screen.buildingwand.worksite.title").withStyle(ChatFormatting.YELLOW), width / 2, y0 + 8, -1);

        int tx = x0 + 8;
        int ty = y0 + 22;
        g.text(font, Component.translatable("screen.buildingwand.worksite.material").withStyle(ChatFormatting.YELLOW), tx, ty, -1);
        g.text(font, Component.translatable("screen.buildingwand.worksite.need").withStyle(ChatFormatting.YELLOW), tx + 170, ty, -1);
        g.text(font, Component.translatable("screen.buildingwand.worksite.deposited").withStyle(ChatFormatting.YELLOW), tx + 215, ty, -1);
        g.text(font, Component.translatable("screen.buildingwand.worksite.missing").withStyle(ChatFormatting.YELLOW), tx + 260, ty, -1);

        g.fill(x0 + 4, y0 + 32, x0 + BG_WIDTH - 4, y0 + 33, 0xFF666666);

        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, materialLines.size() - MAX_VISIBLE)));

        int listY = y0 + 36;
        int count = Math.min(materialLines.size() - scrollOffset, MAX_VISIBLE);
        for (int i = 0; i < count; i++) {
            String id = materialLines.get(i + scrollOffset);
            if (id.equals(selectedMaterialId)) {
                g.fill(x0 + 5, listY - 1, x0 + BG_WIDTH - 24, listY + LINE_H - 1, 0x405A8CFF);
            }
            drawMaterialLine(g, id, tx, listY);
            listY += LINE_H;
        }

        if (materialLines.size() > MAX_VISIBLE) {
            int end = Math.min(scrollOffset + MAX_VISIBLE, materialLines.size());
            String indicator = (scrollOffset + 1) + "-" + end + "/" + materialLines.size();
            g.text(font, Component.literal(indicator).withStyle(ChatFormatting.DARK_GRAY),
                    tx + 268, y0 + 36 + MAX_VISIBLE * LINE_H / 2 - 4, -1);
        }

        if (!selectedMaterialId.isEmpty()) {
            String shortId = selectedMaterialId.contains(":") ? selectedMaterialId.split(":")[1] : selectedMaterialId;
            g.text(font, Component.translatable("screen.buildingwand.worksite.selected", shortId).withStyle(ChatFormatting.GRAY), x0 + 8, y0 + BG_HEIGHT - 82, -1);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawMaterialLine(GuiGraphicsExtractor g, String blockId, int tx, int ty) {
        int n = needed.getOrDefault(blockId, 0);
        int d = deposited.getOrDefault(blockId, 0);
        int miss = Math.max(0, n - d);

        ItemStack icon = ItemStack.EMPTY;
        String displayName = blockId;
        try {
            Block block = BuiltInRegistries.BLOCK.get(Identifier.parse(blockId)).map(ref -> ref.value()).orElse(null);
            if (block != null) {
                icon = new ItemStack(block);
                displayName = block.getName().getString();
            } else {
                Item item = BuiltInRegistries.ITEM.get(Identifier.parse(blockId)).map(ref -> ref.value()).orElse(null);
                if (item != null && item != Items.AIR) {
                    icon = new ItemStack(item);
                    displayName = icon.getHoverName().getString();
                } else {
                    displayName = blockId.contains(":") ? blockId.split(":")[1] : blockId;
                }
            }
        } catch (Exception ignored) {
            displayName = blockId.contains(":") ? blockId.split(":")[1] : blockId;
        }

        if (!icon.isEmpty()) g.fakeItem(icon, tx, ty - 2);
        if (displayName.length() > 15) displayName = displayName.substring(0, 13) + "..";

        ChatFormatting nameColor = miss > 0 ? ChatFormatting.RED : ChatFormatting.GREEN;
        g.text(font, Component.literal(displayName).withStyle(nameColor), tx + 18, ty, -1);
        g.text(font, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.AQUA), tx + 170, ty, -1);
        g.text(font, Component.literal(String.valueOf(d)).withStyle(d >= n ? ChatFormatting.GREEN : ChatFormatting.GOLD), tx + 215, ty, -1);

        if (miss > 0) {
            g.text(font, Component.literal(String.valueOf(miss)).withStyle(ChatFormatting.RED), tx + 260, ty, -1);
        } else {
            g.text(font, Component.translatable("screen.buildingwand.worksite.done").withStyle(ChatFormatting.GREEN), tx + 260, ty, -1);
        }
    }

    private String materialIdAt(double mouseX, double mouseY) {
        int x0 = (width - BG_WIDTH) / 2;
        int y0 = (height - BG_HEIGHT) / 2;
        int top = y0 + 36;
        int left = x0 + 5;
        int right = x0 + BG_WIDTH - 24;
        if (mouseX < left || mouseX > right || mouseY < top) return null;
        int row = (int) ((mouseY - top) / LINE_H);
        if (row < 0 || row >= MAX_VISIBLE) return null;
        int index = row + scrollOffset;
        if (index < 0 || index >= materialLines.size()) return null;
        return materialLines.get(index);
    }

    private void rebuildLines() {
        materialLines.clear();
        List<String> ids = new ArrayList<>(needed.keySet());
        ids.sort(Comparator.<String>comparingInt(id -> {
            int n = needed.getOrDefault(id, 0);
            int dep = deposited.getOrDefault(id, 0);
            return dep >= n ? 1 : 0;
        }).thenComparing(id -> id));
        materialLines.addAll(ids);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean isFor(BlockPos pos) {
        return worksitePos.equals(pos);
    }

    public static void openOrRefresh(BlockPos pos, Map<String, Integer> needed,
                                     Map<String, Integer> deposited, boolean building) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof WorksiteScreen ws && ws.isFor(pos)) {
                ws.refresh(needed, deposited, building);
            } else {
                mc.setScreen(new WorksiteScreen(pos, needed, deposited, building));
            }
        });
    }
}
