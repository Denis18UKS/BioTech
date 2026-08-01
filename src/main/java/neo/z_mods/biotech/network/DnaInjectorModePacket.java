package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import neo.z_mods.biotech.item.DnaInjectorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Серверно-авторитетное переключение режима инъектора по настраиваемому кейбинду. */
public record DnaInjectorModePacket(int handIndex) implements CustomPacketPayload {
    public static final Type<DnaInjectorModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "dna_injector_mode")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DnaInjectorModePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DnaInjectorModePacket packet) {
            buffer.writeVarInt(packet.handIndex());
        }

        @Override
        public DnaInjectorModePacket decode(RegistryFriendlyByteBuf buffer) {
            return new DnaInjectorModePacket(buffer.readVarInt());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DnaInjectorModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            InteractionHand hand = packet.handIndex() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof DnaInjectorItem)) {
                return;
            }
            DnaInjectorItem.Mode mode = DnaInjectorItem.cycleMode(stack);
            player.displayClientMessage(
                    Component.literal("Режим ДНК-инъектора: " + mode.displayName())
                            .withStyle(mode == DnaInjectorItem.Mode.EXTRACT
                                    ? ChatFormatting.GREEN
                                    : ChatFormatting.AQUA),
                    true
            );
        });
    }
}
