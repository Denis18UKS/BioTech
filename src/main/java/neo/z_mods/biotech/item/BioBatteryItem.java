package neo.z_mods.biotech.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Переносной аккумулятор, который вставляется непосредственно в GUI машины. */
public class BioBatteryItem extends Item {
    public static final String ENERGY_KEY = "BioTechBatteryEnergy";

    private final int capacity;
    private final int transferRate;

    public BioBatteryItem(Properties properties, int capacity, int transferRate) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.transferRate = transferRate;
    }

    public int capacity() {
        return capacity;
    }

    public int transferRate() {
        return transferRate;
    }

    public int getEnergy(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(ENERGY_KEY) ? Mth.clamp(tag.getInt(ENERGY_KEY), 0, capacity) : capacity;
    }

    public void setEnergy(ItemStack stack, int value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(ENERGY_KEY, Mth.clamp(value, 0, capacity));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getEnergy(stack) < capacity;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getEnergy(stack) / (float) capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = getEnergy(stack) / (float) capacity;
        return Mth.hsvToRgb(fraction * 0.33F, 0.85F, 0.95F);
    }
}
