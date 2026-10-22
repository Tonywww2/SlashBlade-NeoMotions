package com.tonywww.slashblade_neomotions.platform;

//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
/*import net.neoforged.neoforge.common.ModConfigSpec;
*///?}

/** Personal display preferences; no gameplay or server-side dependencies. */
public final class ClientConfig {
    public static final String FILE_NAME = "slashblade_neomotions-client.toml";
    //? if forge {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_MOVE_INFO, SHOW_WEAPON_GAUGES;
    //?} else {
    /*public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_MOVE_INFO, SHOW_WEAPON_GAUGES;
    *///?}
    static {
        //? if forge {
        var builder = new ForgeConfigSpec.Builder();
        //?} else {
        /*var builder = new ModConfigSpec.Builder();
        *///?}
        builder.push("hud");
        SHOW_MOVE_INFO = builder
                .comment("Show the current Great Sword / Dual Blade / Switch Axe move in the action bar, including charge and SA details.",
                        "显示当前大剑／双刀／斩斧招式、动作阶段及蓄力／SA 信息。默认关闭，仅影响本客户端。")
                .define("showMoveInfo", false);
        SHOW_WEAPON_GAUGES = builder.comment("Show Switch Axe slash/amp gauges while held. 显示斩斧槽，独立于招式信息选项。")
                .define("showWeaponGauges", true);
        builder.pop();
        SPEC = builder.build();
    }
    private ClientConfig() {}
}
