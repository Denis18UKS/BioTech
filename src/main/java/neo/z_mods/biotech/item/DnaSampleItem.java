package neo.z_mods.biotech.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class DnaSampleItem extends Item {
    public DnaSampleItem(Properties properties) {
        super(properties);
    }

    public static String stateForQuality(int quality) {
        if (quality >= 80) {
            return "stable";
        }
        if (quality >= 40) {
            return "unstable";
        }
        return "damaged";
    }

    public static int stateColor(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return switch (tag.getString("BioTechState")) {
            case "stable" -> 0xFF55E873;
            case "unstable" -> 0xFFFFC14D;
            default -> 0xFFFF4B45;
        };
    }

    public static EntityType<?> getSourceType(ItemStack stack) {
        String value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("BioTechEntity");
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = getSourceType(stack);
        if (type == null) {
            return super.getName(stack);
        }
        return Component.literal("Капсула ДНК: ")
                .append(type.getDescription())
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        EntityType<?> type = getSourceType(stack);
        int quality = data.getInt("BioTechQuality");
        String state = data.getString("BioTechState");
        if (type != null) {
            tooltip.add(Component.literal("Источник: ").withStyle(ChatFormatting.GRAY)
                    .append(type.getDescription().copy().withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal("Качество: " + quality + "%")
                    .withStyle(quality >= 80 ? ChatFormatting.GREEN : quality >= 40 ? ChatFormatting.YELLOW : ChatFormatting.RED));
            tooltip.add(Component.literal("Состояние: " + switch (state) {
                case "stable" -> "стабильное";
                case "unstable" -> "нестабильное";
                default -> "повреждённое";
            }).withStyle(switch (state) {
                case "stable" -> ChatFormatting.GREEN;
                case "unstable" -> ChatFormatting.YELLOW;
                default -> ChatFormatting.RED;
            }));
            if (data.getBoolean("BioTechBaby")) {
                tooltip.add(Component.literal("Возраст образца: детёныш").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            tooltip.add(Component.literal("Состояние существа при заборе: "
                    + Math.round(data.getFloat("BioTechHealthFraction") * 100.0F) + "% здоровья")
                    .withStyle(ChatFormatting.DARK_GRAY));
            String secondary = data.getString("BioTechSecondaryEntity");
            if (!secondary.isBlank()) {
                tooltip.add(Component.literal("Гибридный компонент: " + secondary).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
