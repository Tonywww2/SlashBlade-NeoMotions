package com.tonywww.slashblade_neomotions.gametest;

import com.mojang.authlib.GameProfile;
import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.*;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.tonywww.slashblade_neomotions.combat.GreatSwordMove.*;

@GameTestHolder(NeoMotions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GreatSwordSlashArtGameTests {
    private static IInputState input(ServerPlayer p) {
        return p.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new);
    }
    private static void enchant(ServerPlayer p) { p.getMainHandItem().enchant(Enchantments.SHARPNESS, 1); }
    private static ServerPlayer player(GameTestHelper h) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "NeoMotionSATest"));
        p.setPos(h.absoluteVec(new Vec3(2, 2, 2))); p.setOnGround(true);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SlashBladeItems.SLASHBLADE.get()));
        var s = state(p); s.setNonEmpty(); s.setMaxDamage(10000); s.setBaseAttackModifier(4);
        s.setProudSoulCount(100); s.setSlashArtsKey(SlashArtsRegistry.PIERCING.getId());
        enchant(p); GreatSwordStyle.apply(p.getMainHandItem(), false);
        return p;
    }
    private static ISlashBladeState state(ServerPlayer p) { return BladePlatform.state(p.getMainHandItem()).orElseThrow(); }
    private static void charge(ServerPlayer p, GreatSwordMove move, int elapsed) {
        p.stopUsingItem();
        input(p).getCommands().clear(); input(p).getCommands().add(InputCommand.R_DOWN);
        state(p).updateComboSeq(p, GreatSwordStyle.id(move));
        // Give the real Combo click action a deterministic server start time.
        state(p).setLastActionTime(p.level().getGameTime() - elapsed);
        BladePlatform.combo(GreatSwordStyle.id(move)).clickAction(p);
        p.startUsingItem(InteractionHand.MAIN_HAND);
    }
    private static void key(ServerPlayer p, InputCommand key, boolean down) {
        var input = input(p); var old = input.getCommands().clone();
        if (down) input.getCommands().add(key); else input.getCommands().remove(key);
        InputCommandEvent.onInputChange(p, input, old, input.getCommands().clone());
    }
    private static void tick(ServerPlayer p) { BladePlatform.combo(state(p).getComboSeq()).tickAction(p); }
    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (var p : players) { GreatSwordStyle.clear(p); p.stopUsingItem(); p.discard(); }
        h.succeed();
    }
    public static final class Probe {
        final ServerPlayer user;
        final List<SlashArts.ArtsType> types = new ArrayList<>();
        final List<Integer> elapsed = new ArrayList<>();
        boolean cancel;
        Probe(ServerPlayer user) { this.user = user; }
        @SubscribeEvent public void art(SlashBladeEvent.PerformSlashArtEvent event) {
            if (event.getEntityLiving() != user) return;
            types.add(event.getType()); elapsed.add(event.getElapsed());
            if (cancel) event.setCanceled(true);
        }
    }
    @GameTest(template="empty")
    public static void leftPressArmsEveryStageWithoutRestarting(GameTestHelper h) {
        var p = player(h);
        for (int stage = 1; stage <= 3; stage++) {
            charge(p, GreatSwordMove.charge(stage), 5);
            key(p, InputCommand.L_DOWN, true);
            h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(GreatSwordMove.prepareSlashArt(stage))), "Left press did not arm SA stage " + stage);
            long started = state(p).getLastActionTime();
            key(p, InputCommand.L_DOWN, false); key(p, InputCommand.L_DOWN, true);
            h.assertTrue(state(p).getLastActionTime() == started, "Repeated click restarted SA");
            p.releaseUsingItem();
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Undercharge did not cancel");
            h.assertTrue(state(p).getProudSoulCount() == 100, "Undercharge spent souls");
        }
        done(h, p);
    }
    @GameTest(template="empty")
    public static void inheritedChargeUsesNativePerfectWindowAndCostOnce(GameTestHelper h) {
        var p = player(h); var probe = new Probe(p); MinecraftForge.EVENT_BUS.register(probe);
        try {
            int full = state(p).getFullChargeTicks(p);
            charge(p, CHARGE_3, full); key(p, InputCommand.L_DOWN, true);
            p.releaseUsingItem(); GreatSwordStyle.release(p, p.getMainHandItem());
            h.assertTrue(probe.types.equals(List.of(SlashArts.ArtsType.Jackpot)), "Wrong native perfect result: " + probe.types);
            h.assertTrue(probe.elapsed.equals(List.of(full)), "Charge time was reset on SA entry");
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.PIERCING_JUST.getId()), "Blade's perfect SA was not used");
            h.assertTrue(state(p).getProudSoulCount() == 100 - state(p).getSlashArts().getProudSoulCost(), "SA charged twice or wrong cost");
            h.assertTrue(!GreatSwordStyle.releasingSlashArt(p), "Native permission leaked past release");
            done(h, p);
        } finally { MinecraftForge.EVENT_BUS.unregister(probe); }
    }
    @GameTest(template="empty")
    public static void perfectWindowEndUsesNormalBladeArt(GameTestHelper h) {
        var p = player(h); var probe = new Probe(p); MinecraftForge.EVENT_BUS.register(probe);
        try {
            int end = state(p).getFullChargeTicks(p) + SlashArts.getJustReceptionSpan(p);
            charge(p, CHARGE_2, end); key(p, InputCommand.L_DOWN, true); p.releaseUsingItem();
            h.assertTrue(probe.types.equals(List.of(SlashArts.ArtsType.Success)), "Perfect window end was inclusive");
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.PIERCING.getId()), "Normal SA did not use blade setting");
            done(h, p);
        } finally { MinecraftForge.EVENT_BUS.unregister(probe); }
    }
    @GameTest(template="empty")
    public static void soulShortageUsesNativeDurabilityAndCreativeIsFree(GameTestHelper h) {
        var p = player(h); var creative = player(h);
        state(p).setProudSoulCount(0);
        int damage = p.getMainHandItem().getDamageValue();
        charge(p, CHARGE_1, 20); key(p, InputCommand.L_DOWN, true); p.releaseUsingItem();
        h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.PIERCING.getId()), "No SA without souls");
        h.assertTrue(p.getMainHandItem().getDamageValue() == damage + 1, "Missing native durability fallback");
        creative.getAbilities().instabuild = true;
        charge(creative, CHARGE_1, 20); key(creative, InputCommand.L_DOWN, true); creative.releaseUsingItem();
        h.assertTrue(state(creative).getProudSoulCount() == 100 && creative.getMainHandItem().getDamageValue() == 0, "Creative SA consumed resources");
        done(h, p, creative);
    }
    @GameTest(template="empty")
    public static void canceledArtHasNoCostOrLingeringPermission(GameTestHelper h) {
        var p = player(h); var probe = new Probe(p); probe.cancel = true; MinecraftForge.EVENT_BUS.register(probe);
        try {
            charge(p, CHARGE_1, 20); key(p, InputCommand.L_DOWN, true); p.releaseUsingItem();
            h.assertTrue(probe.types.size() == 1, "Native SA event was not offered to other mods");
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Canceled SA remained armed");
            h.assertTrue(state(p).getProudSoulCount() == 100, "Canceled event spent souls");
            h.assertFalse(GreatSwordStyle.releasingSlashArt(p), "Canceled SA left bypass active");
            done(h, p);
        } finally { MinecraftForge.EVENT_BUS.unregister(probe); }
    }
    @GameTest(template="empty")
    public static void sneakCancelsPreparationAndKeepsTackleStage(GameTestHelper h) {
        var p = player(h); charge(p, CHARGE_2, 20); key(p, InputCommand.L_DOWN, true);
        key(p, InputCommand.SNEAK, true); tick(p);
        h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(TACKLE_2)), "SA preparation lost tackle stage");
        p.releaseUsingItem();
        h.assertTrue(state(p).getProudSoulCount() == 100, "Tackle released old SA");
        done(h, p);
    }
    @GameTest(template="empty")
    public static void interruptionsAndTimeoutNeverReleaseArt(GameTestHelper h) {
        for (int interruption = 0; interruption < 5; interruption++) {
            var p = player(h); charge(p, CHARGE_1, 20); key(p, InputCommand.L_DOWN, true);
            switch (interruption) {
                case 0 -> p.setOnGround(false);
                case 1 -> state(p).setBroken(true);
                case 2 -> state(p).setSealed(true);
                case 3 -> p.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem().copy());
                default -> p.stopUsingItem();
            }
            tick(p); GreatSwordStyle.release(p, p.getMainHandItem());
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Interrupted SA survived: " + interruption);
            h.assertTrue(state(p).getProudSoulCount() == 100, "Interrupted SA spent souls: " + interruption);
            GreatSwordStyle.clear(p); p.stopUsingItem(); p.discard();
        }
        var timeout = player(h); charge(timeout, CHARGE_1, 53); key(timeout, InputCommand.L_DOWN, true);
        h.runAtTickTime(2, () -> {
            tick(timeout); timeout.releaseUsingItem();
            h.assertTrue(state(timeout).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Timed out SA remained armed");
            h.assertTrue(state(timeout).getProudSoulCount() == 100, "Timeout automatically cast SA");
            done(h, timeout);
        });
    }
    @GameTest(template="empty")
    public static void preparationCannotCancelAttackRecovery(GameTestHelper h) {
        var p = player(h); charge(p, TRUE_SLASH, 10); key(p, InputCommand.L_DOWN, true);
        h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(TRUE_SLASH)), "SA canceled attack recovery");
        p.releaseUsingItem();
        h.assertTrue(state(p).getProudSoulCount() == 100, "Recovery release cast SA");
        done(h, p);
    }
    @GameTest(template="empty")
    public static void nativeEligibilityAndOtherPlayersArePreserved(GameTestHelper h) {
        var p = player(h); var other = player(h); var plain = player(h);
        // Removing the enchantment preserves all other blade state.
        plain.getMainHandItem().removeTagKey("Enchantments");
        charge(p, CHARGE_3, 32); charge(other, CHARGE_3, 32); charge(plain, CHARGE_1, 20);
        key(p, InputCommand.L_DOWN, true); key(plain, InputCommand.L_DOWN, true);
        p.releaseUsingItem(); other.releaseUsingItem(); plain.releaseUsingItem();
        h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.PIERCING.getId()), "Armed player did not release SA");
        h.assertTrue(state(other).getComboSeq().equals(GreatSwordStyle.id(TRUE_SLASH)), "SA choice leaked to another player");
        h.assertTrue(state(plain).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Unenchanted blade cast SA");
        h.assertTrue(state(plain).getProudSoulCount() == 100, "Unavailable SA spent souls");
        done(h, p, other, plain);
    }
}
