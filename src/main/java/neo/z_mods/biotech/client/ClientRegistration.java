package neo.z_mods.biotech.client;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.registry.ModBlockEntities;
import neo.z_mods.biotech.registry.ModMenus;
import neo.z_mods.biotech.client.screen.BioMachineScreen;
import neo.z_mods.biotech.client.screen.HoloProjectorScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = BioTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRegistration {
    public static final ModelResourceLocation DNK_INJECTOR_HAND_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "item/dnk_injector_hand")
    );

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HOLO_PROJECTOR.get(), HologramProjectorRenderer::new);
    }


    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BIO_MACHINE.get(), BioMachineScreen::new);
        event.register(ModMenus.HOLO_PROJECTOR.get(), HoloProjectorScreen::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(DNK_INJECTOR_HAND_MODEL);
    }

    private ClientRegistration() {
    }
}
