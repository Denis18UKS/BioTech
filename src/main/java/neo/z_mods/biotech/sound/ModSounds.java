package neo.z_mods.biotech.sound;

import neo.z_mods.biotech.BioTech;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Звуковые события, используемые только атмосферой биовыброса. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
            BuiltInRegistries.SOUND_EVENT,
            BioTech.MODID
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> VIBROS_FLASH = SOUND_EVENTS.register(
            "vibros_flash",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "vibros_flash")
            )
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> VIBROS_RUMBLE = SOUND_EVENTS.register(
            "vibros_rumble",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "vibros_rumble")
            )
    );

    private ModSounds() {
    }
}
