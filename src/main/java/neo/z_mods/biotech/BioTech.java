package neo.z_mods.biotech;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(BioTech.MODID)
public class BioTech {
    public static final String MODID = "biotech";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static boolean VIBROS_ACTIVE = false;

    public BioTech(IEventBus modEventBus) {
        LOGGER.info("BioTech mod loading...");
        
        NeoForge.EVENT_BUS.register(new VibrosEventHandler());
        NeoForge.EVENT_BUS.register(new DimensionSystemHandler());
        NeoForge.EVENT_BUS.register(new GhostSyncHandler());
        
        LOGGER.info("BioTech mod loaded!");
    }
}