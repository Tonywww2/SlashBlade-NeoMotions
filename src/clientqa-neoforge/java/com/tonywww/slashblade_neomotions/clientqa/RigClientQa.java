package com.tonywww.slashblade_neomotions.clientqa;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import com.tonywww.slashblade_neomotions.platform.PrototypeBlades;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Opt-in, isolated-world client regression runner. Never included in release JARs. */
@EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
public final class RigClientQa {
    private static final Logger LOG = LogUtils.getLogger();
    private record Shot(String name, String blade, String clip, int tick, boolean back) {}
    private static final Shot[] SHOTS = {
        new Shot("01-greatsword-idle", "greatsword", "greatsword", 0, false),
        new Shot("02-greatsword-back", "greatsword", "greatsword", 0, true),
        new Shot("03-greatsword-charge", "greatsword", "gs_charge_1", 16, false),
        new Shot("04-greatsword-overhead", "greatsword", "gs_overhead", 10, false),
        new Shot("05-greatsword-sweep", "greatsword", "gs_sweep", 12, false),
        new Shot("06-katana-greatsword-idle", "katana", "greatsword", 0, false),
        new Shot("07-katana-greatsword-back", "katana", "greatsword", 0, true),
        new Shot("08-greatsword-native-sa-back", "greatsword", "native", 0, true),
        new Shot("09-dualblade-idle", "dualblade", "dualblade", 0, false),
        new Shot("10-dualblade-right", "dualblade", "db_blade_cut", 4, false),
        new Shot("11-dualblade-left", "dualblade", "db_sheath_cut", 4, false),
        new Shot("12-dualblade-flurry", "dualblade", "db_flurry", 14, false),
        new Shot("13-dualblade-native-sa", "dualblade", "native", 0, false)
    };
    private static boolean opening, prepared, equipped;
    private static volatile boolean saved;
    private static int index, ticks, settling;
    private RigClientQa() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("neomotions.clientQa")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (!opening && mc.screen != null && mc.getOverlay() == null) {
            opening = true;
            mc.createWorldOpenFlows().openWorld("rig-qa", () -> {});
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (!prepared) {
            prepared = true;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var level = server.overworld(); level.setDayTime(6000);
                for (var pos : BlockPos.betweenClosed(180, 64, 180, 220, 64, 220)) level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
                var player = server.getPlayerList().getPlayer(uuid);
                player.setGameMode(GameType.CREATIVE); player.getInventory().clearContent();
                player.teleportTo(level, 200.5, 65, 200.5, 180, 0);
            });
            mc.options.hideGui = true; mc.setScreen(null);
            return;
        }
        if (++settling < 100) return;
        if (index == SHOTS.length) { LOG.info("RIG_QA_COMPLETE {} screenshots", index); mc.stop(); return; }
        var shot = SHOTS[index];
        if (!equipped) {
            equipped = true; ticks = 0; saved = false;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                ItemStack blade;
                if (shot.blade.equals("katana")) {
                    blade = new ItemStack(SlashBladeItems.SLASHBLADE.get());
                    BladePlatform.state(blade).orElseThrow().setNonEmpty(); GreatSwordStyle.apply(blade, false);
                } else blade = PrototypeBlades.create(player.level().registryAccess(), shot.blade.equals("greatsword") ? PrototypeBlades.GREAT_SWORD : PrototypeBlades.DUAL_BLADE);
                player.setItemSlot(EquipmentSlot.MAINHAND, blade);
            });
            mc.options.setCameraType(shot.back ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        }
        if (++ticks == 12 && shot.clip.equals("native")) {
            NeoForge.EVENT_BUS.post(new BladeMotionEvent(mc.player, shot.blade.equals("dualblade")
                    ? ComboStateRegistry.CIRCLE_SLASH.getId() : ComboStateRegistry.DRIVE_VERTICAL.getId()));
        }
        if (saved && ticks > 25) { index++; equipped = false; }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pose(RenderPlayerEvent.Pre event) {
        if (!Boolean.getBoolean("neomotions.clientQa") || index >= SHOTS.length || !equipped || ticks < 10) return;
        var mc = Minecraft.getInstance(); if (event.getEntity() != mc.player) return;
        var shot = SHOTS[index];
        var state = BladePlatform.state(mc.player.getMainHandItem()).orElse(null); if (state == null) return;
        mc.player.setYRot(180); mc.player.yRotO = 180; mc.player.yBodyRot = 180; mc.player.yBodyRotO = 180;
        mc.player.setXRot(0); mc.player.xRotO = 0; mc.player.yHeadRot = 180; mc.player.yHeadRotO = 180;
        // Ready shots exercise the production hotbar/idle handler, without forcing a pose.
        if (shot.clip.equals("greatsword") || shot.clip.equals("dualblade")) return;
        if (shot.clip.equals("native")) {
            state.setComboSeq(shot.blade.equals("dualblade") ? ComboStateRegistry.CIRCLE_SLASH.getId() : ComboStateRegistry.DRIVE_VERTICAL.getId());
            state.setLastActionTime(mc.level.getGameTime() - Math.max(0, ticks - 12));
            return;
        }
        state.setComboSeq(BladePlatform.id(shot.clip));
        state.setLastActionTime(mc.level.getGameTime() - shot.tick);
        var animation = (KeyframeAnimation) PlayerAnimationRegistry.getAnimation(BladePlatform.id(shot.clip));
        var layers = PlayerAnimationAccess.getPlayerAnimLayer(mc.player);
        layers.removeLayer(0); layers.addAnimLayer(0, new KeyframeAnimationPlayer(animation, shot.tick));
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("neomotions.clientQa") || !equipped || ticks != 24 || saved) return;
        var mc = Minecraft.getInstance(); var shot = SHOTS[index];
        saved = true;
        Screenshot.grab(mc.gameDirectory, shot.name + ".png", mc.getMainRenderTarget(), message -> LOG.info("RIG_QA_SHOT {} {}", shot.name, message.getString()));
        if (index == 0) {
            CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), false, mc.player.connection.registryAccess());
            for (var tabKey : java.util.List.of(CreativeModeTabs.COMBAT, CreativeModeTabs.SEARCH)) {
                var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabKey);
                for (var name : new String[]{"greatsword_prototype", "dualblade_prototype"}) {
                    long count = tab.getDisplayItems().stream().filter(stack -> BladePlatform.state(stack)
                            .map(state -> state.getTranslationKey().equals("item.slashblade_neomotions." + name)).orElse(false)).count();
                    if (count != 1) throw new AssertionError("Creative tab " + tabKey + " has " + count + " copies of " + name);
                }
            }
            LOG.info("RIG_QA_CREATIVE_PASS actual client combat/search lists");
        }
    }
}
