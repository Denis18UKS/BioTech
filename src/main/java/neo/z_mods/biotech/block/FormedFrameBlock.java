package neo.z_mods.biotech.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Невидимая коллизия, сохраняющая настоящий размер собранной 3D-машины.
 * PORT отмечает позиции исходных энергетических разъёмов для кабелей.
 */
public class FormedFrameBlock extends Block {
    public static final BooleanProperty PORT = BooleanProperty.create("port");

    public FormedFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PORT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PORT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
