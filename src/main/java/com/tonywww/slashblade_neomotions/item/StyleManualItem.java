package com.tonywww.slashblade_neomotions.item;

import com.tonywww.slashblade_neomotions.combat.StyleSelection;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Optional;

/** Main hand: manual, off hand: blade. Sneak to restore that blade's previous style. */
public final class StyleManualItem extends Item {
    private final ResourceLocation root;
    private final String equippedMessage;
    public StyleManualItem(ResourceLocation root, String equippedMessage) {
        super(new Properties().stacksTo(1));
        this.root = root; this.equippedMessage = equippedMessage;
    }

    @Override public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new ManualMovesTooltip(SwitchAxeStyle.owns(root) ? "sa" : GreatSwordStyle.ROOT.equals(root) ? "gs" : "db"));
    }

    //? if <1.21 {
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
    //?} else {
    /*@Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
    *///?}
        lines.add(Component.translatable("tooltip.slashblade_neomotions.manual.keys").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.slashblade_neomotions.manual.timing").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.slashblade_neomotions.manual.equip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack manual = player.getItemInHand(hand);
        ItemStack blade = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!level.isClientSide()) {
            boolean restored = player.isShiftKeyDown();
            boolean success = StyleSelection.apply(blade, root, restored);
            if (success) StyleSelection.clear(player);
            player.displayClientMessage(Component.translatable("message.slashblade_neomotions."
                    + (success ? (restored ? "restored" : equippedMessage) : "manual_hint")), true);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return InteractionResultHolder.sidedSuccess(manual, level.isClientSide());
    }
}

