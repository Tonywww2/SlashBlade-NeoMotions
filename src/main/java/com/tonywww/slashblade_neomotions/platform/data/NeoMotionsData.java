package com.tonywww.slashblade_neomotions.platform.data;

import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import com.tonywww.slashblade_neomotions.platform.PrototypeBlades;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.registry.slashblade.*;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Set;
//? if forge {
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
//?} else {
/*import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
*///?}

public final class NeoMotionsData {
    private NeoMotionsData() {}
    public static void gather(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var lookup = event.getLookupProvider();
        var builder = new RegistrySetBuilder().add(SlashBladeDefinition.REGISTRY_KEY, context -> {
            //? if <1.21 {
            var enchantment = new EnchantmentDefinition(BladePlatform.id("minecraft", "unbreaking"), 1);
            //?} else {
            /*var enchantment = new EnchantmentDefinition(context.lookup(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 1);
            *///?}
            context.register(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, PrototypeBlades.GREAT_SWORD),
                    definition(PrototypeBlades.GREAT_SWORD, GreatSwordStyle.ROOT, 6, 600, "drive_vertical", 0x9DB9CF, enchantment));
            context.register(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, PrototypeBlades.DUAL_BLADE),
                    definition(PrototypeBlades.DUAL_BLADE, DualBladeStyle.ROOT, 4, 400, "circle_slash", 0xCEAB68, enchantment));
            context.register(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, PrototypeBlades.SWITCH_AXE),
                    definition(PrototypeBlades.SWITCH_AXE, SwitchAxeStyle.AXE_ROOT, 5, 550, "drive_vertical", 0xE8B85B, enchantment));
        });
        generator.addProvider(event.includeServer(), new DatapackBuiltinEntriesProvider(output, lookup, builder, Set.of(NeoMotions.MOD_ID)));
        generator.addProvider(event.includeServer(), new NeoMotionsRecipes(output, lookup));
    }
    private static SlashBladeDefinition definition(ResourceLocation name, ResourceLocation root, float attack,
                                                    int durability, String art, int color, EnchantmentDefinition enchantment) {
        return new SlashBladeDefinition(name,
                RenderDefinition.Builder.newInstance()
                        .modelName(BladePlatform.id("model/" + name.getPath() + ".obj"))
                        .textureName(BladePlatform.id("model/prototype_metal.png"))
                        .standbyRenderType(CarryType.PSO2).effectColor(color).build(),
                PropertiesDefinition.Builder.newInstance().rootComboState(root)
                        .baseAttackModifier(attack).maxDamage(durability)
                        .slashArtsType(BladePlatform.id("slashblade", art)).build(),
                List.of(enchantment));
    }
}
