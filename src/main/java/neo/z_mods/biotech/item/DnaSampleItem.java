package neo.z_mods.biotech.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class DnaSampleItem extends Item {
    public DnaSampleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String entity = data.copyTag().getString("BioTechEntity");
        int quality = data.copyTag().getInt("BioTechQuality");
        if (!entity.isBlank()) {
            tooltip.add(Component.literal("ДНК: " + entity).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Качество: " + quality + "%").withStyle(quality >= 80 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
