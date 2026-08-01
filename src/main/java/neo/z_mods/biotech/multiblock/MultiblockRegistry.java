package neo.z_mods.biotech.multiblock;

import neo.z_mods.biotech.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MultiblockRegistry {
    private static final Map<String, MultiblockDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(platform("dna_synthesizer", ModContent.DNA_SYNTHESIZER, ModContent.FUSION_CHAMBER));
        register(platform("dna_mixer", ModContent.DNA_MIXER, ModContent.FUSION_CHAMBER));
        register(platform("dna_hybridizer", ModContent.DNA_HYBRIDIZER, ModContent.FUSION_CHAMBER));
        register(integrator());
        register(bioreactor());
    }

    private static MultiblockDefinition platform(String id, java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> controller,
                                                  java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> chamber) {
        List<MultiblockDefinition.Requirement> req = new ArrayList<>();
        req.add(new MultiblockDefinition.Requirement(BlockPos.ZERO, controller));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                req.add(new MultiblockDefinition.Requirement(new BlockPos(x, 0, z), ModContent.CARBON_BLOCK));
            }
        }
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 1, 0), chamber));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(-1, 1, -1), ModContent.DNA_STABILIZER));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(1, 1, -1), ModContent.DNA_STABILIZER));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(-1, 1, 1), ModContent.ENERGY_PORT));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(1, 1, 1), ModContent.ENERGY_PORT));
        return new MultiblockDefinition(id, List.copyOf(req));
    }

    private static MultiblockDefinition integrator() {
        List<MultiblockDefinition.Requirement> req = new ArrayList<>();
        req.add(new MultiblockDefinition.Requirement(BlockPos.ZERO, ModContent.DNA_INTEGRATOR));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                req.add(new MultiblockDefinition.Requirement(new BlockPos(x, 0, z), (Math.abs(x) == 2 || Math.abs(z) == 2) ? ModContent.CARBON_BLOCK : ModContent.REINFORCED_GLASS));
            }
        }
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 1, 0), ModContent.PLAYER_CAPSULE));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(-2, 1, 0), ModContent.DNA_STABILIZER));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(2, 1, 0), ModContent.DNA_STABILIZER));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 1, -2), ModContent.ENERGY_PORT));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 1, 2), ModContent.ENERGY_PORT));
        return new MultiblockDefinition("dna_integrator", List.copyOf(req));
    }

    private static MultiblockDefinition bioreactor() {
        List<MultiblockDefinition.Requirement> req = new ArrayList<>();
        req.add(new MultiblockDefinition.Requirement(BlockPos.ZERO, ModContent.BIOREACTOR));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                boolean port = x == 0 && Math.abs(z) == 1;
                req.add(new MultiblockDefinition.Requirement(
                        new BlockPos(x, 0, z),
                        port ? ModContent.ENERGY_PORT : ModContent.ENERGY_STORAGE
                ));
            }
        }
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 1, 0), ModContent.BIO_STORAGE));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 2, 0), ModContent.REINFORCED_GLASS));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(0, 3, 0), ModContent.CONTROLLER));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(-1, 1, 0), ModContent.BIO_DISTRIBUTOR));
        req.add(new MultiblockDefinition.Requirement(new BlockPos(1, 1, 0), ModContent.BIO_DISTRIBUTOR));
        return new MultiblockDefinition("bioreactor", List.copyOf(req));
    }

    private static void register(MultiblockDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static MultiblockDefinition get(String id) {
        return DEFINITIONS.get(id);
    }

    public static List<MultiblockDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static MultiblockDefinition fromBlueprint(ItemStack stack) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!path.startsWith("blueprint_")) return null;
        return get(path.substring("blueprint_".length()));
    }

    public record Match(MultiblockDefinition definition, BlockPos origin, net.minecraft.core.Direction facing) {
        public BlockPos controllerPos() {
            return definition.worldPosition(origin, facing, BlockPos.ZERO);
        }
    }

    /** Ищет полностью собранную структуру даже если ключом кликнули не по контроллеру. */
    public static Match findCompletedAt(net.minecraft.world.level.Level level, BlockPos clickedPos) {
        net.minecraft.world.level.block.state.BlockState clickedState = level.getBlockState(clickedPos);
        for (MultiblockDefinition definition : DEFINITIONS.values()) {
            for (net.minecraft.core.Direction facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (MultiblockDefinition.Requirement requirement : definition.requirements()) {
                    if (!clickedState.is(requirement.block().get())) {
                        continue;
                    }
                    BlockPos rotated = definition.rotateOffset(facing, requirement.offset());
                    BlockPos origin = clickedPos.subtract(rotated);
                    if (definition.validate(level, origin, facing)) {
                        return new Match(definition, origin, facing);
                    }
                }
            }
        }
        return null;
    }

    private MultiblockRegistry() {
    }
}
