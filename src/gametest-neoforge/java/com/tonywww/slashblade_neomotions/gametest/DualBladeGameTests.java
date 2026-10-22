package com.tonywww.slashblade_neomotions.gametest;

import com.mojang.authlib.GameProfile;
import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.*;
import com.tonywww.slashblade_neomotions.platform.*;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import static com.tonywww.slashblade_neomotions.combat.DualBladeMove.*;

@GameTestHolder(NeoMotions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DualBladeGameTests {
    private static IInputState input(ServerPlayer p) {
        return p.getData(CapabilityInputState.INPUT_STATE.get());
    }
    private static void enchant(ServerPlayer p) { p.getMainHandItem().enchant(p.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1); }
    private static ServerPlayer player(GameTestHelper h) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "DualBladeTest"));
        p.setPos(h.absoluteVec(new Vec3(2, 2, 2))); p.setOnGround(true); p.setYRot(0);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SlashBladeItems.SLASHBLADE.get()));
        var s = state(p); s.setNonEmpty(); s.setMaxDamage(10000); s.setBaseAttackModifier(4);
        s.setProudSoulCount(100); s.setSlashArtsKey(SlashArtsRegistry.PIERCING.getId());
        DualBladeStyle.apply(p.getMainHandItem(), false);
        return p;
    }
    private static ISlashBladeState state(ServerPlayer p) { return BladePlatform.state(p.getMainHandItem()).orElseThrow(); }
    private static void begin(ServerPlayer p, DualBladeMove move, int elapsed) {
        state(p).updateComboSeq(p, DualBladeStyle.id(move));
        if (elapsed > 0) {
            state(p).setLastActionTime(p.level().getGameTime() - elapsed);
            BladePlatform.combo(DualBladeStyle.id(move)).clickAction(p);
        }
    }
    private static void tick(ServerPlayer p) { BladePlatform.combo(state(p).getComboSeq()).tickAction(p); }
    private static void charge(ServerPlayer p, int elapsed) {
        p.stopUsingItem(); input(p).getCommands().clear(); input(p).getCommands().add(InputCommand.R_DOWN);
        begin(p, SA_PREPARE, elapsed); p.startUsingItem(InteractionHand.MAIN_HAND);
    }
    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (var p : players) { StyleSelection.clear(p); p.stopUsingItem(); p.discard(); }
        h.succeed();
    }
    public static final class DualBladeEventProbe {
        final ServerPlayer player;
        final List<Double> damage = new ArrayList<>();
        final List<Float> rolls = new ArrayList<>();
        int arts;
        boolean cancelArt;
        DualBladeEventProbe(ServerPlayer player) { this.player = player; }
        @SubscribeEvent public void slash(SlashBladeEvent.DoSlashEvent event) {
            if (event.getUser() == player) { damage.add(event.getDamage()); rolls.add(event.getRoll()); event.setCanceled(true); }
        }
        @SubscribeEvent public void art(SlashBladeEvent.PerformSlashArtEvent event) {
            if (event.getEntityLiving() == player) { arts++; if (cancelArt) event.setCanceled(true); }
        }
    }
    @GameTest(template="empty")
    public static void manualSwitchesBothStylesAndRestoresOriginal(GameTestHelper h) {
        var p = player(h); var blade = p.getMainHandItem();
        for (var move : values()) h.assertTrue(BladePlatform.combo(DualBladeStyle.id(move)) != null, "Missing combo " + move);
        GreatSwordStyle.apply(blade, false); DualBladeStyle.apply(blade, false); GreatSwordStyle.apply(blade, false);
        p.setItemSlot(EquipmentSlot.OFFHAND, ModContent.DUAL_BLADE_MANUAL.get().getDefaultInstance());
        ModContent.DUAL_BLADE_MANUAL.get().use(p.level(), p, InteractionHand.OFF_HAND);
        h.assertTrue(DualBladeStyle.selected(blade), "Manual did not equip dual blade");
        p.setShiftKeyDown(true);
        ModContent.DUAL_BLADE_MANUAL.get().use(p.level(), p, InteractionHand.OFF_HAND);
        h.assertTrue(state(p).getComboRoot().equals(ComboStateRegistry.STANDBY.getId()), "Switching lost original root");
        h.assertFalse(StyleSelection.apply(blade, DualBladeStyle.ROOT, true), "Restoring twice should not modify a native blade");
        h.assertTrue(p.getOffhandItem().getCount() == 1, "Manual was consumed");
        done(h, p);
    }
    @GameTest(template="empty", timeoutTicks=110)
    public static void nativeClicksCompleteChainAndRespectRecovery(GameTestHelper h) {
        var p = player(h); input(p).getCommands().add(InputCommand.L_CLICK);
        state(p).progressCombo(p);
        h.assertTrue(state(p).getComboSeq().equals(DualBladeStyle.id(BLADE_CUT)), "Left click did not enter tree");
        input(p).getCommands().add(InputCommand.R_CLICK); state(p).progressCombo(p);
        h.assertTrue(state(p).getComboSeq().equals(DualBladeStyle.id(BLADE_CUT)), "SA bypassed attack recovery");
        input(p).getCommands().remove(InputCommand.R_CLICK);
        var expected = List.of(SHEATH_CUT, CROSS_CUT, FLURRY, FINISHER, BLADE_CUT);
        int[] times = {9, 18, 32, 56, 78};
        for (int i = 0; i < times.length; i++) {
            var move = expected.get(i);
            h.runAtTickTime(times[i], () -> {
                state(p).progressCombo(p);
                h.assertTrue(state(p).getComboSeq().equals(DualBladeStyle.id(move)), "Wrong follow-up: " + move);
            });
        }
        h.runAtTickTime(80, () -> done(h, p));
    }
    @GameTest(template="empty")
    public static void flurryHasSixAlternatingHitsAndOneDurabilityCost(GameTestHelper h) {
        var p = player(h); var other = player(h); var probe = new DualBladeEventProbe(p); var second = new DualBladeEventProbe(other);
        NeoForge.EVENT_BUS.register(probe); NeoForge.EVENT_BUS.register(second);
        begin(p, FLURRY, 0); begin(other, SHEATH_CUT, 0);
        for (int t = 1; t <= 28; t++) h.runAtTickTime(t, () -> { tick(p); tick(p); tick(other); tick(other); });
        h.runAtTickTime(30, () -> {
            NeoForge.EVENT_BUS.unregister(probe); NeoForge.EVENT_BUS.unregister(second);
            h.assertTrue(probe.damage.equals(Collections.nCopies(6, .35)), "Duplicate/missing flurry hits: " + probe.damage);
            h.assertTrue(probe.rolls.equals(List.of(120F, 60F, 120F, 60F, 120F, 60F)), "Hands did not alternate");
            h.assertTrue(second.damage.equals(List.of(.50)), "Sheath did not strike independently");
            h.assertTrue(p.getMainHandItem().getDamageValue() == 1, "Flurry spent durability per hit");
            done(h, p, other);
        });
    }
    @GameTest(template="empty")
    public static void sheathStrikeCreatesNativeAttackAndDamagesTarget(GameTestHelper h) {
        var p = player(h);
        var target = h.spawn(EntityType.HUSK, new BlockPos(2, 2, 3));
        target.setNoAi(true); target.setNoGravity(true);
        float health = target.getHealth();
        begin(p, SHEATH_CUT, 3); tick(p);
        var effects = h.getLevel().getEntitiesOfClass(EntitySlashEffect.class, p.getBoundingBox().inflate(5), e -> e.getShooter() == p);
        h.assertTrue(effects.size() == 1, "Sheath must create one native slash");
        effects.get(0).tick(); effects.get(0).tick();
        h.assertTrue(target.getHealth() < health, "Sheath only animated; target took no damage");
        effects.forEach(Entity::discard); target.discard(); done(h, p);
    }
    @GameTest(template="empty")
    public static void movementImpulseIsAppliedOnlyOnce(GameTestHelper h) {
        var p = player(h);
        begin(p, DASH, 1); tick(p);
        h.assertTrue(p.getDeltaMovement().z > .4, "Dash did not move forward");
        p.setDeltaMovement(Vec3.ZERO); tick(p);
        h.assertTrue(p.getDeltaMovement().equals(Vec3.ZERO), "Repeated tick accelerated dash again");
        begin(p, RETREAT, 1); tick(p);
        h.assertTrue(p.getDeltaMovement().z < -.3, "Retreat did not move backwards");
        done(h, p);
    }
    @GameTest(template="empty")
    public static void aerialLandingTransitionsOnce(GameTestHelper h) {
        var p = player(h); p.setOnGround(false); input(p).getCommands().add(InputCommand.L_CLICK);
        state(p).progressCombo(p);
        h.assertTrue(state(p).getComboSeq().equals(DualBladeStyle.id(AERIAL)), "Missing aerial entry");
        p.setOnGround(true); tick(p);
        h.assertTrue(state(p).getComboSeq().equals(DualBladeStyle.id(LANDING)), "Missing landing combo");
        long time = state(p).getLastActionTime(); tick(p);
        h.assertTrue(state(p).getLastActionTime() == time, "Landing restarted");
        done(h, p);
    }
    @GameTest(template="empty")
    public static void interruptedAttacksCannotResumeOnCopiedOrBrokenBlade(GameTestHelper h) {
        for (int interruption = 0; interruption < 3; interruption++) {
            var p = player(h); var probe = new DualBladeEventProbe(p); NeoForge.EVENT_BUS.register(probe);
            try {
                begin(p, FLURRY, 20);
                if (interruption == 0) p.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copy());
                else if (interruption == 1) state(p).setBroken(true); else state(p).setSealed(true);
                tick(p);
                h.assertTrue(probe.damage.isEmpty(), "Invalid blade retained delayed attacks");
                h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Invalid action survived");
                StyleSelection.clear(p); p.discard();
            } finally { NeoForge.EVENT_BUS.unregister(probe); }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void rightClickUsesNativePerfectSaAndChargesOnlyOnce(GameTestHelper h) {
        var p = player(h); enchant(p); var probe = new DualBladeEventProbe(p); NeoForge.EVENT_BUS.register(probe);
        try {
            p.getMainHandItem().getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(state(p).getComboSeq().equals(DualBladeStyle.id(SA_PREPARE)), "Right click did not prepare SA");
            charge(p, state(p).getFullChargeTicks(p)); p.releaseUsingItem(); DualBladeStyle.release(p, p.getMainHandItem());
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.PIERCING_JUST.getId()), "Blade's perfect SA was not used");
            h.assertTrue(probe.arts == 1, "SA cast multiple times");
            h.assertTrue(state(p).getProudSoulCount() == 100 - state(p).getSlashArts().getProudSoulCost(), "Incorrect SA cost");
            h.assertFalse(DualBladeStyle.releasingSlashArt(p), "SA permission leaked");
            done(h, p);
        } finally { NeoForge.EVENT_BUS.unregister(probe); }
    }
    @GameTest(template="empty")
    public static void underchargeTimeoutAndCanceledArtNeverSpendSouls(GameTestHelper h) {
        var p = player(h); enchant(p); var probe = new DualBladeEventProbe(p); NeoForge.EVENT_BUS.register(probe);
        try {
            charge(p, 2); p.releaseUsingItem();
            h.assertTrue(probe.arts == 0 && state(p).getProudSoulCount() == 100, "Undercharge cast SA");
            charge(p, 54); tick(p); p.releaseUsingItem();
            h.assertTrue(probe.arts == 0 && state(p).getProudSoulCount() == 100, "Timeout cast SA");
            probe.cancelArt = true; charge(p, 20); p.releaseUsingItem();
            h.assertTrue(probe.arts == 1 && state(p).getProudSoulCount() == 100, "Canceled SA cost souls");
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Canceled SA stayed armed");
            done(h, p);
        } finally { NeoForge.EVENT_BUS.unregister(probe); }
    }
    @GameTest(template="empty")
    public static void saInterruptedByAirSwapSealOrStoppedUseIsCleared(GameTestHelper h) {
        for (int interruption = 0; interruption < 5; interruption++) {
            var p = player(h); enchant(p); charge(p, 20);
            switch (interruption) {
                case 0 -> p.setOnGround(false);
                case 1 -> p.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copy());
                case 2 -> state(p).setSealed(true);
                case 3 -> state(p).setBroken(true);
                default -> p.stopUsingItem();
            }
            tick(p); p.releaseUsingItem();
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Interrupted SA survived " + interruption);
            h.assertTrue(state(p).getProudSoulCount() == 100, "Interrupted SA spent souls " + interruption);
            StyleSelection.clear(p); p.stopUsingItem(); p.discard();
        }
        h.succeed();
    }
}
