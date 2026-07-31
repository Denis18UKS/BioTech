package neo.z_mods.biotech.registry;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.menu.BioMachineMenu;
import neo.z_mods.biotech.menu.HoloProjectorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BioTech.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BioMachineMenu>> BIO_MACHINE = MENUS.register(
            "bio_machine",
            () -> new MenuType<>(BioMachineMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<HoloProjectorMenu>> HOLO_PROJECTOR = MENUS.register(
            "holo_projector",
            () -> new MenuType<>(HoloProjectorMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    private ModMenus() {
    }
}
