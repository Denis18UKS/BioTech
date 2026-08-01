package neo.z_mods.biotech.client;

import neo.z_mods.biotech.item.DnaSampleItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.client.IItemDecorator;

/** Иконка источника ДНК и цвет состояния поверх прозрачной капсулы. */
public class DnaCapsuleDecorator implements IItemDecorator {
    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        EntityType<?> source = DnaSampleItem.getSourceType(stack);
        if (source == null) {
            return false;
        }

        SpawnEggItem egg = SpawnEggItem.byId(source);
        if (egg != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(xOffset + 5.0F, yOffset + 5.0F, 220.0F);
            graphics.pose().scale(0.44F, 0.44F, 0.44F);
            graphics.renderFakeItem(new ItemStack(egg), 0, 0);
            graphics.pose().popPose();
        } else {
            String letter = source.getDescription().getString();
            if (!letter.isBlank()) {
                graphics.drawString(font, letter.substring(0, 1).toUpperCase(), xOffset + 6, yOffset + 5, 0xFFE8FFF0, true);
            }
        }

        int color = DnaSampleItem.stateColor(stack);
        graphics.fill(xOffset + 13, yOffset + 3, xOffset + 15, yOffset + 14, 0xD0000000);
        graphics.fill(xOffset + 13, yOffset + 5, xOffset + 15, yOffset + 14, color);
        return true;
    }
}
