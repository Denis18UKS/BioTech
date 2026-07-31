package neo.z_mods.biotech.block.entity;

import neo.z_mods.biotech.block.HoloProjectorBlock;
import neo.z_mods.biotech.menu.HoloProjectorMenu;
import neo.z_mods.biotech.multiblock.MultiblockDefinition;
import neo.z_mods.biotech.multiblock.MultiblockRegistry;
import neo.z_mods.biotech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HoloProjectorBlockEntity extends BlockEntity implements Container, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int selectedDefinition;
    private int selectedLayer = -1;
    private boolean completed;

    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> selectedDefinition;
                case 1 -> selectedLayer + 1;
                case 2 -> completed ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> selectedDefinition = value;
                case 1 -> selectedLayer = value - 1;
                case 2 -> completed = value != 0;
                default -> { }
            }
        }
        @Override public int getCount() { return 3; }
    };

    public HoloProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLO_PROJECTOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HoloProjectorBlockEntity projector) {
        if (level.getGameTime() % 20L != 0L) return;
        MultiblockDefinition definition = projector.getDefinition();
        Direction direction = projector.getProjectionDirection();
        projector.completed = definition != null && definition.validate(level, pos.above(), direction);
        projector.setChanged();
    }

    public void selectDefinition(int index) {
        List<MultiblockDefinition> definitions = MultiblockRegistry.all();
        if (definitions.isEmpty()) return;
        selectedDefinition = Math.floorMod(index, definitions.size());
        selectedLayer = -1;
        completed = false;
        setChanged();
    }

    public void changeLayer(int delta) {
        MultiblockDefinition definition = getDefinition();
        if (definition == null) return;
        int maxLayer = definition.maxLayer();
        if (selectedLayer < 0) selectedLayer = delta > 0 ? 0 : maxLayer;
        else selectedLayer = Math.floorMod(selectedLayer + delta, maxLayer + 1);
        setChanged();
    }

    public void showAllLayers() {
        selectedLayer = -1;
        setChanged();
    }

    public MultiblockDefinition getDefinition() {
        MultiblockDefinition blueprintDefinition = MultiblockRegistry.fromBlueprint(items.get(0));
        if (blueprintDefinition != null) return blueprintDefinition;
        List<MultiblockDefinition> definitions = MultiblockRegistry.all();
        if (definitions.isEmpty()) return null;
        return definitions.get(Math.floorMod(selectedDefinition, definitions.size()));
    }

    public int getSelectedLayer() { return selectedLayer; }
    public boolean isCompleted() { return completed; }

    public Direction getProjectionDirection() {
        BlockState state = getBlockState();
        return state.hasProperty(HoloProjectorBlock.FACING) ? state.getValue(HoloProjectorBlock.FACING) : Direction.NORTH;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("SelectedDefinition", selectedDefinition);
        tag.putInt("SelectedLayer", selectedLayer);
        tag.putBoolean("Completed", completed);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        selectedDefinition = tag.getInt("SelectedDefinition");
        selectedLayer = tag.getInt("SelectedLayer");
        completed = tag.getBoolean("Completed");
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack stack = ContainerHelper.removeItem(items, slot, amount); if (!stack.isEmpty()) setChanged(); return stack; }
    @Override public ItemStack removeItemNoUpdate(int slot) { ItemStack stack = ContainerHelper.takeItem(items, slot); setChanged(); return stack; }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); stack.limitSize(getMaxStackSize(stack)); setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public Component getDisplayName() { return Component.translatable("container.biotech.holo_projector"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HoloProjectorMenu(id, inventory, this, dataAccess, this);
    }
}
