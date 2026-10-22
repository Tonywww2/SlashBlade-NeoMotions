package com.tonywww.slashblade_neomotions.platform;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.GreatSwordMove;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.combat.DualBladeMove;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.item.StyleManualItem;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import java.util.function.Supplier;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
*///?}

public final class ModContent {
    private static final DeferredRegister<ComboState> COMBOS = DeferredRegister.create(ComboState.REGISTRY_KEY, NeoMotions.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, NeoMotions.MOD_ID);
    public static final Supplier<Item> GREAT_SWORD_MANUAL = ITEMS.register("greatsword_manual", () -> new StyleManualItem(GreatSwordStyle.ROOT, "equipped"));
    public static final Supplier<Item> DUAL_BLADE_MANUAL = ITEMS.register("dualblade_manual", () -> new StyleManualItem(DualBladeStyle.ROOT, "dual_equipped"));
    public static final Supplier<Item> SWITCH_AXE_MANUAL = ITEMS.register("switchaxe_manual", () -> new StyleManualItem(SwitchAxeStyle.AXE_ROOT, "switchaxe_equipped"));
    static {
        for (GreatSwordMove move : GreatSwordMove.values()) COMBOS.register(move.path, () -> GreatSwordStyle.build(move));
        for (DualBladeMove move : DualBladeMove.values()) COMBOS.register(move.path, () -> DualBladeStyle.build(move));
        for (var move : com.tonywww.slashblade_neomotions.combat.SwitchAxeMove.values()) COMBOS.register(move.path, () -> SwitchAxeStyle.build(move));
    }
    private ModContent() {}
    public static void register(IEventBus bus) {
        COMBOS.register(bus);
        ITEMS.register(bus);
        bus.addListener(EventPriority.LOWEST, ModContent::creative);
        bus.addListener(com.tonywww.slashblade_neomotions.platform.data.NeoMotionsData::gather);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(GREAT_SWORD_MANUAL.get());
            event.accept(DUAL_BLADE_MANUAL.get());
            event.accept(SWITCH_AXE_MANUAL.get());
        }
        var access = event.getParameters().holders();
        // Add this namespace's definitions to their declared tab and the combat tab.
        // SlashBlade also fills the declared tab on clients; keep those entries unique.
        access.lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY).listElements()
                .filter(entry -> entry.key().location().getNamespace().equals(NeoMotions.MOD_ID))
                .sorted(SlashBladeDefinition.COMPARATOR)
                .filter(entry -> event.getTabKey() == CreativeModeTabs.COMBAT
                        || event.getTabKey().location().equals(entry.value().getCreativeGroup()))
                .forEach(entry -> {
                    var blade = PrototypeBlades.create(access, entry.key().location());
                    if (!blade.isEmpty()) acceptUnique(event, blade);
                });
    }
    private static void acceptUnique(BuildCreativeModeTabContentsEvent event, ItemStack blade) {
        //? if forge {
        if (event.getEntries().get(blade) != CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS) {
            event.accept(blade, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        //?} else {
        /*boolean inParent = event.getParentEntries().contains(blade);
        boolean inSearch = event.getSearchEntries().contains(blade);
        if (!inParent && !inSearch) event.accept(blade);
        else if (!inParent) event.accept(blade, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        else if (!inSearch) event.accept(blade, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        *///?}
    }
}

