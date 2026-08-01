package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.DnaBlacklistConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashSet;
import java.util.Set;

public record DnaBlacklistUpdatePacket(Set<String> excluded) implements CustomPacketPayload {
    public static final Type<DnaBlacklistUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "dna_blacklist_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DnaBlacklistUpdatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DnaBlacklistUpdatePacket packet) {
            buffer.writeVarInt(packet.excluded().size());
            packet.excluded().forEach(buffer::writeUtf);
        }

        @Override
        public DnaBlacklistUpdatePacket decode(RegistryFriendlyByteBuf buffer) {
            int size = Math.min(buffer.readVarInt(), 4096);
            Set<String> values = new LinkedHashSet<>();
            for (int i = 0; i < size; i++) {
                values.add(buffer.readUtf(256));
            }
            return new DnaBlacklistUpdatePacket(values);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DnaBlacklistUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean canEdit = player.hasPermissions(2) || player.getServer().isSingleplayer();
            if (!canEdit) {
                player.displayClientMessage(
                        Component.literal("Недостаточно прав для изменения списка ДНК").withStyle(ChatFormatting.RED),
                        false
                );
                return;
            }
            DnaBlacklistConfig.replace(packet.excluded());
            PacketDistributor.sendToPlayer(
                    player,
                    new DnaBlacklistSyncPacket(DnaBlacklistConfig.snapshot(), true)
            );
            player.displayClientMessage(
                    Component.literal("Список исключений ДНК сохранён").withStyle(ChatFormatting.GREEN),
                    false
            );
        });
    }
}
