package com.tonywww.slashblade_neomotions.platform.data;

import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import com.tonywww.slashblade_neomotions.platform.ModContent;
import com.tonywww.slashblade_neomotions.platform.PrototypeBlades;
import mods.flammpfeil.slashblade.recipe.SlashBladeShapedRecipeBuilder;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import java.util.concurrent.CompletableFuture;
//? if <1.21 {
import net.minecraft.data.recipes.FinishedRecipe;
import java.util.function.Consumer;
//?} else {
/*import net.minecraft.data.recipes.RecipeOutput;
*///?}

public final class NeoMotionsRecipes extends RecipeProvider {
    public NeoMotionsRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        //? if <1.21 {
        super(output);
        //?} else {
        /*super(output, lookup);
        *///?}
    }
    //? if <1.21 {
    @Override protected void buildRecipes(Consumer<FinishedRecipe> output) {
    //?} else {
    /*@Override protected void buildRecipes(RecipeOutput output) {
    *///?}
        ShapelessRecipeBuilder.shapeless(RecipeCategory.COMBAT, ModContent.GREAT_SWORD_MANUAL.get())
                .requires(Items.BOOK).requires(Items.IRON_BLOCK).requires(SlashBladeItems.PROUDSOUL.get())
                .unlockedBy("has_proudsoul", has(SlashBladeItems.PROUDSOUL.get()))
                .save(output, BladePlatform.id("greatsword_manual"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.COMBAT, ModContent.DUAL_BLADE_MANUAL.get())
                .requires(Items.BOOK).requires(Items.IRON_SWORD, 2).requires(SlashBladeItems.PROUDSOUL.get())
                .unlockedBy("has_proudsoul", has(SlashBladeItems.PROUDSOUL.get()))
                .save(output, BladePlatform.id("dualblade_manual"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.COMBAT, ModContent.SWITCH_AXE_MANUAL.get())
                .requires(Items.BOOK).requires(Items.IRON_AXE).requires(SlashBladeItems.PROUDSOUL.get())
                .unlockedBy("has_proudsoul", has(SlashBladeItems.PROUDSOUL.get()))
                .save(output, BladePlatform.id("switchaxe_manual"));
        SlashBladeShapedRecipeBuilder.shaped(PrototypeBlades.SWITCH_AXE)
                .pattern(" IA").pattern(" B ").pattern("RPR")
                .define('I', Items.IRON_BLOCK).define('A', Items.IRON_AXE)
                .define('B', SlashBladeItems.SLASHBLADE.get()).define('R', Items.REDSTONE)
                .define('P', SlashBladeItems.PROUDSOUL_INGOT.get())
                .unlockedBy("has_blade", has(SlashBladeItems.SLASHBLADE.get()))
                .save(output, PrototypeBlades.SWITCH_AXE);
        SlashBladeShapedRecipeBuilder.shaped(PrototypeBlades.GREAT_SWORD)
                .pattern(" I ").pattern("IBI").pattern(" P ")
                .define('I', Items.IRON_BLOCK).define('B', SlashBladeItems.SLASHBLADE.get())
                .define('P', SlashBladeItems.PROUDSOUL_INGOT.get())
                .unlockedBy("has_blade", has(SlashBladeItems.SLASHBLADE.get()))
                .save(output, PrototypeBlades.GREAT_SWORD);
        SlashBladeShapedRecipeBuilder.shaped(PrototypeBlades.DUAL_BLADE)
                .pattern("I I").pattern(" B ").pattern("LPL")
                .define('I', Items.IRON_INGOT).define('B', SlashBladeItems.SLASHBLADE.get())
                .define('L', Items.LEATHER).define('P', SlashBladeItems.PROUDSOUL_INGOT.get())
                .unlockedBy("has_blade", has(SlashBladeItems.SLASHBLADE.get()))
                .save(output, PrototypeBlades.DUAL_BLADE);
    }
}
