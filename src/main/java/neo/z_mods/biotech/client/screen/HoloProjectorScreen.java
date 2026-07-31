package neo.z_mods.biotech.client.screen;

import neo.z_mods.biotech.menu.HoloProjectorMenu;
import neo.z_mods.biotech.multiblock.MultiblockDefinition;
import neo.z_mods.biotech.multiblock.MultiblockRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class HoloProjectorScreen extends AbstractContainerScreen<HoloProjectorMenu> {
    public HoloProjectorScreen(HoloProjectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 194;
        inventoryLabelY = 100;
    }

    @Override
    protected void init() {
        super.init();
        List<MultiblockDefinition> definitions = MultiblockRegistry.all();
        for (int i = 0; i < definitions.size(); i++) {
            int buttonId = i;
            String text = shortName(definitions.get(i).id());
            addRenderableWidget(Button.builder(Component.literal(text), button -> send(buttonId))
                    .bounds(leftPos + 47, topPos + 22 + i * 15, 115, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal("− слой"), b -> send(HoloProjectorMenu.PREVIOUS_LAYER))
                .bounds(leftPos + 8, topPos + 89, 51, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Все"), b -> send(HoloProjectorMenu.ALL_LAYERS))
                .bounds(leftPos + 62, topPos + 89, 51, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+ слой"), b -> send(HoloProjectorMenu.NEXT_LAYER))
                .bounds(leftPos + 116, topPos + 89, 51, 16).build());
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xEE111918);
        border(graphics, x, y, imageWidth, imageHeight, 0xFF4E6660);
        graphics.fill(x + 5, y + 17, x + 171, y + 108, 0xFF1C2724);
        border(graphics, x + 5, y + 17, 166, 91, 0xFF55716A);
        slot(graphics, x + 17, y + 34);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(graphics, x + 7 + col * 18, y + 111 + row * 18);
        for (int col = 0; col < 9; col++) slot(graphics, x + 7 + col * 18, y + 169);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFE1E9E5, false);
        graphics.drawString(font, "Чертёж", 10, 23, 0xFF78E982, false);
        String layer = menu.selectedLayer() < 0 ? "все" : Integer.toString(menu.selectedLayer());
        graphics.drawString(font, "Слой: " + layer, 8, 78, 0xFFB8C5C0, false);
        graphics.drawString(font, menu.completed() ? "Структура собрана" : "Проекция активна", 84, 78,
                menu.completed() ? 0xFF64F27A : 0xFF5AE1C1, false);
        graphics.drawString(font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xFFB7C3BF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static String shortName(String id) {
        return switch (id) {
            case "dna_synthesizer" -> "Синтезатор ДНК";
            case "dna_mixer" -> "Смеситель ДНК";
            case "dna_hybridizer" -> "Гибридизатор ДНК";
            case "dna_integrator" -> "Интегратор ДНК";
            case "bioreactor" -> "Биореактор";
            default -> id;
        };
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF52605B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF141B19);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
