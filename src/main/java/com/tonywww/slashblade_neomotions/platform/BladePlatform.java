package com.tonywww.slashblade_neomotions.platform;

import com.tonywww.slashblade_neomotions.NeoMotions;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.EnumSet;
import java.util.Optional;
//? if >=1.21 {
/*import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
*///?}

public final class BladePlatform {
    private static final String PREVIOUS_ROOT = "NeoMotionsPreviousRoot";
    private BladePlatform() {}

    public static ResourceLocation id(String path) { return id(NeoMotions.MOD_ID, path); }
    public static ResourceLocation id(String namespace, String path) {
        //? if <1.21 {
        return new ResourceLocation(namespace, path);
        //?} else {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?}
    }
    public static Optional<ISlashBladeState> state(ItemStack stack) {
        //? if forge {
        return stack.getCapability(ItemSlashBlade.BLADESTATE).resolve();
        //?} else {
        /*return BladeStateAccess.of(stack);
        *///?}
    }
    public static EnumSet<InputCommand> input(LivingEntity user) {
        //? if forge {
        return user.getCapability(CapabilityInputState.INPUT_STATE).map(s -> s.getCommands(user))
                .orElseGet(() -> EnumSet.noneOf(InputCommand.class));
        //?} else {
        /*return user.getData(CapabilityInputState.INPUT_STATE.get()).getCommands(user);
        *///?}
    }
    public static ComboState combo(ResourceLocation id) {
        //? if forge {
        return ComboStateRegistry.REGISTRY.get().getValue(id);
        //?} else {
        /*return ComboStateRegistry.REGISTRY.get(id);
        *///?}
    }
    public static void damageBlade(LivingEntity user) {
        //? if <1.21 {
        user.getMainHandItem().hurtAndBreak(1, user, entity -> entity.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        //?} else {
        /*user.getMainHandItem().hurtAndBreak(1, user, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        *///?}
    }
    /** Reuse the blade's SA validation, events, soul/durability cost and swing exactly once. */
    public static void releaseSlashArt(LivingEntity user, ItemStack stack, int chargeTicks) {
        //? if <1.21 {
        int duration = stack.getUseDuration();
        //?} else {
        /*int duration = stack.getUseDuration(user);
        *///?}
        stack.getItem().releaseUsing(stack, user.level(), user, duration - chargeTicks);
    }
    public static void savePreviousRoot(ItemStack stack, ResourceLocation root) {
        CompoundTag data = customData(stack);
        data.putString(PREVIOUS_ROOT, root.toString());
        setCustomData(stack, data);
    }
    public static ResourceLocation takePreviousRoot(ItemStack stack) {
        CompoundTag data = customData(stack);
        ResourceLocation previous = ResourceLocation.tryParse(data.getString(PREVIOUS_ROOT));
        data.remove(PREVIOUS_ROOT);
        setCustomData(stack, data);
        return previous != null && combo(previous) != null ? previous : PrototypeBlades.defaultRoot(stack);
    }
    private static CompoundTag customData(ItemStack stack) {
        //? if <1.21 {
        return stack.getOrCreateTag().copy();
        //?} else {
        /*return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        *///?}
    }
    /** Loader-neutral, stack-owned state. Vanilla inventory/equipment sync carries it to clients. */
    public static CompoundTag data(ItemStack stack, String key) { return customData(stack).getCompound(key).copy(); }
    public static void data(ItemStack stack, String key, CompoundTag value) {
        var data = customData(stack);
        data.put(key, value);
        setCustomData(stack, data);
    }
    private static void setCustomData(ItemStack stack, CompoundTag data) {
        //? if <1.21 {
        stack.setTag(data);
        //?} else {
        /*stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        *///?}
    }
}

