package com.tonywww.slashblade_neomotions.platform.client;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.DualBladeMove;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordMove;
import com.tonywww.slashblade_neomotions.combat.GreatSwordRules;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.combat.SourceAction;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import com.tonywww.slashblade_neomotions.platform.ClientConfig;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
*///?}
public final class MoveInfoDisplay {
    private static ClientLevel trackedLevel;
    private static ItemStack trackedBlade = ItemStack.EMPTY;
    private static ResourceLocation trackedCombo;
    private static GreatSwordMove trackedGreat;
    private static long actionStarted, chargeStarted;
    private static Component shown;
    private MoveInfoDisplay() {}

    //? if forge {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
    //?} else {
    /*@SubscribeEvent public static void tick(ClientTickEvent.Post event) {
    *///?}
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        var state = BladePlatform.state(stack).orElse(null);
        boolean great = state != null && GreatSwordStyle.ROOT.equals(state.getComboRoot());
        boolean dual = state != null && DualBladeStyle.ROOT.equals(state.getComboRoot());
        boolean switchaxe = state != null && SwitchAxeStyle.owns(state.getComboRoot());
        if (player == null || !player.isAlive() || state == null || state.isBroken() || state.isSealed()
                || !great && !dual && !switchaxe) {
            clear(mc);
            trackedLevel = null; trackedBlade = ItemStack.EMPTY; trackedCombo = null; trackedGreat = null;
            return;
        }
        if (mc.isPaused()) return;
        var gs = great ? GreatSwordStyle.move(state.getComboSeq()) : null;
        long started = state.getLastActionTime();
        boolean sameBlade = trackedLevel == mc.level && trackedBlade == stack;
        if (!sameBlade || !state.getComboSeq().equals(trackedCombo) || actionStarted != started) {
            // Observe transitions even while hidden so enabling the preference during
            // SA preparation keeps the preceding charge's timer rather than restarting it.
            if (!(sameBlade && gs != null && gs.preparingSlashArt() && trackedGreat != null
                    && trackedGreat.charging() && trackedGreat.stage() == gs.stage())) chargeStarted = started;
            trackedLevel = mc.level; trackedBlade = stack; trackedCombo = state.getComboSeq();
            trackedGreat = gs; actionStarted = started;
        }
        if (!ClientConfig.SHOW_MOVE_INFO.get() || mc.options.hideGui || player.isSpectator()) {
            clear(mc);
            return;
        }
        var message = switchaxe ? describeSwitchAxe(player, state) : describe(player, state, great);
        if (!Objects.equals(message, shown) || player.tickCount % 20 == 0) {
            mc.gui.setOverlayMessage(message, false);
            shown = message;
        }
    }

    private static Component describeSwitchAxe(LocalPlayer player, ISlashBladeState state) {
        var move = SwitchAxeStyle.move(state.getComboSeq());
        long elapsed = Math.max(0, state.getElapsedTime(player));
        boolean ready = move == null || move.ready() || elapsed >= move.duration;
        String name = ready ? state.getComboRoot().getPath() : move.path;
        String phase = ready ? "recovery" : elapsed >= move.cancelTick ? "chain" : "windup";
        if (!ready) for (var hit : move.source.hits)
            if (elapsed * 3 >= hit.beginFrame() && elapsed * 3 <= hit.endFrame()) phase = "attack";
        return Component.translatable("hud.slashblade_neomotions.current_move",
                Component.translatable("hud.slashblade_neomotions.switchaxe"),
                Component.translatable("move.slashblade_neomotions." + name),
                ready ? Component.translatable("hud.slashblade_neomotions.ready") :
                        Component.translatable("hud.slashblade_neomotions.progress",
                                Component.translatable("hud.slashblade_neomotions.phase." + phase),
                                Math.min(elapsed, move.duration), move.duration));
    }
    private static Component describe(LocalPlayer player, ISlashBladeState state, boolean great) {
        long elapsed = Math.max(0, state.getElapsedTime(player));
        var gs = great ? GreatSwordStyle.move(state.getComboSeq()) : null;
        var db = great ? null : DualBladeStyle.move(state.getComboSeq());
        if (gs != null && gs.preparingSlashArt() || db == DualBladeMove.SA_PREPARE) {
            long charged = gs != null ? Math.max(0, player.level().getGameTime() - chargeStarted) : elapsed;
            int full = state.getFullChargeTicks(player);
            boolean perfect = charged >= full && charged < full + SlashArts.getJustReceptionSpan(player);
            String status = charged < full ? "charging" : perfect ? "perfect" : "ready";
            return heading(great, gs != null ? gs.path : db.path,
                    Component.translatable("message.slashblade_neomotions.sa_charge",
                            state.getSlashArts().getDescription(), Math.min(charged, full), full,
                            Component.translatable("message.slashblade_neomotions.sa_" + status)))
                    .withStyle(perfect ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA);
        }
        if (gs != null && gs.charging()) return heading(true, gs.path,
                Component.translatable("hud.slashblade_neomotions.charge", GreatSwordRules.chargeLevel(elapsed),
                        Math.min(elapsed, GreatSwordRules.CHARGE_3_TICK), GreatSwordRules.CHARGE_3_TICK));
        boolean aerial = gs != null && gs.aerial() || db == DualBladeMove.AERIAL;
        SourceAction source = gs != null ? gs.source : db != null ? db.source : null;
        if (source != null && (gs == GreatSwordMove.READY || db == DualBladeMove.READY
                || !aerial && elapsed >= source.duration)) return ready(great);
        if (source == null) {
            var combo = BladePlatform.combo(state.getComboSeq());
            if (state.getComboSeq().equals(ComboStateRegistry.NONE.getId()) || combo == null
                    || elapsed * 50 > combo.getTimeoutMS()) return ready(great);
            return heading(great, "native_action", Component.translatable("hud.slashblade_neomotions.special"));
        }
        String phase = aerial && elapsed >= source.duration ? "air_hold"
                : elapsed >= source.cancelTick ? "chain" : source.hits.isEmpty() ? "recovery" : "windup";
        for (var hit : source.hits) {
            if (elapsed * 3 >= hit.beginFrame() && elapsed * 3 <= hit.endFrame()) { phase = "attack"; break; }
            if (elapsed * 3 > hit.endFrame() && elapsed < source.cancelTick) phase = "recovery";
        }
        return heading(great, gs != null ? gs.path : db.path,
                Component.translatable("hud.slashblade_neomotions.progress",
                        Component.translatable("hud.slashblade_neomotions.phase." + phase),
                        Math.min(elapsed, source.duration), source.duration));
    }
    private static Component ready(boolean great) {
        return heading(great, great ? "greatsword" : "dualblade", Component.translatable("hud.slashblade_neomotions.ready"));
    }
    private static net.minecraft.network.chat.MutableComponent heading(boolean great, String path, Component details) {
        return Component.translatable("hud.slashblade_neomotions.current_move",
                Component.translatable("hud.slashblade_neomotions." + (great ? "greatsword" : "dualblade")),
                Component.translatable("move.slashblade_neomotions." + path), details);
    }
    private static void clear(Minecraft mc) {
        if (shown != null) mc.gui.setOverlayMessage(Component.empty(), false);
        shown = null;
    }
}
