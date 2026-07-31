package neo.z_mods.biotech.block.entity;

import neo.z_mods.biotech.block.BioMachineBlock;
import neo.z_mods.biotech.menu.BioMachineMenu;
import neo.z_mods.biotech.multiblock.MultiblockDefinition;
import neo.z_mods.biotech.multiblock.MultiblockRegistry;
import neo.z_mods.biotech.registry.ModBlockEntities;
import neo.z_mods.biotech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BioMachineBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int MAX_ENERGY = 100_000;
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int energy;
    private int progress;
    private int maxProgress = 1;
    private boolean formed;
    private Direction formedDirection = Direction.NORTH;
    private long lastStructureCheck;

    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> energy;
                case 1 -> progress;
                case 2 -> formed ? 1 : 0;
                case 3 -> maxProgress;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> energy = value;
                case 1 -> progress = value;
                case 2 -> formed = value != 0;
                case 3 -> maxProgress = Math.max(1, value);
                default -> { }
            }
        }
        @Override public int getCount() { return 4; }
    };

    public BioMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIO_MACHINE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BioMachineBlockEntity machine) {
        if (level.isClientSide()) return;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

        if (state.getBlock() instanceof BioMachineBlock machineBlock && machineBlock.isCable()) {
            machine.balanceCableEnergy(level, pos, cableRate(id));
            return;
        }

        if (level.getGameTime() - machine.lastStructureCheck >= 20L) {
            machine.lastStructureCheck = level.getGameTime();
            MultiblockDefinition definition = MultiblockRegistry.get(id);
            if (definition == null) {
                machine.formed = true;
            } else {
                Direction direction = definition.findValidRotation(level, pos);
                machine.formed = direction != null;
                if (direction != null) machine.formedDirection = direction;
            }
            machine.setChanged();
        }

        machine.pullEnergy(level, pos, 300);
        if (id.equals("bioreactor")) {
            machine.tickBioreactor();
        } else if (machine.formed) {
            machine.tickProcess(id);
        } else {
            machine.progress = Math.max(0, machine.progress - 2);
        }
    }

    private static int cableRate(String id) {
        return switch (id) {
            case "improved_biocable" -> 400;
            case "advanced_biocable" -> 1600;
            case "quantum_biocable" -> 6400;
            default -> 100;
        };
    }

    private void balanceCableEnergy(Level level, BlockPos pos, int rate) {
        for (Direction direction : Direction.values()) {
            if (!(level.getBlockEntity(pos.relative(direction)) instanceof BioMachineBlockEntity other)) continue;
            int difference = other.energy - energy;
            if (difference > 0) {
                int moved = Math.min(rate, Math.min(Math.max(1, difference / 2), Math.min(other.energy, MAX_ENERGY - energy)));
                if (moved > 0) {
                    other.energy -= moved;
                    energy += moved;
                    other.setChanged();
                }
            } else if (difference < 0) {
                int moved = Math.min(rate, Math.min(Math.max(1, -difference / 2), Math.min(energy, MAX_ENERGY - other.energy)));
                if (moved > 0) {
                    energy -= moved;
                    other.energy += moved;
                    other.setChanged();
                }
            }
        }
        setChanged();
    }

    private void pullEnergy(Level level, BlockPos pos, int rate) {
        for (Direction direction : Direction.values()) {
            if (!(level.getBlockEntity(pos.relative(direction)) instanceof BioMachineBlockEntity other)) continue;
            if (!(other.getBlockState().getBlock() instanceof BioMachineBlock cable) || !cable.isCable()) continue;
            int moved = Math.min(rate, Math.min(other.energy, MAX_ENERGY - energy));
            if (moved <= 0) continue;
            other.energy -= moved;
            energy += moved;
            other.setChanged();
        }
    }

    private void tickBioreactor() {
        if (!formed || energy >= MAX_ENERGY) return;
        ItemStack fuel = items.get(0);
        boolean biofuel = fuel.is(ModContent.BIOFUEL.get());
        boolean biomass = fuel.is(ModContent.BIOMASS.get());
        if (!biofuel && !biomass) {
            progress = 0;
            maxProgress = 1;
            return;
        }
        maxProgress = biofuel ? 40 : 100;
        progress++;
        if (progress < maxProgress) return;
        fuel.shrink(1);
        energy = Math.min(MAX_ENERGY, energy + (biofuel ? 8000 : 2500));
        progress = 0;
        setChanged();
    }

    private void tickProcess(String id) {
        RecipePlan plan = recipeFor(id);
        maxProgress = plan == null ? 1 : plan.duration;
        if (plan == null || energy < plan.energyCost || !plan.matches(items.get(0), items.get(1)) || !canOutput(plan.output)) {
            progress = Math.max(0, progress - 1);
            return;
        }
        progress++;
        if (progress < plan.duration) return;

        ItemStack originalFirst = items.get(0).copy();
        ItemStack originalSecond = items.get(1).copy();
        items.get(0).shrink(plan.consumeFirst);
        if (plan.consumeSecond > 0) items.get(1).shrink(plan.consumeSecond);
        ItemStack result = plan.copyOutput(originalFirst);

        if (id.equals("injector_upgrader")) {
            CompoundTag data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (originalSecond.is(ModContent.RANGE_UPGRADE.get())) data.putBoolean("BioTechRange", true);
            if (originalSecond.is(ModContent.SAMPLE_STABILIZER_UPGRADE.get())) data.putBoolean("BioTechStabilizer", true);
            if (originalSecond.is(ModContent.EXTRACTION_ACCELERATOR_UPGRADE.get())) data.putBoolean("BioTechAccelerator", true);
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }

        if (items.get(2).isEmpty()) items.set(2, result);
        else items.get(2).grow(result.getCount());
        energy -= plan.energyCost;
        progress = 0;
        setChanged();
    }

    private RecipePlan recipeFor(String id) {
        return switch (id) {
            case "dna_analyzer", "gene_analyzer" -> new RecipePlan(ModContent.DNA_CAPSULE_STANDARD.get(), null, new ItemStack(ModContent.GENETIC_CHIP.get()), 120, 400, 1, 0);
            case "dna_synthesizer" -> new RecipePlan(ModContent.DNA_CAPSULE_STANDARD.get(), ModContent.BIO_GEL.get(), new ItemStack(ModContent.DNA_CAPSULE_IMPROVED.get()), 180, 1200, 1, 1);
            case "dna_mixer" -> new RecipePlan(ModContent.DNA_CAPSULE_STANDARD.get(), ModContent.MUTAGEN.get(), new ItemStack(ModContent.DNA_CAPSULE_IMPROVED.get()), 160, 1000, 1, 1);
            case "dna_hybridizer" -> new RecipePlan(ModContent.DNA_CAPSULE_STANDARD.get(), ModContent.DNA_CAPSULE_STANDARD.get(), new ItemStack(ModContent.RARE_DNA_SAMPLE.get()), 240, 1800, 1, 1);
            case "dna_integrator" -> new RecipePlan(ModContent.DNA_CAPSULE_IMPROVED.get(), ModContent.BIO_GEL.get(), new ItemStack(ModContent.BIO_CORE.get()), 300, 5000, 1, 1);
            case "injector_upgrader" -> new RecipePlan(ModContent.DNK_INJECTOR.get(), null, new ItemStack(ModContent.DNK_INJECTOR.get()), 100, 800, 1, 1) {
                @Override public boolean matches(ItemStack first, ItemStack second) {
                    return first.is(ModContent.DNK_INJECTOR.get()) && (second.is(ModContent.RANGE_UPGRADE.get()) || second.is(ModContent.SAMPLE_STABILIZER_UPGRADE.get()) || second.is(ModContent.EXTRACTION_ACCELERATOR_UPGRADE.get()));
                }
                @Override public ItemStack copyOutput(ItemStack first) { return first.copyWithCount(1); }
            };
            case "cooler" -> new RecipePlan(ModContent.ENERGY_CRYSTAL.get(), ModContent.BIO_GEL.get(), new ItemStack(ModContent.QUANTUM_CRYSTAL.get()), 180, 1600, 1, 1);
            default -> null;
        };
    }

    private boolean canOutput(ItemStack output) {
        ItemStack current = items.get(2);
        return current.isEmpty() || (ItemStack.isSameItemSameComponents(current, output) && current.getCount() + output.getCount() <= current.getMaxStackSize());
    }

    public int getEnergy() { return energy; }
    public int getProgress() { return progress; }
    public boolean isFormed() { return formed; }
    public Direction getFormedDirection() { return formedDirection; }
    public ContainerData getDataAccess() { return dataAccess; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putBoolean("Formed", formed);
        tag.putInt("FormedDirection", formedDirection.get2DDataValue());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        energy = tag.getInt("Energy");
        progress = tag.getInt("Progress");
        maxProgress = Math.max(1, tag.getInt("MaxProgress"));
        formed = tag.getBoolean("Formed");
        formedDirection = Direction.from2DDataValue(tag.getInt("FormedDirection"));
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack stack = ContainerHelper.removeItem(items, slot, amount); if (!stack.isEmpty()) setChanged(); return stack; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); stack.limitSize(getMaxStackSize(stack)); setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { items.clear(); setChanged(); }

    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BioMachineMenu(containerId, inventory, this, dataAccess);
    }

    private static class RecipePlan {
        final ItemStack firstTemplate;
        final ItemStack secondTemplate;
        final ItemStack output;
        final int duration;
        final int energyCost;
        final int consumeFirst;
        final int consumeSecond;

        RecipePlan(Item first, Item second, ItemStack output, int duration, int energyCost, int consumeFirst, int consumeSecond) {
            this.firstTemplate = new ItemStack(first);
            this.secondTemplate = second == null ? ItemStack.EMPTY : new ItemStack(second);
            this.output = output;
            this.duration = duration;
            this.energyCost = energyCost;
            this.consumeFirst = consumeFirst;
            this.consumeSecond = consumeSecond;
        }

        public boolean matches(ItemStack first, ItemStack second) {
            return first.is(firstTemplate.getItem()) && (secondTemplate.isEmpty() || second.is(secondTemplate.getItem()));
        }

        public ItemStack copyOutput(ItemStack first) {
            return output.copy();
        }
    }
}
