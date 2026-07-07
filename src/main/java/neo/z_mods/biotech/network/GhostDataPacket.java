package neo.z_mods.biotech.network;

import neo.z_mods.biotech.BioTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record GhostDataPacket(Set<UUID> hidden, Set<UUID> ghosts) implements CustomPacketPayload {
    
    public static final Type<GhostDataPacket> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath(BioTech.MODID, "ghost_data"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, GhostDataPacket> STREAM_CODEC = 
        new StreamCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buf, GhostDataPacket packet) {
                buf.writeInt(packet.hidden.size());
                for (UUID uuid : packet.hidden) {
                    buf.writeUUID(uuid);
                }
                buf.writeInt(packet.ghosts.size());
                for (UUID uuid : packet.ghosts) {
                    buf.writeUUID(uuid);
                }
            }
            
            @Override
            public GhostDataPacket decode(RegistryFriendlyByteBuf buf) {
                int hiddenSize = buf.readInt();
                Set<UUID> hidden = new HashSet<>();
                for (int i = 0; i < hiddenSize; i++) {
                    hidden.add(buf.readUUID());
                }
                
                int ghostSize = buf.readInt();
                Set<UUID> ghosts = new HashSet<>();
                for (int i = 0; i < ghostSize; i++) {
                    ghosts.add(buf.readUUID());
                }
                
                return new GhostDataPacket(hidden, ghosts);
            }
        };
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(final GhostDataPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientGhostData.HIDDEN_PLAYERS.clear();
            ClientGhostData.HIDDEN_PLAYERS.addAll(packet.hidden);
            ClientGhostData.GHOST_PLAYERS.clear();
            ClientGhostData.GHOST_PLAYERS.addAll(packet.ghosts);
        });
    }
}