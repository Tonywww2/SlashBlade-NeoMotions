package com.tonywww.slashblade_neomotions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.combat.StyleSelection;
import com.tonywww.slashblade_neomotions.platform.ModContent;
import com.tonywww.slashblade_neomotions.platform.PrototypeBlades;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class NeoMotionsCommands {
    private NeoMotionsCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("neomotions")
                .then(Commands.literal("help").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.slashblade_neomotions.help"), false);
                    return 1;
                }))
                .then(Commands.literal("style").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("switchaxe").executes(ctx -> apply(ctx.getSource(), SwitchAxeStyle.AXE_ROOT, false)))
                        .then(Commands.literal("greatsword").executes(ctx -> apply(ctx.getSource(), GreatSwordStyle.ROOT, false)))
                        .then(Commands.literal("dualblade").executes(ctx -> apply(ctx.getSource(), DualBladeStyle.ROOT, false)))
                        .then(Commands.literal("restore").executes(ctx -> apply(ctx.getSource(), GreatSwordStyle.ROOT, true))))
                .then(Commands.literal("blade").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("switchaxe").executes(ctx -> blade(ctx.getSource(), PrototypeBlades.SWITCH_AXE)))
                        .then(Commands.literal("greatsword").executes(ctx -> blade(ctx.getSource(), PrototypeBlades.GREAT_SWORD)))
                        .then(Commands.literal("dualblade").executes(ctx -> blade(ctx.getSource(), PrototypeBlades.DUAL_BLADE))))
                .then(Commands.literal("manual").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("switchaxe").executes(ctx -> manual(ctx.getSource(), ModContent.SWITCH_AXE_MANUAL.get())))
                        .executes(ctx -> manual(ctx.getSource(), ModContent.GREAT_SWORD_MANUAL.get()))
                        .then(Commands.literal("greatsword").executes(ctx -> manual(ctx.getSource(), ModContent.GREAT_SWORD_MANUAL.get())))
                        .then(Commands.literal("dualblade").executes(ctx -> manual(ctx.getSource(), ModContent.DUAL_BLADE_MANUAL.get())))));
    }
    private static int blade(CommandSourceStack source, ResourceLocation name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var stack = PrototypeBlades.create(player.level().registryAccess(), name);
        if (stack.isEmpty()) return 0;
        var displayName = stack.getHoverName();
        if (!player.addItem(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.translatable("message.slashblade_neomotions.prototype_given", displayName), false);
        return 1;
    }
    private static int manual(CommandSourceStack source, Item item) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var stack = item.getDefaultInstance();
        if (!player.addItem(stack)) player.drop(stack, false);
        return 1;
    }
    private static int apply(CommandSourceStack source, ResourceLocation root, boolean restore) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!StyleSelection.apply(player.getMainHandItem(), root, restore)) {
            source.sendFailure(Component.translatable("message.slashblade_neomotions.command_hint"));
            return 0;
        }
        StyleSelection.clear(player);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        source.sendSuccess(() -> Component.translatable("message.slashblade_neomotions."
                + (restore ? "restored" : SwitchAxeStyle.owns(root) ? "switchaxe_equipped" : root.equals(DualBladeStyle.ROOT) ? "dual_equipped" : "equipped")), false);
        return 1;
    }
}

