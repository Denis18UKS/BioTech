package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashSet;
import java.util.Set;

public record DnaBlacklistSyncPacket(Set<String> excluded, boolean canEdit) implements CustomPacketPayload {
    public static final Type<DnaBlacklistSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "dna_blacklist_sync")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DnaBlacklistSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DnaBlacklistSyncPacket packet) {
            buffer.writeVarInt(packet.excluded().size());
            packet.excluded().forEach(buffer::writeUtf);
            buffer.writeBoolean(packet.canEdit());
        }

        @Override
        public DnaBlacklistSyncPacket decode(RegistryFriendlyByteBuf buffer) {
            int size = Math.min(buffer.readVarInt(), 4096);
            Set<String> values = new LinkedHashSet<>();
            for (int i = 0; i < size; i++) {
                values.add(buffer.readUtf(256));
            }
            return new DnaBlacklistSyncPacket(values, buffer.readBoolean());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DnaBlacklistSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDnaBlacklistData.update(packet.excluded(), packet.canEdit()));
    }
}
