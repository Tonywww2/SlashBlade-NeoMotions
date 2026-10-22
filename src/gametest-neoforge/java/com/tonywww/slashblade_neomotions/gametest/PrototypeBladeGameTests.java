package com.tonywww.slashblade_neomotions.gametest;

import com.mojang.authlib.GameProfile;
import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.combat.*;
import com.tonywww.slashblade_neomotions.platform.*;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.registry.*;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.SlashBladeCreativeGroup;
import mods.flammpfeil.slashblade.recipe.SlashBladeShapedRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(NeoMotions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrototypeBladeGameTests {
    private static final List<ResourceLocation> IDS = List.of(PrototypeBlades.GREAT_SWORD, PrototypeBlades.DUAL_BLADE);
    private static ItemStack make(GameTestHelper h, ResourceLocation id) { return PrototypeBlades.create(h.getLevel().registryAccess(), id); }
    private static ItemStack roundTrip(GameTestHelper h, ItemStack stack) { return ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow(); }
    @GameTest(template="empty")
    public static void creativeTabsContainBothPrototypesAndSearchEntries(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        for (var tabId : List.of(CreativeModeTabs.COMBAT.location(), SlashBladeCreativeGroup.SLASHBLADE_GROUP.getId())) {
            var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
            for (boolean permissions : new boolean[]{false, true, false}) {
                tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), permissions, access));
                for (var id : IDS) {
                    String name = "item.slashblade_neomotions." + id.getPath();
                    long visible = tab.getDisplayItems().stream().filter(stack -> BladePlatform.state(stack)
                            .map(state -> state.getTranslationKey().equals(name)).orElse(false)).count();
                    long searchable = tab.getSearchTabDisplayItems().stream().filter(stack -> BladePlatform.state(stack)
                            .map(state -> state.getTranslationKey().equals(name)).orElse(false)).count();
                    h.assertTrue(visible == 1, tabId + " must contain exactly one " + id + ", got " + visible);
                    h.assertTrue(searchable == 1, tabId + " search must contain exactly one " + id + ", got " + searchable);
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void creativeEntriesAlreadyAddedBySlashBladeRemainUnique(GameTestHelper h) {
        var tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, SlashBladeCreativeGroup.SLASHBLADE_GROUP.getId());
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabKey);
        var parameters = new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess());
        for (var initialVisibility : CreativeModeTab.TabVisibility.values()) {
            var parent = new HashMap<String, Integer>();
            var search = new HashMap<String, Integer>();
            // Exercise the loader's real event dispatch with entries supplied before our handler.
            EventHooks.onCreativeModeTabBuildContents(tab, tabKey,
                    (params, output) -> IDS.forEach(id -> output.accept(make(h, id), initialVisibility)),
                    parameters, (stack, visibility) -> BladePlatform.state(stack).ifPresent(state -> {
                        var name = state.getTranslationKey();
                        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) parent.merge(name, 1, Integer::sum);
                        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY) search.merge(name, 1, Integer::sum);
                    }));
            for (var id : IDS) {
                String name = "item.slashblade_neomotions." + id.getPath();
                h.assertTrue(parent.getOrDefault(name, 0) == 1, "Duplicate or missing parent entry after " + initialVisibility);
                h.assertTrue(search.getOrDefault(name, 0) == 1, "Duplicate or missing search entry after " + initialVisibility);
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void generatedDefinitionsProvideDistinctModelsAndFactoryStyles(GameTestHelper h) {
        for (var id : IDS) {
            var stack = make(h, id); var state = BladePlatform.state(stack).orElseThrow();
            boolean greatsword = id.equals(PrototypeBlades.GREAT_SWORD);
            h.assertTrue(state.getComboRoot().equals(greatsword ? GreatSwordStyle.ROOT : DualBladeStyle.ROOT), "Missing factory style");
            h.assertTrue(state.getModel().orElseThrow().equals(BladePlatform.id("model/"+id.getPath()+".obj")), "Incorrect model");
            h.assertTrue(state.getTexture().orElseThrow().equals(BladePlatform.id("model/prototype_metal.png")), "Incorrect texture");
            h.assertTrue(state.getTranslationKey().equals("item.slashblade_neomotions."+id.getPath()), "Incorrect name key");
            h.assertTrue(stack.isEnchanted() && !state.isBroken() && !state.isSealed(), "Prototype SA cannot be used out of the box");
            h.assertTrue(stack.getMaxDamage() == (greatsword ? 600 : 400), "Durability mismatch");
            var definition = h.getLevel().registryAccess().registryOrThrow(SlashBladeDefinition.REGISTRY_KEY).get(id);
            h.assertTrue(definition.getCreativeGroup().equals(SlashBladeCreativeGroup.SLASHBLADE_GROUP.getId()), "Missing SlashBlade creative entry");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void saveLoadAndManualRestoreKeepFactoryIdentity(GameTestHelper h) {
        for (var id : IDS) {
            var original = make(h,id); var originalState = BladePlatform.state(original).orElseThrow();
            var expectedRoot = originalState.getComboRoot();
            originalState.setKillCount(42); originalState.setProudSoulCount(123); originalState.setRefine(3);
            var blade = roundTrip(h,original);
            GreatSwordStyle.apply(blade,false); DualBladeStyle.apply(blade,false); GreatSwordStyle.apply(blade,false);
            blade = roundTrip(h,blade);
            DualBladeStyle.apply(blade,true);
            var state = BladePlatform.state(blade).orElseThrow();
            h.assertTrue(state.getComboRoot().equals(expectedRoot), "Restoration lost the prototype's native tree");
            h.assertTrue(state.getModel().equals(originalState.getModel()) && state.getTranslationKey().equals(originalState.getTranslationKey()), "Model/name lost in save");
            h.assertTrue(state.getKillCount()==42 && state.getProudSoulCount()==123 && state.getRefine()==3, "Saved blade progression lost");
        }
        h.succeed();
    }
    private static ItemStack craft(GameTestHelper h, ResourceLocation id, List<ItemStack> ingredients) {
        var recipe = (SlashBladeShapedRecipe) h.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
        var input = CraftingInput.of(3,3,ingredients);
        h.assertTrue(recipe.matches(input,h.getLevel()), "Generated pattern did not match");
        return recipe.assemble(input,h.getLevel().registryAccess());
    }
    @GameTest(template="empty")
    public static void generatedRecipesCraftBladesAndPreserveProgression(GameTestHelper h) {
        for (var id : IDS) {
            var source = new ItemStack(SlashBladeItems.SLASHBLADE.get());
            var old = BladePlatform.state(source).orElseThrow(); old.setNonEmpty();
            old.setKillCount(42); old.setProudSoulCount(123); old.setRefine(3);
            var ingredients = new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));
            ingredients.set(4,source); ingredients.set(7,SlashBladeItems.PROUDSOUL_INGOT.get().getDefaultInstance());
            if(id.equals(PrototypeBlades.GREAT_SWORD)) {
                for(int i:new int[]{1,3,5}) ingredients.set(i,new ItemStack(Items.IRON_BLOCK));
            } else {
                for(int i:new int[]{0,2}) ingredients.set(i,new ItemStack(Items.IRON_INGOT));
                for(int i:new int[]{6,8}) ingredients.set(i,new ItemStack(Items.LEATHER));
            }
            var result=craft(h,id,ingredients); var state=BladePlatform.state(result).orElseThrow();
            h.assertTrue(state.getTranslationKey().equals("item.slashblade_neomotions."+id.getPath()),"Recipe returned a plain blade");
            h.assertTrue(state.getComboRoot().equals(PrototypeBlades.defaultRoot(result)),"Crafted blade lost its style");
            h.assertTrue(state.getKillCount()==42 && state.getProudSoulCount()==123 && state.getRefine()==3,"Recipe discarded input progression");
            h.assertTrue(result.isEnchanted(),"Generated default enchantment missing");
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void factoryStylesCanReleaseTheirConfiguredSlashArts(GameTestHelper h) {
        for(var id:IDS) {
            var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"PrototypeTest"));
            p.setItemSlot(EquipmentSlot.MAINHAND,make(h,id)); p.setOnGround(true);
            var state=BladePlatform.state(p.getMainHandItem()).orElseThrow(); state.setProudSoulCount(100);
            boolean greatsword=id.equals(PrototypeBlades.GREAT_SWORD);
            var charge=greatsword ? GreatSwordStyle.id(GreatSwordMove.CHARGE_1) : DualBladeStyle.id(DualBladeMove.SA_PREPARE);
            state.updateComboSeq(p,charge); state.setLastActionTime(p.level().getGameTime()-state.getFullChargeTicks(p));
            BladePlatform.combo(charge).clickAction(p); p.startUsingItem(InteractionHand.MAIN_HAND);
            if(greatsword) GreatSwordStyle.prepareSlashArt(p);
            p.releaseUsingItem();
            h.assertTrue(state.getComboSeq().equals(greatsword ? ComboStateRegistry.DRIVE_VERTICAL.getId() : ComboStateRegistry.CIRCLE_SLASH.getId()),"Factory SA did not execute");
            h.assertTrue(state.getProudSoulCount()<100,"Factory SA bypassed native cost");
            StyleSelection.clear(p);p.discard();
        }
        h.succeed();
    }
}
