package com.tonywww.slashblade_neomotions.combat;

import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

public final class GreatSwordStyle {
    public static final ResourceLocation ROOT = id(GreatSwordMove.READY);
    private static final Map<LivingEntity, Action> ACTIONS = new WeakHashMap<>();
    private GreatSwordStyle() {}

    /** No value retains the weak key. An action is bound to the exact held stack and start time. */
    private static final class Action {
        final ItemStack blade;
        final GreatSwordMove move;
        final long started;
        final float yaw;
        long chargeStarted;
        int level, pulses;
        GreatSwordMove queued;
        boolean sneaking, releasingSlashArt;
        Action(LivingEntity user, GreatSwordMove move, long started) {
            this.blade = user.getMainHandItem(); this.move = move; this.started = started;
            this.yaw = user.getYRot();
            this.chargeStarted = started;
            this.sneaking = BladePlatform.input(user).contains(InputCommand.SNEAK);
        }
    }

    public static ResourceLocation id(GreatSwordMove move) { return BladePlatform.id(move.path); }
    public static GreatSwordMove move(ResourceLocation id) {
        for (GreatSwordMove move : GreatSwordMove.values()) if (id(move).equals(id)) return move;
        return null;
    }
    public static boolean selected(ItemStack stack) { return BladePlatform.state(stack).map(s -> ROOT.equals(s.getComboRoot())).orElse(false); }

    public static boolean apply(ItemStack stack, boolean restore) {
        return StyleSelection.apply(stack, ROOT, restore);
    }

    public static ComboState build(GreatSwordMove move) {
        ComboState.Builder builder = ComboState.Builder.newInstance()
                .startAndEnd(0, move.duration * SourceAction.VMD_FRAMES_PER_TICK).speed(SourceAction.PLAYBACK_SPEED).priority(move == GreatSwordMove.READY ? 1000 : 100)
                .motionLoc(BladePlatform.id("motions/" + move.path + ".vmd"))
                .timeout(move == GreatSwordMove.READY || move.charging() || move.tackling() ? 0 : 500)
                .next(user -> next(user, move))
                .nextOfTimeout(user -> ComboStateRegistry.NONE.getId())
                .clickAction(user -> begin(user, move))
                .addTickAction(user -> tick(user, move));
        if (move.aerial()) builder.aerial();
        if (move == GreatSwordMove.READY) builder.loop();
        return builder.build();
    }

    private static ResourceLocation next(LivingEntity user, GreatSwordMove move) {
        ISlashBladeState state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null || state.isBroken() || state.isSealed()) return ComboStateRegistry.NONE.getId();
        // Native doChargeAction rejects an uninterrupted combo (next == current). Only unlock
        // it for the synchronous native release call; ordinary input stays locked in SA preparation.
        if (move.preparingSlashArt() && releasingSlashArt(user)) return ComboStateRegistry.NONE.getId();
        var input = BladePlatform.input(user);
        boolean right = input.contains(InputCommand.R_CLICK);
        if (move == GreatSwordMove.READY)
            return id(GreatSwordRules.entry(user.onGround(), right, input.contains(InputCommand.SNEAK), input.contains(InputCommand.BACK)));
        if (!user.onGround() && !move.aerial()) return id(move); // Do not start ground follow-ups in midair.
        if (move.aerial()) return id(move);
        if (move.charging() && !right && !user.isUsingItem()) return id(move);
        long elapsed = state.getElapsedTime(user);
        var next = GreatSwordRules.follow(move, elapsed, right, input.contains(InputCommand.SNEAK));
        if (!user.level().isClientSide() && !right && !move.charging() && next == move) {
            var action = action(user, state, move);
            var target = GreatSwordRules.follow(move, Long.MAX_VALUE, false, input.contains(InputCommand.SNEAK));
            if (action != null && elapsed >= GreatSwordRules.preFor(move, target)) action.queued = target;
        }
        return id(next);
    }

    private static void begin(LivingEntity user, GreatSwordMove move) {
        if (user.level().isClientSide()) return;
        ISlashBladeState state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null) return;
        Action previous = ACTIONS.get(user);
        Action next = new Action(user, move, state.getLastActionTime());
        if (move.preparingSlashArt()) {
            if (previous == null || previous.blade != user.getMainHandItem() || !previous.move.charging()
                    || previous.move.preparingSlashArt() || previous.move.stage() != move.stage()) {
                abort(user, state);
                return;
            }
            next.chargeStarted = previous.chargeStarted;
            next.sneaking = previous.sneaking;
        }
        ACTIONS.put(user, next);
    }

    private static Action action(LivingEntity user, ISlashBladeState state, GreatSwordMove move) {
        Action action = ACTIONS.get(user);
        if (action == null || action.blade != user.getMainHandItem() || action.move != move
                || action.started != state.getLastActionTime()) return null;
        return action;
    }

    private static void transition(LivingEntity user, ISlashBladeState state, GreatSwordMove next) {
        state.updateComboSeq(user, id(next));
    }

    public static void prepareSlashArt(LivingEntity user) {
        if (user.level().isClientSide() || !user.isAlive() || !user.onGround() || !user.isUsingItem()
                || user.getUseItem() != user.getMainHandItem() || !selected(user.getMainHandItem())) return;
        ISlashBladeState state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null || state.isBroken() || state.isSealed()) return;
        GreatSwordMove move = move(state.getComboSeq());
        if (move == null || !move.charging() || move.preparingSlashArt()) return;
        Action action = action(user, state, move);
        if (action == null || chargeElapsed(user, action) >= GreatSwordRules.AUTO_RELEASE_TICK) return;
        transition(user, state, GreatSwordMove.prepareSlashArt(move.stage()));
    }

    public static boolean releasingSlashArt(LivingEntity user) {
        ISlashBladeState state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null) return false;
        GreatSwordMove move = move(state.getComboSeq());
        Action action = move == null ? null : action(user, state, move);
        return move != null && move.preparingSlashArt() && action != null && action.releasingSlashArt;
    }

    private static long chargeElapsed(LivingEntity user, Action action) {
        return Math.max(0, user.level().getGameTime() - action.chargeStarted);
    }

    public static void release(LivingEntity user, ItemStack usedStack) {
        if (user.level().isClientSide()) return;
        if (usedStack != user.getMainHandItem() || !selected(usedStack)) { clear(user); return; }
        ISlashBladeState state = BladePlatform.state(usedStack).orElse(null);
        if (state == null) return;
        GreatSwordMove current = move(state.getComboSeq());
        if (current == null || !current.charging()) return;
        Action action = action(user, state, current);
        if (action == null || !user.isAlive() || state.isBroken() || state.isSealed() || !user.onGround()) {
            abort(user, state);
            return;
        }
        if (action.releasingSlashArt) return;
        if (current.preparingSlashArt()) {
            long elapsed = chargeElapsed(user, action);
            if (elapsed < state.getFullChargeTicks(user) || elapsed >= GreatSwordRules.AUTO_RELEASE_TICK) {
                abort(user, state);
                return;
            }
            action.releasingSlashArt = true;
            try {
                BladePlatform.releaseSlashArt(user, usedStack, (int) elapsed);
            } finally {
                action.releasingSlashArt = false;
                ACTIONS.remove(user, action);
                // A missing/unavailable/canceled SA must not leave a reusable armed state.
                if (state.getComboSeq().equals(id(current))) abort(user, state);
            }
            return;
        }
        int level = GreatSwordRules.chargeLevel(state.getElapsedTime(user));
        transition(user, state, GreatSwordMove.slash(current.stage()));
        Action next = action(user, state, GreatSwordMove.slash(current.stage()));
        if (next != null) next.level = level;
    }

    private static void abort(LivingEntity user, ISlashBladeState state) {
        ACTIONS.remove(user);
        state.updateComboSeq(user, ComboStateRegistry.NONE.getId());
    }

    private static void tick(LivingEntity user, GreatSwordMove move) {
        if (!(user.level() instanceof ServerLevel level) || move == GreatSwordMove.READY) return;
        ISlashBladeState state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null || !ROOT.equals(state.getComboRoot())) return;
        Action action = action(user, state, move);
        // A swapped/reloaded stack must never resume an attack or retain its charge bonus.
        if (action == null || !user.isAlive() || state.isBroken() || state.isSealed()) { abort(user, state); return; }
        long elapsed = state.getElapsedTime(user);
        if (move.charging()) {
            if (!user.onGround()) { abort(user, state); return; }
            boolean sneak = BladePlatform.input(user).contains(InputCommand.SNEAK);
            long charged = chargeElapsed(user, action);
            if (sneak && !action.sneaking && charged >= 2) {
                transition(user, state, GreatSwordMove.tackle(move.stage()));
                return;
            }
            action.sneaking = sneak;
            if (move.preparingSlashArt()) {
                // Never automatically spend an SA when the hold times out or use is interrupted.
                if (charged >= GreatSwordRules.AUTO_RELEASE_TICK || !user.isUsingItem()
                        || user.getUseItem() != action.blade) { abort(user, state); return; }
                Vec3 motion = user.getDeltaMovement();
                user.setDeltaMovement(motion.x * 0.25, motion.y, motion.z * 0.25);
                if (elapsed % 4 == 0) {
                    level.sendParticles(ParticleTypes.ENCHANT, user.getX(), user.getY() + 1, user.getZ(),
                            6, 0.3, 0.4, 0.3, 0.02);
                }
                int full = state.getFullChargeTicks(user);
                if (charged == full)
                    level.playSound(null, user.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, 1.8F);
                return;
            }
            if (elapsed >= GreatSwordRules.AUTO_RELEASE_TICK || (elapsed >= 2 && !user.isUsingItem())) {
                release(user, action.blade);
                return;
            }
            Vec3 motion = user.getDeltaMovement();
            user.setDeltaMovement(motion.x * 0.25, motion.y, motion.z * 0.25);
            if (elapsed == GreatSwordRules.CHARGE_1_TICK || elapsed == GreatSwordRules.CHARGE_2_TICK
                    || elapsed == GreatSwordRules.CHARGE_3_TICK) {
                level.sendParticles(ParticleTypes.CRIT,
                        user.getX(), user.getY() + 1, user.getZ(), 10, 0.35, 0.45, 0.35, 0.02);
                level.playSound(null, user.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                        0.6F, 0.6F + GreatSwordRules.chargeLevel(elapsed) * 0.4F);
            }
            return;
        }
        if (move.aerial() && user.onGround()) {
            transition(user, state, GreatSwordMove.LANDING);
            return;
        }
        SourceRootMotion.apply(user, move.source, elapsed, action.yaw);
        for (int i = 0; i < move.source.hits.size(); i++) {
            var hit = move.source.hits.get(i);
            if (elapsed < hit.tick() || (action.pulses & (1 << i)) != 0) continue;
            action.pulses |= 1 << i;
            boolean opener = move == GreatSwordMove.TRUE_SLASH && i == 0;
            var slash = AttackManager.doSlash(user, hit.roll(), false, !opener && action.level == 3,
                    (opener ? 0.35 : move.damage) * GreatSwordRules.chargeMultiplier(action.level));
            if (slash != null) slash.setXRot(hit.pitch());
            if (i == move.source.hits.size() - 1) BladePlatform.damageBlade(user);
            if (state.isBroken()) { abort(user, state); return; }
        }
        if (action.queued != null && user.onGround()
                && elapsed >= GreatSwordRules.cancelFor(move, action.queued)) {
            transition(user, state, action.queued);
            return;
        }
        // Native use() starts using the blade even when progressCombo rejects an input
        // during recovery. Consume that held input once the follow-up window opens.
        if (!move.aerial() && elapsed >= move.cancelTick && user.isUsingItem()
                && user.getUseItem() == action.blade && user.onGround()) {
            if (state.isBroken() || state.isSealed()) { abort(user, state); return; }
            var next = GreatSwordRules.follow(move, elapsed, true, false);
            if (next != move) transition(user, state, next);
        }
    }

    public static boolean armored(LivingEntity user) {
        ISlashBladeState state = BladePlatform.state(user.getMainHandItem()).orElse(null);
        if (state == null || state.isBroken() || state.isSealed() || !selected(user.getMainHandItem())) return false;
        GreatSwordMove move = move(state.getComboSeq());
        return move != null && move.tackling() && action(user, state, move) != null
                && state.getElapsedTime(user) < move.source.hyperArmorEndTick;
    }
    public static void clear(LivingEntity user) { ACTIONS.remove(user); }
    public static void clearAll() { ACTIONS.clear(); }
}
