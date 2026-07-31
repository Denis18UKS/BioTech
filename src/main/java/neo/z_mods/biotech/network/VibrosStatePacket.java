package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Синхронизирует фазу и таймер биовыброса с клиентом.
 */
public record VibrosStatePacket(
        int phase,
        int remainingTicks,
        int totalTicks,
        boolean automaticEvents
) implements CustomPacketPayload {

    public static final Type<VibrosStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "vibros_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VibrosStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, VibrosStatePacket packet) {
            buffer.writeVarInt(packet.phase());
            buffer.writeVarInt(packet.remainingTicks());
            buffer.writeVarInt(packet.totalTicks());
            buffer.writeBoolean(packet.automaticEvents());
        }

        @Override
        public VibrosStatePacket decode(RegistryFriendlyByteBuf buffer) {
            return new VibrosStatePacket(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean()
            );
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VibrosStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientVibrosData.update(
                packet.phase(),
                packet.remainingTicks(),
                packet.totalTicks(),
                packet.automaticEvents()
        ));
    }
}
