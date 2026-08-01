package neo.z_mods.biotech.registry;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.block.BioMachineBlock;
import neo.z_mods.biotech.block.CableBlock;
import neo.z_mods.biotech.block.HoloProjectorBlock;
import neo.z_mods.biotech.block.FormedFrameBlock;
import neo.z_mods.biotech.block.FormedMultiblockBlock;
import neo.z_mods.biotech.item.DnaInjectorItem;
import neo.z_mods.biotech.item.AssemblyWrenchItem;
import neo.z_mods.biotech.item.DnaSampleItem;
import neo.z_mods.biotech.item.BioBatteryItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BioTech.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BioTech.MODID);

    public static final List<DeferredItem<? extends Item>> ITEM_TAB_CONTENT = new ArrayList<>();
    public static final List<DeferredItem<? extends Item>> BLOCK_TAB_CONTENT = new ArrayList<>();
    public static final List<DeferredBlock<? extends Block>> MACHINE_ENTITY_BLOCKS = new ArrayList<>();

    private static BlockBehaviour.Properties machineProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0F, 9.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties glassProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(2.5F, 7.0F)
                .noOcclusion()
                .sound(SoundType.GLASS);
    }

    private static DeferredBlock<Block> registerBlock(String name, BlockBehaviour.Properties properties) {
        DeferredBlock<Block> block = BLOCKS.register(name, () -> new Block(properties));
        DeferredItem<BlockItem> item = ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        BLOCK_TAB_CONTENT.add(item);
        return block;
    }

    private static DeferredBlock<BioMachineBlock> registerMachine(String name, BioMachineBlock.Role role) {
        DeferredBlock<BioMachineBlock> block = BLOCKS.register(name, () -> new BioMachineBlock(machineProps(), role));
        DeferredItem<BlockItem> item = ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        BLOCK_TAB_CONTENT.add(item);
        MACHINE_ENTITY_BLOCKS.add(block);
        return block;
    }

    private static DeferredBlock<CableBlock> registerCable(String name) {
        DeferredBlock<CableBlock> block = BLOCKS.register(name, () -> new CableBlock(machineProps()));
        DeferredItem<BlockItem> item = ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        BLOCK_TAB_CONTENT.add(item);
        MACHINE_ENTITY_BLOCKS.add(block);
        return block;
    }

    private static DeferredItem<BioBatteryItem> registerBattery(String name, int capacity, int rate) {
        DeferredItem<BioBatteryItem> item = ITEMS.register(
                name,
                () -> new BioBatteryItem(new Item.Properties(), capacity, rate)
        );
        ITEM_TAB_CONTENT.add(item);
        return item;
    }

    private static DeferredItem<Item> registerItem(String name) {
        DeferredItem<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties()));
        ITEM_TAB_CONTENT.add(item);
        return item;
    }

    private static DeferredItem<Item> registerBlueprint(String machine) {
        return registerItem("blueprint_" + machine);
    }

    // Материалы BioTech.
    public static final DeferredItem<Item> BIOMASS = registerItem("biomass");
    public static final DeferredItem<Item> BIO_GEL = registerItem("bio_gel");
    public static final DeferredItem<Item> GENETIC_CHIP = registerItem("genetic_chip");
    public static final DeferredItem<Item> ENERGY_CRYSTAL = registerItem("energy_crystal");
    public static final DeferredItem<Item> REINFORCED_ALLOY = registerItem("reinforced_alloy");
    public static final DeferredItem<Item> ANIMAL_CELL = registerItem("animal_cell");
    public static final DeferredItem<Item> QUANTUM_CRYSTAL = registerItem("quantum_crystal");
    public static final DeferredItem<Item> MUTAGEN = registerItem("mutagen");
    public static final DeferredItem<Item> STABILIZER = registerItem("stabilizer");
    public static final DeferredItem<Item> BIO_CORE = registerItem("bio_core");
    public static final DeferredItem<Item> BIOFUEL = registerItem("biofuel");
    public static final DeferredItem<Item> STABILIZING_SOLUTION = registerItem("stabilizing_solution");

    // Расходные предметы и ДНК.
    public static final DeferredItem<DnaInjectorItem> DNK_INJECTOR = ITEMS.register("dnk_injector", () -> new DnaInjectorItem(new Item.Properties().durability(512).stacksTo(1)));
    public static final DeferredItem<AssemblyWrenchItem> ASSEMBLY_WRENCH = ITEMS.register("assembly_wrench", () -> new AssemblyWrenchItem(new Item.Properties().durability(512).stacksTo(1)));
    public static final DeferredItem<Item> DNA_CAPSULE_EMPTY = registerItem("dna_capsule_empty");
    public static final DeferredItem<DnaSampleItem> DNA_CAPSULE_STANDARD = ITEMS.register("dna_capsule_standard", () -> new DnaSampleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<DnaSampleItem> DNA_CAPSULE_IMPROVED = ITEMS.register("dna_capsule_improved", () -> new DnaSampleItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> DNA_PURIFIER = registerItem("dna_purifier");
    public static final DeferredItem<Item> RARE_DNA_SAMPLE = registerItem("rare_dna_sample");

    // Улучшения инъектора.
    public static final DeferredItem<Item> RANGE_UPGRADE = registerItem("range_upgrade");
    public static final DeferredItem<Item> SAMPLE_STABILIZER_UPGRADE = registerItem("sample_stabilizer_upgrade");
    public static final DeferredItem<Item> EXTRACTION_ACCELERATOR_UPGRADE = registerItem("extraction_accelerator_upgrade");
    public static final DeferredItem<Item> NETHER_ADAPTATION_MODULE = registerItem("nether_adaptation_module");

    // Карманные аккумуляторы для прямого питания машин без кабельной сети.
    public static final DeferredItem<BioBatteryItem> POCKET_BATTERY = registerBattery("pocket_battery", 20_000, 240);
    public static final DeferredItem<BioBatteryItem> IMPROVED_POCKET_BATTERY = registerBattery("improved_pocket_battery", 80_000, 800);
    public static final DeferredItem<BioBatteryItem> ADVANCED_POCKET_BATTERY = registerBattery("advanced_pocket_battery", 320_000, 2_400);
    public static final DeferredItem<BioBatteryItem> QUANTUM_POCKET_BATTERY = registerBattery("quantum_pocket_battery", 1_280_000, 8_000);

    // Защитный костюм: временно использует ванильную геометрию железной брони.
    public static final DeferredItem<ArmorItem> PROTECTIVE_HELMET = ITEMS.register("protective_helmet", () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET, new Item.Properties().durability(420)));
    public static final DeferredItem<ArmorItem> PROTECTIVE_CHESTPLATE = ITEMS.register("protective_chestplate", () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Item.Properties().durability(610)));
    public static final DeferredItem<ArmorItem> PROTECTIVE_LEGGINGS = ITEMS.register("protective_leggings", () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS, new Item.Properties().durability(570)));
    public static final DeferredItem<ArmorItem> PROTECTIVE_BOOTS = ITEMS.register("protective_boots", () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS, new Item.Properties().durability(480)));

    static {
        ITEM_TAB_CONTENT.add(DNK_INJECTOR);
        ITEM_TAB_CONTENT.add(ASSEMBLY_WRENCH);
        ITEM_TAB_CONTENT.add(DNA_CAPSULE_STANDARD);
        ITEM_TAB_CONTENT.add(DNA_CAPSULE_IMPROVED);
        ITEM_TAB_CONTENT.add(PROTECTIVE_HELMET);
        ITEM_TAB_CONTENT.add(PROTECTIVE_CHESTPLATE);
        ITEM_TAB_CONTENT.add(PROTECTIVE_LEGGINGS);
        ITEM_TAB_CONTENT.add(PROTECTIVE_BOOTS);
    }

    // Простые строительные и функциональные блоки.
    public static final DeferredBlock<Block> CARBON_BLOCK = registerBlock("carbon_block", machineProps().mapColor(MapColor.COLOR_BLACK));
    public static final DeferredBlock<Block> REINFORCED_GLASS = registerBlock("reinforced_glass", glassProps());
    public static final DeferredBlock<Block> PURIFIED_GLASS = registerBlock("purified_glass", glassProps().mapColor(MapColor.COLOR_LIGHT_GREEN));

    // Одноблочные и мультиблочные машины.
    public static final DeferredBlock<BioMachineBlock> DNA_ANALYZER = registerMachine("dna_analyzer", BioMachineBlock.Role.SINGLE_MACHINE);
    public static final DeferredBlock<BioMachineBlock> DNA_SYNTHESIZER = registerMachine("dna_synthesizer", BioMachineBlock.Role.MULTIBLOCK_CONTROLLER);
    public static final DeferredBlock<BioMachineBlock> DNA_MIXER = registerMachine("dna_mixer", BioMachineBlock.Role.MULTIBLOCK_CONTROLLER);
    public static final DeferredBlock<BioMachineBlock> DNA_HYBRIDIZER = registerMachine("dna_hybridizer", BioMachineBlock.Role.MULTIBLOCK_CONTROLLER);
    public static final DeferredBlock<BioMachineBlock> DNA_INTEGRATOR = registerMachine("dna_integrator", BioMachineBlock.Role.MULTIBLOCK_CONTROLLER);
    public static final DeferredBlock<BioMachineBlock> BIOREACTOR = registerMachine("bioreactor", BioMachineBlock.Role.MULTIBLOCK_CONTROLLER);
    public static final DeferredBlock<BioMachineBlock> INJECTOR_UPGRADER = registerMachine("injector_upgrader", BioMachineBlock.Role.SINGLE_MACHINE);
    public static final DeferredBlock<BioMachineBlock> GENE_ANALYZER = registerMachine("gene_analyzer", BioMachineBlock.Role.SINGLE_MACHINE);
    public static final DeferredBlock<BioMachineBlock> COOLER = registerMachine("cooler", BioMachineBlock.Role.SINGLE_MACHINE);

    // Служебные блоки мультиструктур и энергии.
    public static final DeferredBlock<BioMachineBlock> CONTROLLER = registerMachine("controller", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> DNA_STABILIZER = registerMachine("dna_stabilizer", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> ENERGY_PORT = registerMachine("energy_port", BioMachineBlock.Role.ENERGY_PORT);
    public static final DeferredBlock<BioMachineBlock> DNA_CABLE = registerMachine("dna_cable", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> FUSION_CHAMBER = registerMachine("fusion_chamber", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> PLAYER_CAPSULE = registerMachine("player_capsule", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> BIO_DISTRIBUTOR = registerMachine("bio_distributor", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> ENERGY_STORAGE = registerMachine("energy_storage", BioMachineBlock.Role.SERVICE);
    public static final DeferredBlock<BioMachineBlock> BIO_STORAGE = registerMachine("bio_storage", BioMachineBlock.Role.SERVICE);

    // Четыре уровня биокабеля.
    public static final DeferredBlock<CableBlock> BASIC_BIOCABLE = registerCable("basic_biocable");
    public static final DeferredBlock<CableBlock> IMPROVED_BIOCABLE = registerCable("improved_biocable");
    public static final DeferredBlock<CableBlock> ADVANCED_BIOCABLE = registerCable("advanced_biocable");
    public static final DeferredBlock<CableBlock> QUANTUM_BIOCABLE = registerCable("quantum_biocable");

    // Служебные блоки уже собранной цельной 3D-машины. Они не выдаются игроку напрямую.
    public static final DeferredBlock<FormedMultiblockBlock> FORMED_MULTIBLOCK = BLOCKS.register(
            "formed_multiblock",
            () -> new FormedMultiblockBlock(machineProps().strength(4.5F, 18.0F).noOcclusion())
    );
    public static final DeferredBlock<FormedFrameBlock> FORMED_FRAME = BLOCKS.register(
            "formed_frame",
            () -> new FormedFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.5F, 18.0F)
                    .noOcclusion())
    );

    // Голопроектор и чертежи.
    public static final DeferredBlock<HoloProjectorBlock> HOLO_PROJECTOR = BLOCKS.register("holo_projector", () -> new HoloProjectorBlock(machineProps()));
    public static final DeferredItem<BlockItem> HOLO_PROJECTOR_ITEM = ITEMS.register("holo_projector", () -> new BlockItem(HOLO_PROJECTOR.get(), new Item.Properties()));
    public static final DeferredItem<Item> BLUEPRINT_DNA_SYNTHESIZER = registerBlueprint("dna_synthesizer");
    public static final DeferredItem<Item> BLUEPRINT_DNA_MIXER = registerBlueprint("dna_mixer");
    public static final DeferredItem<Item> BLUEPRINT_DNA_HYBRIDIZER = registerBlueprint("dna_hybridizer");
    public static final DeferredItem<Item> BLUEPRINT_DNA_INTEGRATOR = registerBlueprint("dna_integrator");
    public static final DeferredItem<Item> BLUEPRINT_BIOREACTOR = registerBlueprint("bioreactor");

    static {
        BLOCK_TAB_CONTENT.add(HOLO_PROJECTOR_ITEM);
    }

    private ModContent() {
    }
}
