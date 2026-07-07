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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

public class SostIZMEventHandler {
    public static boolean CONVERGED = false;
    public static boolean TOUCHING = false;
    private static final Map<UUID, Map<UUID, Integer>> SYNCS = new HashMap<>();

    public static boolean sameDimension(ServerPlayer a, ServerPlayer b) {
        return a.level().dimension().equals(b.level().dimension());
    }

    public static boolean canMeet(ServerPlayer a, ServerPlayer b) {
        if (!sameDimension(a, b)) return false;
        if (CONVERGED) return true;
        Map<UUID, Integer> list = SYNCS.get(a.getUUID());
        if (list != null && list.containsKey(b.getUUID())) return true;
        // Проверяем обратную связь
        Map<UUID, Integer> list2 = SYNCS.get(b.getUUID());
        return list2 != null && list2.containsKey(a.getUUID());
    }

    public static boolean isGhost(ServerPlayer viewer, ServerPlayer target) {
        if (!TOUCHING) return false;
        if (!sameDimension(viewer, target)) return false;
        return !canMeet(viewer, target);
    }

    public static boolean shouldHide(ServerPlayer viewer, ServerPlayer target) {
        if (!TOUCHING && !CONVERGED) {
            // Измерения разошлись и не соприкасаются - скрываем всех кто не в синхронизации
            return !canMeet(viewer, target);
        }
        return false;
    }

    private static boolean takePayment(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.NETHERITE_INGOT)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("izm")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("converge")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .executes(ctx -> {
                                    CONVERGED = StringArgumentType.getString(ctx, "state").equalsIgnoreCase("on");
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            CONVERGED ? "§aИзмерения сошлись" : "§cИзмерения разделены"), true);
                                    return 1;
                                })))
                .then(Commands.literal("touch")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .executes(ctx -> {
                                    TOUCHING = StringArgumentType.getString(ctx, "state").equalsIgnoreCase("on");
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            TOUCHING ? "§5Измерения соприкасаются" : "§7Измерения разошлись"), true);
                                    return 1;
                                })))
                .then(Commands.literal("sync")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(ctx, "player");
                                            int time = IntegerArgumentType.getInteger(ctx, "seconds");
                                            ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            if (target == null) {
                                                sender.sendSystemMessage(Component.literal("§cИгрок не существует"));
                                                return 0;
                                            }
                                            if (!takePayment(sender)) {
                                                sender.sendSystemMessage(Component.literal("§cНужен незеритовый слиток"));
                                                return 0;
                                            }
                                            SYNCS.computeIfAbsent(sender.getUUID(), k -> new HashMap<>())
                                                    .put(target.getUUID(), time);
                                            sender.sendSystemMessage(Component.literal(
                                                    "§aСвязь создана на " + time + " секунд"));
                                            return 1;
                                        }))))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    """
                                    §e=== ИЗМЕРЕНИЯ ===
                                    
                                    Сошлись: %s
                                    Соприкасаются: %s
                                    Активных связей: %s
                                    """.formatted(CONVERGED, TOUCHING, SYNCS.size())), false);
                            return 1;
                        })));
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        Iterator<Map.Entry<UUID, Map<UUID, Integer>>> it = SYNCS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<UUID, Integer>> entry = it.next();
            entry.getValue().replaceAll((uuid, time) -> time - 1);
            entry.getValue().entrySet().removeIf(e -> e.getValue() <= 0);
            if (entry.getValue().isEmpty()) it.remove();
        }
    }
}