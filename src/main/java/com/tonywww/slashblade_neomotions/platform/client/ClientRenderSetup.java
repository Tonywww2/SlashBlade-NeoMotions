package com.tonywww.slashblade_neomotions.platform.client;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.item.ManualMovesTooltip;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
*///?}
public final class ClientRenderSetup {
    private ClientRenderSetup() {}
    @SubscribeEvent public static void tooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ManualMovesTooltip.class, ClientManualMovesTooltip::new);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer)
                playerRenderer.addLayer(new StyleWeaponLayer(playerRenderer));
        }
    }
}
