package com.tonywww.slashblade_neomotions.platform.client;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.animation.ActionAnimationPlayer;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.WeakHashMap;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
@EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
*///?}
public final class ClientAnimations {
    private static final Map<AbstractClientPlayer, KeyframeAnimationPlayer> ACTIVE = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, ResourceLocation> IDLE = new WeakHashMap<>();
    private ClientAnimations() {}
    static boolean hasStyleAnimation(AbstractClientPlayer player) {
        var active = ACTIVE.get(player);
        return active != null && active.isActive();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void motion(BladeMotionEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        var previous = ACTIVE.get(player);
        remove(player);
        var greatsword = GreatSwordStyle.move(event.getCombo());
        var dual = DualBladeStyle.move(event.getCombo());
        var switchaxe = SwitchAxeStyle.move(event.getCombo());
        if (greatsword == null && dual == null && switchaxe == null) return;
        int elapsed = 0;
        //? if >=1.21 {
        /*elapsed = (int) Math.max(0, player.level().getGameTime() - event.getActionTime());
        *///?}
        boolean hold = greatsword != null ? greatsword.charging() || greatsword.aerial()
                : dual == com.tonywww.slashblade_neomotions.combat.DualBladeMove.AERIAL
                    || dual == com.tonywww.slashblade_neomotions.combat.DualBladeMove.SA_PREPARE;
        play(player, BladePlatform.id(greatsword != null ? greatsword.path : dual != null ? dual.path : switchaxe.path), elapsed, previous, hold);
    }
    private static void remove(AbstractClientPlayer player) {
        IDLE.remove(player);
        var stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
        var previous = ACTIVE.remove(player);
        if (previous != null) {
            // NONE has no native player animation. Remove our canceled charge by identity so
            // a native SA animation installed earlier in this event keeps its own layer.
            stack.removeLayer(previous);
            previous.stop();
        }
    }
    private static void play(AbstractClientPlayer player, ResourceLocation id, int elapsed,
                             KeyframeAnimationPlayer previous, boolean hold) {
        Object loaded = PlayerAnimationRegistry.getAnimation(id);
        if (!(loaded instanceof KeyframeAnimation animation)) return;
        var stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
        stack.removeLayer(0);
        var playback = new ActionAnimationPlayer(animation, elapsed, previous, hold,
                com.tonywww.slashblade_neomotions.combat.SourceAction.get(id.getPath()).blendTicks);
        stack.addAnimLayer(0, playback);
        ACTIVE.put(player, playback);
    }
    // Match the root VMD whenever it is used, including hotbar selection and the
    // frozen final frames between the end of a motion and the native combo timeout.
    //? if forge {
    @SubscribeEvent public static void idle(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var entity = event.player;
    //?} else {
    /*@SubscribeEvent public static void idle(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
    *///?}
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (!player.isAlive() || !GreatSwordStyle.selected(player.getMainHandItem())
                && !DualBladeStyle.selected(player.getMainHandItem()) && !SwitchAxeStyle.selected(player.getMainHandItem())) {
            remove(player);
            return;
        }
        ResourceLocation root = BladePlatform.state(player.getMainHandItem()).map(s -> {
            boolean great = GreatSwordStyle.ROOT.equals(s.getComboRoot());
            boolean dual = DualBladeStyle.ROOT.equals(s.getComboRoot());
            boolean switchaxe = SwitchAxeStyle.owns(s.getComboRoot());
            if (!great && !dual && !switchaxe) return null;
            var gsMove = GreatSwordStyle.move(s.getComboSeq());
            var dbMove = DualBladeStyle.move(s.getComboSeq());
            var saMove = SwitchAxeStyle.move(s.getComboSeq());
            long elapsed = s.getElapsedTime(player);
            var combo = BladePlatform.combo(s.getComboSeq());
            boolean nativeEnded = gsMove == null && dbMove == null && saMove == null && combo != null
                    && elapsed * 50 > combo.getTimeoutMS()
                    && combo.getNextOfTimeout(player).equals(ComboStateRegistry.NONE.getId());
            boolean ready = s.getComboSeq().equals(ComboStateRegistry.NONE.getId())
                    || nativeEnded
                    || great && gsMove != null && !gsMove.charging() && !gsMove.aerial() && elapsed >= gsMove.duration
                    || dual && dbMove != null && dbMove != com.tonywww.slashblade_neomotions.combat.DualBladeMove.SA_PREPARE
                        && dbMove != com.tonywww.slashblade_neomotions.combat.DualBladeMove.AERIAL && elapsed >= dbMove.duration
                    || switchaxe && saMove != null && elapsed >= saMove.duration;
            return ready ? s.getComboRoot() : null;
        }).orElse(null);
        if (!player.isAlive()) root = null;
        if (root != null && !root.equals(IDLE.get(player))) {
            var previous = ACTIVE.get(player);
            remove(player);
            play(player, root, 0, previous, false);
            IDLE.put(player, root);
        } else if (root == null && IDLE.containsKey(player)) remove(player);
    }
}
