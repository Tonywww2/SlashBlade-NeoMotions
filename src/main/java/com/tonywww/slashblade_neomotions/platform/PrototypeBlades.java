package com.tonywww.slashblade_neomotions.platform;

import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Named blades use SlashBlade's synced data registry, recipes, renderer and save format. */
public final class PrototypeBlades {
    public static final ResourceLocation GREAT_SWORD = BladePlatform.id("greatsword_prototype");
    public static final ResourceLocation DUAL_BLADE = BladePlatform.id("dualblade_prototype");
    public static final ResourceLocation SWITCH_AXE = BladePlatform.id("switchaxe_prototype");
    private PrototypeBlades() {}
    public static ItemStack create(HolderLookup.Provider access, ResourceLocation name) {
        var definition = access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .getOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, name)).value();
        //? if <1.21 {
        return definition.getBlade();
        //?} else {
        /*return definition.getBlade(access);
        *///?}
    }
    /** A prototype's factory style is its restore target even before a manual has been used. */
    public static ResourceLocation defaultRoot(ItemStack blade) {
        String name = BladePlatform.state(blade).map(s -> s.getTranslationKey()).orElse("");
        if (name.equals("item.slashblade_neomotions.greatsword_prototype")) return GreatSwordStyle.ROOT;
        if (name.equals("item.slashblade_neomotions.dualblade_prototype")) return DualBladeStyle.ROOT;
        if (name.equals("item.slashblade_neomotions.switchaxe_prototype")) return SwitchAxeStyle.AXE_ROOT;
        return ComboStateRegistry.STANDBY.getId();
    }
}
