package neo.z_mods.biotech;

import com.mojang.logging.LogUtils;
import neo.z_mods.biotech.registry.ModBlockEntities;
import neo.z_mods.biotech.registry.ModContent;
import neo.z_mods.biotech.registry.ModCreativeTabs;
import neo.z_mods.biotech.registry.ModEffects;
import neo.z_mods.biotech.registry.ModMenus;
import neo.z_mods.biotech.sound.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BioTech.MODID)
public class BioTech {
    public static final String MODID = "biotech";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static boolean VIBROS_ACTIVE = false;

    public BioTech(IEventBus modEventBus) {
        LOGGER.info("BioTech mod loading...");
        DnaBlacklistConfig.load();

        ModContent.BLOCKS.register(modEventBus);
        ModContent.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);

        NeoForge.EVENT_BUS.register(new VibrosEventHandler());
        NeoForge.EVENT_BUS.register(new DimensionSystemHandler());
        NeoForge.EVENT_BUS.register(new GhostSyncHandler());
        NeoForge.EVENT_BUS.register(new DnaIntegrationHandler());

        LOGGER.info("BioTech mod loaded!");
    }
}
