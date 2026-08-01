package neo.z_mods.biotech.menu;

import neo.z_mods.biotech.block.entity.BioMachineBlockEntity;
import neo.z_mods.biotech.item.BioBatteryItem;
import neo.z_mods.biotech.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BioMachineMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = 4;
    private final Container container;
    private final ContainerData data;

    public BioMachineMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(4));
    }

    public BioMachineMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.BIO_MACHINE.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerDataCount(data, 4);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, 0, 35, 35));
        addSlot(new Slot(container, 1, 71, 35));
        addSlot(new Slot(container, 2, 125, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Slot(container, BioMachineBlockEntity.BATTERY_SLOT, 164, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BioBatteryItem;
            }
        });

        addPlayerInventory(playerInventory, 16, 84);
        addDataSlots(data);
    }

    private void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, left + column * 18, top + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, left + column * 18, top + 58));
        }
    }

    public int energy() {
        return data.get(0);
    }

    public int progress() {
        return data.get(1);
    }

    public boolean formed() {
        return data.get(2) != 0;
    }

    public int maxProgress() {
        return Math.max(1, data.get(3));
    }

    public float energyFraction() {
        return Math.min(1.0F, energy() / (float) BioMachineBlockEntity.MAX_ENERGY);
    }

    public float progressFraction() {
        return Math.min(1.0F, progress() / (float) maxProgress());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return result;
        }

        ItemStack source = slot.getItem();
        result = source.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(source, MACHINE_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (source.getItem() instanceof BioBatteryItem) {
            if (!moveItemStackTo(source, BioMachineBlockEntity.BATTERY_SLOT, BioMachineBlockEntity.BATTERY_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(source, 0, 2, false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (source.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
