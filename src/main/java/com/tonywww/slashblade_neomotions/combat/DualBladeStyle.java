package com.tonywww.slashblade_neomotions.combat;

import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.WeakHashMap;
import static com.tonywww.slashblade_neomotions.combat.DualBladeMove.*;

public final class DualBladeStyle {
    public static final ResourceLocation ROOT = id(READY);
    private static final Map<LivingEntity, Action> ACTIONS = new WeakHashMap<>();
    private DualBladeStyle() {}
    private static final class Action {
        final ItemStack blade;
        final DualBladeMove move;
        final long started;
        final float yaw;
        int pulses;
        boolean moved, releasingSlashArt;
        Action(LivingEntity user, DualBladeMove move, long started) {
            this.blade = user.getMainHandItem(); this.move = move; this.started = started;
            this.yaw = user.getYRot();
        }
    }
    public static ResourceLocation id(DualBladeMove move) { return BladePlatform.id(move.path); }
    public static DualBladeMove move(ResourceLocation id) {
        for (var move : values()) if (id(move).equals(id)) return move;
        return null;
    }
    public static boolean selected(ItemStack blade) { return BladePlatform.state(blade).map(s -> ROOT.equals(s.getComboRoot())).orElse(false); }
    public static boolean apply(ItemStack blade, boolean restore) { return StyleSelection.apply(blade, ROOT, restore); }
    public static ComboState build(DualBladeMove move) {
        var builder = ComboState.Builder.newInstance().startAndEnd(0, move.duration * SourceAction.VMD_FRAMES_PER_TICK)
                .speed(SourceAction.PLAYBACK_SPEED).priority(move == READY ? 1000 : 100)
                .motionLoc(BladePlatform.id("motions/" + move.path + ".vmd"))
                .timeout(move == READY || move == SA_PREPARE ? 0 : 350)
                .next(user -> next(user, move)).nextOfTimeout(user -> ComboStateRegistry.NONE.getId())
                .clickAction(user -> begin(user, move)).addTickAction(user -> tick(user, move));
        if (move == AERIAL) builder.aerial();
        if (move == READY) builder.loop();
        return builder.build();
    }
    private static ResourceLocation next(LivingEntity user, DualBladeMove move) {
        var state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null || state.isBroken() || state.isSealed()) return ComboStateRegistry.NONE.getId();
        if (move == SA_PREPARE && releasingSlashArt(user)) return ComboStateRegistry.NONE.getId();
        var input = BladePlatform.input(user);
        boolean right = input.contains(InputCommand.R_CLICK), sneak = input.contains(InputCommand.SNEAK);
        boolean forward = input.contains(InputCommand.FORWARD), back = input.contains(InputCommand.BACK);
        if (move == READY) return id(DualBladeRules.entry(user.onGround(), right, sneak, forward, back));
        if (!user.onGround() && move != AERIAL) return id(move);
        return id(DualBladeRules.follow(move, state.getElapsedTime(user), right, sneak, forward, back));
    }
    private static void begin(LivingEntity user, DualBladeMove move) {
        if (user.level().isClientSide()) return;
        var state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state != null) ACTIONS.put(user, new Action(user, move, state.getLastActionTime()));
    }
    private static Action action(LivingEntity user, ISlashBladeState state, DualBladeMove move) {
        var action = ACTIONS.get(user);
        return action != null && action.blade == user.getMainHandItem() && action.move == move
                && action.started == state.getLastActionTime() ? action : null;
    }
    private static void abort(LivingEntity user, ISlashBladeState state) {
        clear(user);
        state.updateComboSeq(user, ComboStateRegistry.NONE.getId());
    }
    private static void tick(LivingEntity user, DualBladeMove move) {
        if (!(user.level() instanceof ServerLevel level) || move == READY) return;
        var state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null || !ROOT.equals(state.getComboRoot())) return;
        var action = action(user, state, move);
        if (action == null || !user.isAlive() || state.isBroken() || state.isSealed()) { abort(user, state); return; }
        long elapsed = state.getElapsedTime(user);
        if (move == SA_PREPARE) {
            if (!user.onGround() || !user.isUsingItem() || user.getUseItem() != action.blade
                    || elapsed >= DualBladeRules.SA_TIMEOUT) { abort(user, state); return; }
            int full = state.getFullChargeTicks(user);
            if (elapsed % 4 == 0 || elapsed == full || elapsed == full + SlashArts.getJustReceptionSpan(user)) {
                level.sendParticles(ParticleTypes.ENCHANT, user.getX(), user.getY() + 1, user.getZ(), 4, .3, .3, .3, .02);
            }
            if (elapsed == full) level.playSound(null, user.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, .6F, 1.8F);
            return;
        }
        if (move == AERIAL && user.onGround()) { state.updateComboSeq(user, id(LANDING)); return; }
        SourceRootMotion.apply(user, move.source, elapsed, action.yaw);
        for (int i = 0; i < move.strikes.size(); i++) {
            var strike = move.strikes.get(i);
            if (elapsed < strike.tick() || (action.pulses & (1 << i)) != 0) continue;
            action.pulses |= 1 << i;
            // Both hands belong to the same blade stack, preserving native damage, events and ownership.
            var slash = AttackManager.doSlash(user, strike.roll(), false, false, strike.damage());
            if (slash != null) slash.setXRot(strike.pitch());
            if (i == 0) BladePlatform.damageBlade(user);
            if (state.isBroken()) { abort(user, state); return; }
        }
    }
    public static boolean releasingSlashArt(LivingEntity user) {
        var state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        var action = state == null ? null : action(user, state, SA_PREPARE);
        return action != null && action.releasingSlashArt;
    }
    public static void release(LivingEntity user, ItemStack blade) {
        if (user.level().isClientSide()) return;
        if (blade != user.getMainHandItem() || !selected(blade)) { clear(user); return; }
        var state = BladePlatform.state(blade).orElse(null);
        if (state == null || !state.getComboSeq().equals(id(SA_PREPARE))) return;
        var action = action(user, state, SA_PREPARE);
        long elapsed = state.getElapsedTime(user);
        if (action == null || !user.isAlive() || !user.onGround() || state.isBroken() || state.isSealed()
                || elapsed < state.getFullChargeTicks(user) || elapsed >= DualBladeRules.SA_TIMEOUT) { abort(user, state); return; }
        if (action.releasingSlashArt) return;
        action.releasingSlashArt = true;
        try { BladePlatform.releaseSlashArt(user, blade, (int) elapsed); }
        finally {
            action.releasingSlashArt = false;
            clear(user);
            if (state.getComboSeq().equals(id(SA_PREPARE))) abort(user, state);
        }
    }
    public static void clear(LivingEntity user) { ACTIONS.remove(user); }
    public static void clearAll() { ACTIONS.clear(); }
}
