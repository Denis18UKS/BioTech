package neo.z_mods.biotech.registry;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.block.entity.BioMachineBlockEntity;
import neo.z_mods.biotech.block.entity.HoloProjectorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BioTech.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BioMachineBlockEntity>> BIO_MACHINE = BLOCK_ENTITIES.register(
            "bio_machine",
            () -> BlockEntityType.Builder.of(
                    BioMachineBlockEntity::new,
                    ModContent.MACHINE_ENTITY_BLOCKS.stream().map(holder -> holder.get()).toArray(Block[]::new)
            ).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoloProjectorBlockEntity>> HOLO_PROJECTOR = BLOCK_ENTITIES.register(
            "holo_projector",
            () -> BlockEntityType.Builder.of(HoloProjectorBlockEntity::new, ModContent.HOLO_PROJECTOR.get()).build(null)
    );

    private ModBlockEntities() {
    }
}
