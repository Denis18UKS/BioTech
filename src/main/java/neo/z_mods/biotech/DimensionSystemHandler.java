package neo.z_mods.biotech;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

public class DimensionSystemHandler {
    public static boolean CONVERGED = false;
    public static boolean TOUCHING = false;
    public static final Map<UUID, Map<UUID, Long>> SYNCS = new HashMap<>();

    public static boolean sameDimension(ServerPlayer a, ServerPlayer b) {
        return a.level().dimension().equals(b.level().dimension());
    }

    public static boolean canMeet(ServerPlayer a, ServerPlayer b) {
        if (!sameDimension(a, b)) return false;
        if (CONVERGED) return true;
        
        long now = System.currentTimeMillis();
        
        Map<UUID, Long> syncs = SYNCS.get(a.getUUID());
        if (syncs != null && syncs.containsKey(b.getUUID())) {
            if (now < syncs.get(b.getUUID())) return true;
            syncs.remove(b.getUUID());
        }
        
        syncs = SYNCS.get(b.getUUID());
        if (syncs != null && syncs.containsKey(a.getUUID())) {
            if (now < syncs.get(a.getUUID())) return true;
            syncs.remove(a.getUUID());
        }
        
        return false;
    }

    public static boolean isHiddenFor(ServerPlayer viewer, ServerPlayer target) {
        if (viewer == target) return false;
        if (!sameDimension(viewer, target)) return false;
        if (CONVERGED) return false;
        if (TOUCHING) return false;
        return !canMeet(viewer, target);
    }

    public static boolean isGhostFor(ServerPlayer viewer, ServerPlayer target) {
        if (viewer == target) return false;
        if (!sameDimension(viewer, target)) return false;
        if (CONVERGED) return false;
        if (!TOUCHING) return false;
        return !canMeet(viewer, target);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        
        d.register(Commands.literal("izm")
            .requires(s -> s.hasPermission(2))
            
            .then(Commands.literal("converge")
                .then(Commands.argument("state", StringArgumentType.word())
                    .executes(ctx -> {
                        boolean on = StringArgumentType.getString(ctx, "state").equalsIgnoreCase("on");
                        CONVERGED = on;
                        if (on) TOUCHING = false;
                        ctx.getSource().sendSuccess(() -> 
                            Component.literal(on ? "§a§lИзмерения СОШЛИСЬ! Все видят всех!" : "§cИзмерения разделены"), true);
                        return 1;
                    })))
            
            .then(Commands.literal("touch")
                .then(Commands.argument("state", StringArgumentType.word())
                    .executes(ctx -> {
                        boolean on = StringArgumentType.getString(ctx, "state").equalsIgnoreCase("on");
                        TOUCHING = on;
                        if (on) CONVERGED = false;
                        ctx.getSource().sendSuccess(() -> 
                            Component.literal(on ? "§5§lИзмерения СОПРИКАСАЮТСЯ! Видны призраки!" : "§7Измерения разошлись"), true);
                        return 1;
                    })))
            
            .then(Commands.literal("sync")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "player");
                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                            
                            ServerPlayer target = ctx.getSource().getServer()
                                .getPlayerList().getPlayerByName(name);
                            
                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("§cИгрок не найден!"));
                                return 0;
                            }
                            
                            boolean hasPayment = false;
                            for (int i = 0; i < sender.getInventory().getContainerSize(); i++) {
                                if (sender.getInventory().getItem(i).is(Items.NETHERITE_INGOT)) {
                                    sender.getInventory().getItem(i).shrink(1);
                                    hasPayment = true;
                                    break;
                                }
                            }
                            
                            if (!hasPayment) {
                                ctx.getSource().sendFailure(Component.literal("§cНужен НЕЗЕРИТОВЫЙ СЛИТОК!"));
                                return 0;
                            }
                            
                            long expiryTime = System.currentTimeMillis() + (seconds * 1000L);
                            SYNCS.computeIfAbsent(sender.getUUID(), k -> new HashMap<>())
                                .put(target.getUUID(), expiryTime);
                            
                            ctx.getSource().sendSuccess(() -> 
                                Component.literal("§a§lСвязь создана! §r§aВы видите §e" + name + 
                                    " §aещё §6" + seconds + " §aсекунд"), true);
                            
                            return 1;
                        }))))
            
            .then(Commands.literal("info")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        """
                        §e╔══════════════════════════╗
                        §e║ §6§lИЗМЕРЕНИЯ §e║
                        §e╠══════════════════════════╣
                        §e║ §fСошлись: §a%s
                        §e║ §fСоприкасаются: §d%s
                        §e║ §fАктивных связей: §b%d
                        §e╚══════════════════════════╝
                        """.formatted(CONVERGED ? "ДА" : "НЕТ", 
                                     TOUCHING ? "ДА" : "НЕТ", 
                                     SYNCS.size())), false);
                    return 1;
                })));
    }
}