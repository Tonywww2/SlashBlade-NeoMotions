package com.tonywww.slashblade_neomotions.combat;

import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Preserve the blade's original root across any number of changes between our styles. */
public final class StyleSelection {
    private StyleSelection() {}
    public static boolean owns(ResourceLocation root) {
        return GreatSwordStyle.ROOT.equals(root) || DualBladeStyle.ROOT.equals(root) || SwitchAxeStyle.owns(root);
    }
    public static boolean apply(ItemStack blade, ResourceLocation root, boolean restore) {
        var state = BladePlatform.state(blade).orElse(null);
        if (state == null) return false;
        var current = state.getComboRoot();
        if (restore) {
            if (!owns(current)) return false;
            state.setComboRoot(BladePlatform.takePreviousRoot(blade));
        } else {
            if (!owns(current)) BladePlatform.savePreviousRoot(blade, current);
            state.setComboRoot(root);
        }
        state.setComboSeq(ComboStateRegistry.NONE.getId());
        state.setLastActionTime(0);
        return true;
    }
    public static void clear(LivingEntity user) { GreatSwordStyle.clear(user); DualBladeStyle.clear(user); SwitchAxeStyle.clear(user); }
    public static void clearAll() { GreatSwordStyle.clearAll(); DualBladeStyle.clearAll(); SwitchAxeStyle.clearAll(); }
}
