package neo.z_mods.biotech.client.screen;

import neo.z_mods.biotech.menu.BioMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BioMachineScreen extends AbstractContainerScreen<BioMachineMenu> {
    public BioMachineScreen(BioMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = 166;
        inventoryLabelX = 16;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        boolean bioreactor = title.getString().toLowerCase().contains("биореактор")
                || title.getString().toLowerCase().contains("bioreactor");

        graphics.fill(x + 3, y + 3, x + imageWidth + 3, y + imageHeight + 3, 0x70000000);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xF0151B1A);
        border(graphics, x, y, imageWidth, imageHeight, 0xFF53615C);
        graphics.fill(x + 5, y + 18, x + imageWidth - 5, y + 76, 0xFF202927);
        border(graphics, x + 5, y + 18, imageWidth - 10, 58, 0xFF65736E);

        slot(graphics, x + 34, y + 34);
        slot(graphics, x + 70, y + 34);
        slot(graphics, x + 124, y + 34);
        slot(graphics, x + 163, y + 34);

        graphics.fill(x + 91, y + 41, x + 112, y + 45, 0xFF79847F);
        graphics.fill(x + 108, y + 37, x + 112, y + 49, 0xFF79847F);
        int progressWidth = Math.round(31 * menu.progressFraction());
        graphics.fill(x + 88, y + 57, x + 121, y + 64, 0xFF0D1311);
        graphics.fill(x + 89, y + 58, x + 89 + progressWidth, y + 63,
                bioreactor ? 0xFF62E56C : 0xFF52D866);

        // Горизонтальная шкала энергии. Слот справа предназначен для батарейки.
        int energyWidth = Math.round(150 * menu.energyFraction());
        graphics.fill(x + 20, y + 67, x + 172, y + 74, 0xFF0B100F);
        graphics.fill(x + 21, y + 68, x + 21 + energyWidth, y + 73, 0xFF39E778);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + 15 + col * 18, y + 83 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + 15 + col * 18, y + 141);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean bioreactor = title.getString().toLowerCase().contains("биореактор")
                || title.getString().toLowerCase().contains("bioreactor");
        graphics.drawString(font, title, titleLabelX, 6, 0xFFDDE5E2, false);
        graphics.drawString(font, "RF " + menu.energy() + "/100000", 116, 7, 0xFF63E98A, false);

        if (bioreactor) {
            graphics.drawString(font, "Биомасса", 24, 23, 0xFFAED7B5, false);
            graphics.drawString(font, "Катализатор", 61, 23, 0xFFAED7B5, false);
            graphics.drawString(font, "Энергия", 119, 23, 0xFFAED7B5, false);
        } else {
            graphics.drawString(font, "Материал", 27, 23, 0xFFAED7B5, false);
            graphics.drawString(font, "Модуль", 64, 23, 0xFFAED7B5, false);
            graphics.drawString(font, "Результат", 119, 23, 0xFFAED7B5, false);
        }
        graphics.drawString(font, "АКБ", 164, 23, 0xFF8BEAB0, false);

        graphics.drawString(
                font,
                menu.formed() ? "Корпус: собран" : "Одиночная машина",
                8,
                78,
                menu.formed() ? 0xFF6BEE77 : 0xFFB7C6BF,
                false
        );
        graphics.drawString(font, Component.translatable("container.inventory"), inventoryLabelX, inventoryLabelY, 0xFFBAC4C0, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF56605D);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF161D1B);
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
