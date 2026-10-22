package com.tonywww.slashblade_neomotions.platform;

import com.tonywww.slashblade_neomotions.command.NeoMotionsCommands;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.combat.StyleSelection;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
*///?}

public final class CommonEvents {
    private CommonEvents() {}
    public static void register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.register(CommonEvents.class);
        //?} else {
        /*NeoForge.EVENT_BUS.register(CommonEvents.class);
        *///?}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) { NeoMotionsCommands.register(event.getDispatcher()); }
    @SubscribeEvent public static void input(InputCommandEvent event) {
        // Vanilla does not send an attack action while using an item. SlashBlade still syncs L_DOWN.
        if (event.getCurrent().contains(InputCommand.R_DOWN)
                && event.getCurrent().contains(InputCommand.L_DOWN) && !event.getOld().contains(InputCommand.L_DOWN))
            GreatSwordStyle.prepareSlashArt(event.getEntity());
    }
    //? if forge {
    @SubscribeEvent public static void handBinding(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.player.level().isClientSide()) return;
        SwitchAxeStyle.validate(event.player);
    }
    //?} else {
    /*@SubscribeEvent public static void handBinding(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) SwitchAxeStyle.validate(event.getEntity());
    }
    *///?}
    @SubscribeEvent public static void switchAxeUpdate(SlashBladeEvent.UpdateEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living)
            SwitchAxeStyle.inventoryTick(event.getBlade(), living, event.isSelected());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void switchAxeHit(SlashBladeEvent.HitEvent event) {
        SwitchAxeStyle.confirmedHit(event.getUser(), event.getTarget(), event.getBlade());
    }
    @SubscribeEvent public static void stopUsing(LivingEntityUseItemEvent.Stop event) {
        if (SwitchAxeStyle.selected(event.getItem())) { event.setCanceled(true); return; }
        if (DualBladeStyle.selected(event.getItem())) {
            event.setCanceled(true);
            DualBladeStyle.release(event.getEntity(), event.getItem());
            return;
        }
        if (!GreatSwordStyle.selected(event.getItem())) return;
        // We dispatch either the greatsword slash or one scoped native SA release, never both.
        event.setCanceled(true);
        GreatSwordStyle.release(event.getEntity(), event.getItem());
    }
    @SubscribeEvent public static void suppressSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        if (SwitchAxeStyle.selected(event.getEntityLiving().getMainHandItem())) event.setCanceled(true);
        if (DualBladeStyle.selected(event.getEntityLiving().getMainHandItem())
                && !DualBladeStyle.releasingSlashArt(event.getEntityLiving())) event.setCanceled(true);
        if (GreatSwordStyle.selected(event.getEntityLiving().getMainHandItem())
                && !GreatSwordStyle.releasingSlashArt(event.getEntityLiving())) event.setCanceled(true);
    }
    @SubscribeEvent public static void suppressNativeCharge(SlashBladeEvent.ChargeActionEvent event) {
        if (GreatSwordStyle.selected(event.getEntityLiving().getMainHandItem())
                || DualBladeStyle.selected(event.getEntityLiving().getMainHandItem())
                || SwitchAxeStyle.selected(event.getEntityLiving().getMainHandItem())) event.setCanceled(true);
    }
    //? if forge {
    @SubscribeEvent public static void tackleDamage(LivingHurtEvent event) {
    //?} else {
    /*@SubscribeEvent public static void tackleDamage(LivingIncomingDamageEvent event) {
    *///?}
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (GreatSwordStyle.armored(event.getEntity())) event.setAmount(event.getAmount()
                * com.tonywww.slashblade_neomotions.combat.SourceAction.tackleDamageRate());
    }
    @SubscribeEvent public static void tackleKnockback(LivingKnockBackEvent event) {
        if (GreatSwordStyle.armored(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { StyleSelection.clear(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { StyleSelection.clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { StyleSelection.clear(event.getEntity()); }
    @SubscribeEvent public static void shutdown(ServerStoppedEvent event) { StyleSelection.clearAll(); }
}

