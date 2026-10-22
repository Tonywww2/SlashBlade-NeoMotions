package com.tonywww.slashblade_neomotions.combat;

import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import static com.tonywww.slashblade_neomotions.combat.SwitchAxeMove.*;

public final class SwitchAxeStyle {
    public static final ResourceLocation AXE_ROOT = id(AXE_READY), SWORD_ROOT = id(SWORD_READY);
    private static final String DATA = "NeoMotionsSwitchAxe";
    private static final Map<LivingEntity, Action> ACTIONS = new WeakHashMap<>();
    private SwitchAxeStyle() {}
    private static final class Pulse {
        final EntitySlashEffect slash;
        final boolean amped;
        final long started;
        boolean confirmed;
        Pulse(EntitySlashEffect slash, boolean amped) { this.slash = slash; this.amped = amped; started = slash.level().getGameTime(); }
    }
    private static final class Action {
        final ItemStack blade;
        final SwitchAxeMove move;
        final long started;
        final float yaw;
        final ArrayList<Pulse> pending = new ArrayList<>();
        final Map<java.util.UUID, Long> hitTimes = new java.util.HashMap<>();
        int pulses;
        boolean modeChanged, enhanced;
        Action(LivingEntity user, SwitchAxeMove move, long started) {
            blade = user.getMainHandItem(); this.move = move; this.started = started; yaw = user.getYRot();
        }
    }
    public static ResourceLocation id(SwitchAxeMove move) { return BladePlatform.id(move.path); }
    public static SwitchAxeMove move(ResourceLocation id) {
        for (var move : values()) if (id(move).equals(id)) return move;
        return null;
    }
    public static boolean owns(ResourceLocation root) { return AXE_ROOT.equals(root) || SWORD_ROOT.equals(root); }
    public static boolean selected(ItemStack blade) { return BladePlatform.state(blade).map(s -> owns(s.getComboRoot())).orElse(false); }
    public static boolean sword(ItemStack blade) { return BladePlatform.state(blade).map(s -> SWORD_ROOT.equals(s.getComboRoot())).orElse(false); }
    public static SwitchAxeGauge gauge(ItemStack blade) {
        var tag = BladePlatform.data(blade, DATA);
        if (!tag.contains("Slash")) return SwitchAxeGauge.initial();
        return new SwitchAxeGauge(tag.getFloat("Slash"), tag.getFloat("Awake"), tag.getInt("AmpTicks"),
                tag.getInt("AxeTicks"), tag.getInt("Recovery"), tag.getInt("Decay"));
    }
    private static void save(ItemStack blade, SwitchAxeGauge gauge, long time) {
        var tag = new CompoundTag();
        tag.putFloat("Slash", gauge.slash()); tag.putFloat("Awake", gauge.awake());
        tag.putInt("AmpTicks", gauge.ampTicks()); tag.putInt("AxeTicks", gauge.axeTicks());
        tag.putInt("Recovery", gauge.recoveryTicks()); tag.putInt("Decay", gauge.decayTicks());
        tag.putLong("Time", time);
        BladePlatform.data(blade, DATA, tag);
    }
    private static SwitchAxeGauge advance(ItemStack blade, long now) {
        return advance(blade, now, sword(blade));
    }
    private static SwitchAxeGauge advance(ItemStack blade, long now, boolean sword) {
        var tag = BladePlatform.data(blade, DATA);
        long then = tag.contains("Time") ? tag.getLong("Time") : now;
        var gauge = gauge(blade).advance((int) Math.max(0, Math.min(36000, now - then)), sword);
        save(blade, gauge, now);
        return gauge;
    }
    /** Inventory updates include unequipped blades; timers cannot be paused by swapping slots. */
    public static void inventoryTick(ItemStack blade, LivingEntity owner, boolean selected) {
        if (owner.level().isClientSide()) return;
        validate(owner);
        if (!selected(blade)) return;
        if (owner.tickCount % 20 == 0) advance(blade, owner.level().getGameTime(), selected && sword(blade));
        if (!selected || blade != owner.getMainHandItem()) return;
        var state = BladePlatform.state(blade).orElse(null);
        if (state == null) return;
        var action = ACTIONS.get(owner);
        if (action != null && (action.blade != blade || action.started != state.getLastActionTime()
                || !id(action.move).equals(state.getComboSeq()) || !owner.isAlive() || state.isBroken() || state.isSealed())) clear(owner);
        // Depletion waits for the current action's source cancel boundary.
        if (sword(blade) && gauge(blade).slash() <= 0 && (move(state.getComboSeq()) == null
                || move(state.getComboSeq()).ready()) && owner.onGround()) state.updateComboSeq(owner, id(MORPH_AXE));
    }
    public static ComboState build(SwitchAxeMove move) {
        var builder = ComboState.Builder.newInstance().startAndEnd(0, move.duration * SourceAction.VMD_FRAMES_PER_TICK)
                .speed(SourceAction.PLAYBACK_SPEED).priority(move.ready() ? 1000 : 100)
                .motionLoc(BladePlatform.id("motions/" + move.path + ".vmd"))
                .timeout(move.ready() ? 0 : 350).next(user -> next(user, move))
                .nextOfTimeout(user -> ComboStateRegistry.NONE.getId())
                .clickAction(user -> begin(user, move)).addTickAction(user -> tick(user, move));
        if (move.ready()) builder.loop();
        return builder.build();
    }
    private static ResourceLocation next(LivingEntity user, SwitchAxeMove current) {
        var blade = user.getMainHandItem();
        var state = BladePlatform.state(blade).orElse(null);
        if (state == null || state.isBroken() || state.isSealed()) return ComboStateRegistry.NONE.getId();
        if (!user.onGround()) return id(current); // Selected ground graph; no borrowed aerial animation.
        var input = BladePlatform.input(user);
        boolean right = input.contains(InputCommand.R_CLICK), sneak = input.contains(InputCommand.SNEAK);
        var gauge = gauge(blade);
        var next = SwitchAxeRules.follow(current, state.getElapsedTime(user), sword(blade), gauge,
                right, sneak, input.contains(InputCommand.FORWARD), input.contains(InputCommand.BACK));
        if (right && !sneak && !sword(blade) && !gauge.canMorph() && !user.level().isClientSide()
                && user instanceof Player player)
            player.displayClientMessage(Component.translatable("message.slashblade_neomotions.switchaxe_gauge_low", (int) SwitchAxeGauge.MORPH_REQUIRED), true);
        return id(next);
    }
    private static void begin(LivingEntity user, SwitchAxeMove move) {
        if (user.level().isClientSide() || move.ready()) return;
        var blade = user.getMainHandItem();
        var state = BladePlatform.state(blade).orElse(null);
        if (state == null || !selected(blade)) return;
        var gauge = advance(blade, user.level().getGameTime());
        if (move == MORPH_SWORD && !gauge.canMorph() || !gauge.canSpend(move.cost)
                || move == FULL_RELEASE && !gauge.amped()) {
            state.updateComboSeq(user, sword(blade) ? id(MORPH_AXE) : ComboStateRegistry.NONE.getId());
            return;
        }
        clear(user);
        save(blade, gauge.spend(move.cost), user.level().getGameTime());
        ACTIONS.put(user, new Action(user, move, state.getLastActionTime()));
    }
    private static void abort(LivingEntity user, ISlashBladeState state) {
        clear(user); state.updateComboSeq(user, ComboStateRegistry.NONE.getId());
    }
    private static void tick(LivingEntity user, SwitchAxeMove move) {
        if (!(user.level() instanceof ServerLevel level) || move.ready()) return;
        var blade = user.getMainHandItem();
        var state = BladePlatform.state(blade).orElse(null);
        if (state == null || !selected(blade)) return;
        var action = ACTIONS.get(user);
        if (action == null || action.blade != blade || action.move != move || action.started != state.getLastActionTime()
                || !user.isAlive() || state.isBroken() || state.isSealed()) { abort(user, state); return; }
        long elapsed = state.getElapsedTime(user);
        confirmHits(user, action, level);
        int change = move.source.modeChangeTick();
        if (change >= 0 && elapsed >= change && !action.modeChanged) {
            advance(blade, level.getGameTime()); // Settle recovery under the previous mode first.
            state.setComboRoot(move.endsInAxe() ? AXE_ROOT : SWORD_ROOT);
            action.modeChanged = true;
        }
        if (sword(blade) && gauge(blade).slash() <= 0 && elapsed >= move.cancelTick
                && !move.morph() && change < 0) { state.updateComboSeq(user, id(MORPH_AXE)); return; }
        SourceRootMotion.apply(user, move.source, elapsed, action.yaw);
        for (int i = 0; i < move.source.hits.size(); i++) {
            var hit = move.source.hits.get(i);
            if (elapsed < hit.tick() || (action.pulses & (1 << i)) != 0) continue;
            action.pulses |= 1 << i;
            var gauge = advance(blade, level.getGameTime());
            double damage = move.damage * (move.sword && !move.endsInAxe() ? SwitchAxeGauge.POWER_RATE : 1);
            var slash = AttackManager.doSlash(user, hit.roll(), false, false, damage);
            if (slash != null) {
                slash.setXRot(hit.pitch());
                action.pending.add(new Pulse(slash, gauge.amped()));
            }
            if (move == AXE_SLAM && i == move.source.hits.size() - 1 && !action.enhanced) {
                save(blade, advance(blade, level.getGameTime()).enhanceAxe(), level.getGameTime()); action.enhanced = true;
            }
            if (i == 0) BladePlatform.damageBlade(user);
            if (state.isBroken()) { abort(user, state); return; }
        }
    }
    private static void confirmHits(LivingEntity user, Action action, ServerLevel level) {
        for (var pulse : action.pending) {
            if (pulse.confirmed) continue;
            var targets = pulse.slash.getAlreadyHits().stream().filter(e -> e instanceof LivingEntity && e != user
                    && action.hitTimes.getOrDefault(e.getUUID(), -1L) >= pulse.started).toList();
            if (targets.isEmpty()) continue;
            pulse.confirmed = true; // One gain per source strike, never per mob or per effect tick.
            var gauge = advance(action.blade, level.getGameTime());
            save(action.blade, gauge.hit(action.move.ampGain, action.move.slashGain), level.getGameTime());
            if (pulse.amped) for (var target : targets) {
                // Phial follow-up is a local damage pulse, without a world/block explosion.
                float damage = (float) (user.getAttributeValue(Attributes.ATTACK_DAMAGE) * .22
                        * AttackManager.getSlashBladeDamageScale(user));
                AttackManager.doAttackWith(user.damageSources().indirectMagic(pulse.slash, user), damage, target, true, false);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() / 2,
                        target.getZ(), 8, .2, .25, .2, .03);
            }
        }
    }
    /** A native slash's target list alone also contains blocked/immune targets. */
    public static void confirmedHit(LivingEntity user, LivingEntity target, ItemStack blade) {
        if (user.level().isClientSide()) return;
        var action = ACTIONS.get(user);
        if (action != null && action.blade == blade && blade == user.getMainHandItem())
            action.hitTimes.put(target.getUUID(), user.level().getGameTime());
    }
    public static void validate(LivingEntity user) {
        var action = ACTIONS.get(user);
        if (action != null && (action.blade != user.getMainHandItem() || !user.isAlive()
                || !selected(action.blade))) clear(user);
    }
    public static void clear(LivingEntity user) {
        var action = ACTIONS.remove(user);
        // Prevent a delayed native slash from attacking with a newly selected weapon.
        if (action != null) for (var pulse : action.pending) if (!pulse.slash.isRemoved()) pulse.slash.discard();
    }
    public static void clearAll() { for (var user : new ArrayList<>(ACTIONS.keySet())) clear(user); }
}
