package com.tonywww.slashblade_neomotions.gametest;

import com.mojang.authlib.GameProfile;
import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.*;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import com.tonywww.slashblade_neomotions.platform.CommonEvents;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.tonywww.slashblade_neomotions.combat.GreatSwordMove.*;

@GameTestHolder(NeoMotions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GreatSwordGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "NeoMotionTest"));
        player.setPos(h.absoluteVec(new Vec3(2,2,2)));
        player.setOnGround(true);
        player.setYRot(0);
        var blade = new ItemStack(SlashBladeItems.SLASHBLADE.get());
        player.setItemSlot(EquipmentSlot.MAINHAND,blade);
        var state = state(player);
        state.setNonEmpty(); state.setMaxDamage(10000); state.setBaseAttackModifier(4);
        GreatSwordStyle.apply(blade,false);
        return player;
    }
    private static ISlashBladeState state(ServerPlayer p) { return BladePlatform.state(p.getMainHandItem()).orElseThrow(); }
    private static void begin(ServerPlayer p, GreatSwordMove move) { state(p).updateComboSeq(p,GreatSwordStyle.id(move)); }
    private static void tick(ServerPlayer p) {
        var combo=BladePlatform.combo(state(p).getComboSeq());
        if(combo!=null) combo.tickAction(p);
    }
    public static final class Probe {
        final ServerPlayer user;
        final List<Double> damage = new ArrayList<>();
        Probe(ServerPlayer p){user=p;}
        @SubscribeEvent public void slash(SlashBladeEvent.DoSlashEvent e){
            if(e.getUser()==user){damage.add(e.getDamage());e.setCanceled(true);}
        }
    }
    private static void done(GameTestHelper h, ServerPlayer... players) {
        for(var p:players){GreatSwordStyle.clear(p);p.discard();}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void registrationsAndStyleRestoration(GameTestHelper h) {
        var p=player(h);
        for(var move:values())h.assertTrue(BladePlatform.combo(GreatSwordStyle.id(move))!=null,"Missing "+move);
        h.assertTrue(GreatSwordStyle.selected(p.getMainHandItem()),"Style not installed");
        GreatSwordStyle.apply(p.getMainHandItem(),false);
        GreatSwordStyle.apply(p.getMainHandItem(),true);
        h.assertTrue(state(p).getComboRoot().equals(ComboStateRegistry.STANDBY.getId()),"Repeated install overwrote original root");
        done(h,p);
    }
    @GameTest(template="empty")
    public static void heldRecoveryInputStartsNextChargeAtTheWindow(GameTestHelper h) {
        var players = new ArrayList<ServerPlayer>();
        for (var slash : List.of(CHARGED_SLASH, STRONG_SLASH, TRUE_SLASH)) {
            var p = player(h); players.add(p); begin(p, slash);
            state(p).setLastActionTime(h.getLevel().getGameTime() - slash.cancelTick + 1);
            BladePlatform.combo(GreatSwordStyle.id(slash)).clickAction(p);
            p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            tick(p);
            h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(slash)), "Buffered input interrupted recovery");
        }
        h.runAtTickTime(1, () -> {
            for (int i = 0; i < players.size(); i++) {
                var p = players.get(i); tick(p); tick(p);
                var expected = i == 0 ? CHARGE_2 : i == 1 ? CHARGE_3 : CHARGE_1;
                h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(expected)), "Held input was lost after slash " + i);
                h.assertTrue(state(p).getElapsedTime(p) == 0, "Recovery time counted toward charge");
            }
            done(h, players.toArray(ServerPlayer[]::new));
        });
    }
    @GameTest(template="empty")
    public static void releasedOrSwappedRecoveryInputCannotStartCharge(GameTestHelper h) {
        var released = player(h); var swapped = player(h);
        for (var p : List.of(released, swapped)) {
            begin(p, CHARGED_SLASH);
            state(p).setLastActionTime(h.getLevel().getGameTime() - CHARGED_SLASH.cancelTick + 1);
            BladePlatform.combo(GreatSwordStyle.id(CHARGED_SLASH)).clickAction(p);
            p.getMainHandItem().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        }
        released.releaseUsingItem();
        swapped.setItemSlot(EquipmentSlot.MAINHAND, swapped.getMainHandItem().copy());
        h.runAtTickTime(1, () -> {
            tick(released); tick(swapped);
            h.assertTrue(state(released).getComboSeq().equals(GreatSwordStyle.id(CHARGED_SLASH)), "Released buffer started a charge");
            h.assertTrue(state(swapped).getComboSeq().equals(ComboStateRegistry.NONE.getId()), "Buffer leaked into a replacement stack");
            done(h, released, swapped);
        });
    }
    @GameTest(template="empty")
    public static void nativeInputAndRecoveryLock(GameTestHelper h) {
        var p=player(h);
        p.getCapability(CapabilityInputState.INPUT_STATE).orElseThrow(IllegalStateException::new).getCommands().add(InputCommand.L_CLICK);
        state(p).progressCombo(p);
        h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(OVERHEAD)),"Left click did not enter custom tree");
        state(p).progressCombo(p);
        h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(OVERHEAD)),"Click spam bypassed recovery");
        h.runAtTickTime(15,()->{
            state(p).progressCombo(p);
            h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(SWEEP)),"Follow-up escaped to native root");
            done(h,p);
        });
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void trueSlashHasExactlyTwoPulses(GameTestHelper h) {
        var p=player(h);var probe=new Probe(p);MinecraftForge.EVENT_BUS.register(probe);begin(p,TRUE_SLASH);
        for(int i=1;i<=44;i++)h.runAtTickTime(i,()->{tick(p);tick(p);});
        h.runAtTickTime(46,()->{
            MinecraftForge.EVENT_BUS.unregister(probe);
            h.assertTrue(probe.damage.equals(List.of(0.35,2.50)),"Wrong pulse count/ratio: "+probe.damage);
            done(h,p);
        });
    }
    @GameTest(template="empty",timeoutTicks=85)
    public static void chargeAndPlayersAreIndependent(GameTestHelper h) {
        var p=player(h);var other=player(h);var probe=new Probe(p);var second=new Probe(other);
        MinecraftForge.EVENT_BUS.register(probe);MinecraftForge.EVENT_BUS.register(second);
        begin(p,CHARGE_1);p.startUsingItem(InteractionHand.MAIN_HAND);
        h.runAtTickTime(8,()->begin(other,OVERHEAD));
        h.runAtTickTime(20,()->{tick(other);tick(other);});
        h.runAtTickTime(32,()->{
            var stop=new LivingEntityUseItemEvent.Stop(p,p.getMainHandItem(),71968);
            CommonEvents.stopUsing(stop);
            h.assertTrue(stop.isCanceled(),"Native SA not suppressed");
            h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(CHARGED_SLASH)),"Release didn't slash");
        });
        h.runAtTickTime(46,()->{
            tick(p);tick(p);MinecraftForge.EVENT_BUS.unregister(probe);MinecraftForge.EVENT_BUS.unregister(second);
            h.assertTrue(probe.damage.size()==1 && Math.abs(probe.damage.get(0)-1.3*1.75)<.001,"Charged damage invalid "+probe.damage);
            h.assertTrue(second.damage.equals(List.of(1.15)),"Charge or timeline leaked to second player "+second.damage);
            done(h,p,other);
        });
    }
    @GameTest(template="empty",timeoutTicks=55)
    public static void swappedBladeCannotResumeDamage(GameTestHelper h) {
        var p=player(h);var probe=new Probe(p);MinecraftForge.EVENT_BUS.register(probe);begin(p,TRUE_SLASH);
        var copy=p.getMainHandItem().copy();p.setItemSlot(EquipmentSlot.MAINHAND,copy);
        h.runAtTickTime(28,()->{
            tick(p);MinecraftForge.EVENT_BUS.unregister(probe);
            h.assertTrue(probe.damage.isEmpty(),"Replacement blade resumed old cast");
            h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()),"Invalid action not aborted");
            done(h,p);
        });
    }
    @GameTest(template="empty")
    public static void tackleArmorAndSkip(GameTestHelper h) {
        var p=player(h);begin(p,TACKLE_1);p.startUsingItem(InteractionHand.MAIN_HAND);
        var hurt=new LivingHurtEvent(p,h.getLevel().damageSources().generic(),10);
        CommonEvents.tackleDamage(hurt);
        h.assertTrue(hurt.getAmount()==5,"Tackle did not halve damage");
        h.runAtTickTime(10,()->{
            tick(p);
            h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(CHARGE_2)),"Tackle didn't skip charge stage");
            h.assertFalse(GreatSwordStyle.armored(p),"Tackle armor leaked into charge");
            done(h,p);
        });
    }
    @GameTest(template="empty")
    public static void chargeCancelsInAirAndBrokenBlade(GameTestHelper h) {
        var p=player(h);begin(p,CHARGE_1);p.setOnGround(false);tick(p);
        h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()),"Airborne charge survived");
        p.setOnGround(true);begin(p,OVERHEAD);state(p).setBroken(true);tick(p);
        h.assertTrue(state(p).getComboSeq().equals(ComboStateRegistry.NONE.getId()),"Broken blade retained action");
        done(h,p);
    }
    @GameTest(template="empty")
    public static void landingTransitionsOnce(GameTestHelper h) {
        var p=player(h);p.setOnGround(false);begin(p,AERIAL);p.setOnGround(true);tick(p);
        h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(LANDING)),"Missing landing transition");
        tick(p);h.assertTrue(state(p).getComboSeq().equals(GreatSwordStyle.id(LANDING)),"Landing restarted");
        done(h,p);
    }
}
