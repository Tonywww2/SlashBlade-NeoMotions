package com.tonywww.slashblade_neomotions.platform.client;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeGauge;
import com.tonywww.slashblade_neomotions.platform.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
*///?}
public final class SwitchAxeGaugeDisplay {
    private SwitchAxeGaugeDisplay() {}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!ClientConfig.SHOW_WEAPON_GAUGES.get() || mc.options.hideGui || mc.player == null
                || !mc.player.isAlive() || mc.player.isSpectator() || !SwitchAxeStyle.selected(mc.player.getMainHandItem())) return;
        var stack = mc.player.getMainHandItem();
        var gauge = SwitchAxeStyle.gauge(stack);
        var graphics = event.getGuiGraphics();
        int x = 12, y = mc.getWindow().getGuiScaledHeight() - 76;
        graphics.drawString(mc.font, Component.translatable("hud.slashblade_neomotions." +
                (SwitchAxeStyle.sword(stack) ? "sword_mode" : "axe_mode")), x, y - 12, 0xFFFFFF, true);
        bar(graphics, x, y, gauge.slash(), "slash_gauge", gauge.canMorph() ? 0xFFE1B661 : 0xFF9B7777,
                Math.round(gauge.slash()) + "/100", true);
        bar(graphics, x, y + 23, gauge.amped() ? gauge.ampTicks() * 100F / SwitchAxeGauge.AMP_DURATION : gauge.awake(),
                gauge.amped() ? "amped" : "awake_gauge", gauge.amped() ? 0xFF65DCE4 : 0xFFD1A16E,
                gauge.amped() ? (int) Math.ceil(gauge.ampTicks() / 20.0) + "s" : Math.round(gauge.awake()) + "/100", false);
    }
    private static void bar(GuiGraphics graphics, int x, int y, float value, String key, int color, String number, boolean threshold) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("hud.slashblade_neomotions." + key), x, y, 0xD7DCE2, true);
        graphics.drawString(font, number, x + 110 - font.width(number), y, 0xD7DCE2, true);
        graphics.fill(x, y + 11, x + 110, y + 17, 0xBB11151B);
        graphics.fill(x + 1, y + 12, x + 1 + Math.round(108 * value / 100), y + 16, color);
        if (threshold) graphics.fill(x + 33, y + 11, x + 34, y + 17, 0xFFF4F4F4);
    }
}
