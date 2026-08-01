package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.DnaBlacklistConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DnaBlacklistRequestPacket() implements CustomPacketPayload {
    public static final Type<DnaBlacklistRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "dna_blacklist_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DnaBlacklistRequestPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public void encode(RegistryFriendlyByteBuf buffer, DnaBlacklistRequestPacket packet) { }
        @Override public DnaBlacklistRequestPacket decode(RegistryFriendlyByteBuf buffer) { return new DnaBlacklistRequestPacket(); }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DnaBlacklistRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                boolean canEdit = player.hasPermissions(2) || player.getServer().isSingleplayer();
                PacketDistributor.sendToPlayer(
                        player,
                        new DnaBlacklistSyncPacket(DnaBlacklistConfig.snapshot(), canEdit)
                );
            }
        });
    }
}
