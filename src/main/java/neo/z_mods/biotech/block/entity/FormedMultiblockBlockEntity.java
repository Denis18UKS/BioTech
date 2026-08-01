package neo.z_mods.biotech.block.entity;

import neo.z_mods.biotech.multiblock.MultiblockDefinition;
import neo.z_mods.biotech.multiblock.MultiblockRegistry;
import neo.z_mods.biotech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер уже собранной цельной машины. Он хранит прежний инвентарь и
 * энергию, а клиентский рендерер рисует одну большую 3D-модель.
 */
public class FormedMultiblockBlockEntity extends BioMachineBlockEntity {
    private String machineId = "bioreactor";
    private Direction assemblyDirection = Direction.NORTH;

    public FormedMultiblockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORMED_MULTIBLOCK.get(), pos, state);
        formed = true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FormedMultiblockBlockEntity machine) {
        BioMachineBlockEntity.serverTick(level, pos, state, machine);
    }

    public void initialize(String id, Direction direction, CompoundTag machineData, HolderLookup.Provider registries) {
        machineId = id;
        assemblyDirection = direction;
        loadMachineData(machineData, registries);
        formed = true;
        formedDirection = direction;
        markChangedAndSync();
    }

    public String getFormedMachineId() {
        return machineId;
    }

    public Direction getAssemblyDirection() {
        return assemblyDirection;
    }

    @Override
    protected String getMachineId(BlockState state) {
        return machineId;
    }

    @Override
    protected boolean isCollapsedFormed() {
        return true;
    }

    @Override
    protected Direction getCollapsedDirection() {
        return assemblyDirection;
    }

    @Override
    protected Iterable<BlockPos> energyInputPositions(Level level, BlockPos pos) {
        MultiblockDefinition definition = MultiblockRegistry.get(machineId);
        if (definition == null) {
            return List.of(pos);
        }
        List<BlockPos> positions = new ArrayList<>();
        for (MultiblockDefinition.Requirement requirement : definition.requirements()) {
            if (requirement.block().get() == neo.z_mods.biotech.registry.ModContent.ENERGY_PORT.get()) {
                positions.add(definition.worldPosition(pos, assemblyDirection, requirement.offset()));
            }
        }
        return positions;
    }

    private void markChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("MachineId", machineId);
        tag.putInt("AssemblyDirection", assemblyDirection.get2DDataValue());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String loadedId = tag.getString("MachineId");
        if (!loadedId.isBlank()) {
            machineId = loadedId;
        }
        assemblyDirection = Direction.from2DDataValue(tag.getInt("AssemblyDirection"));
        formed = true;
        formedDirection = assemblyDirection;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.biotech." + machineId);
    }
}
