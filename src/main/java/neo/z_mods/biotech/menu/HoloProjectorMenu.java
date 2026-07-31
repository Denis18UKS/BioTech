package neo.z_mods.biotech.menu;

import neo.z_mods.biotech.block.entity.HoloProjectorBlockEntity;
import neo.z_mods.biotech.multiblock.MultiblockRegistry;
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

public class HoloProjectorMenu extends AbstractContainerMenu {
    public static final int SELECT_BASE = 0;
    public static final int PREVIOUS_LAYER = 100;
    public static final int NEXT_LAYER = 101;
    public static final int ALL_LAYERS = 102;

    private final Container container;
    private final ContainerData data;
    private final HoloProjectorBlockEntity projector;

    public HoloProjectorMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1), new SimpleContainerData(3), null);
    }

    public HoloProjectorMenu(int containerId, Inventory playerInventory, Container container, ContainerData data,
                             HoloProjectorBlockEntity projector) {
        super(ModMenus.HOLO_PROJECTOR.get(), containerId);
        checkContainerSize(container, 1);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        this.projector = projector;
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, 0, 18, 35));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 112 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 170));
        }
        addDataSlots(data);
    }

    public int selectedDefinition() {
        return data.get(0);
    }

    public int selectedLayer() {
        return data.get(1) - 1;
    }

    public boolean completed() {
        return data.get(2) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (projector == null) return true;
        if (id >= SELECT_BASE && id < SELECT_BASE + MultiblockRegistry.all().size()) {
            projector.selectDefinition(id - SELECT_BASE);
            return true;
        }
        if (id == PREVIOUS_LAYER) {
            projector.changeLayer(-1);
            return true;
        }
        if (id == NEXT_LAYER) {
            projector.changeLayer(1);
            return true;
        }
        if (id == ALL_LAYERS) {
            projector.showAllLayers();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack source = slot.getItem();
            result = source.copy();
            if (index == 0) {
                if (!moveItemStackTo(source, 1, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(source, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
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
