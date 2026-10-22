package com.tonywww.slashblade_neomotions;

import com.tonywww.slashblade_neomotions.platform.ModContent;
import com.tonywww.slashblade_neomotions.platform.CommonEvents;
import com.tonywww.slashblade_neomotions.platform.ClientConfig;
//? if forge {
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
//?} else {
/*import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
*///?}

@Mod(NeoMotions.MOD_ID)
public final class NeoMotions {
    public static final String MOD_ID = "slashblade_neomotions";
    //? if forge {
    public NeoMotions() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, ClientConfig.FILE_NAME);
        ModContent.register(FMLJavaModLoadingContext.get().getModEventBus());
    //?} else {
    /*public NeoMotions(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, ClientConfig.FILE_NAME);
        ModContent.register(bus);
    *///?}
        CommonEvents.register();
    }
}

