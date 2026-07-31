package neo.z_mods.biotech.registry;

import neo.z_mods.biotech.BioTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BioTech.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = TABS.register("blocks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.biotech.blocks"))
            .icon(() -> ModContent.DNA_ANALYZER.get().asItem().getDefaultInstance())
            .displayItems((parameters, output) -> ModContent.BLOCK_TAB_CONTENT.forEach(item -> output.accept(item.get())))
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS = TABS.register("items", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.biotech.items"))
            .icon(() -> ModContent.DNK_INJECTOR.get().getDefaultInstance())
            .displayItems((parameters, output) -> ModContent.ITEM_TAB_CONTENT.forEach(item -> output.accept(item.get())))
            .build());

    private ModCreativeTabs() {
    }
}
