package com.tonywww.slashblade_neomotions.platform.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import com.tonywww.slashblade_neomotions.NeoMotions;
import com.tonywww.slashblade_neomotions.animation.WeaponPose;
import com.tonywww.slashblade_neomotions.combat.SwitchAxeStyle;
import com.tonywww.slashblade_neomotions.combat.GreatSwordStyle;
import com.tonywww.slashblade_neomotions.combat.DualBladeStyle;
import com.tonywww.slashblade_neomotions.platform.BladePlatform;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid = NeoMotions.MOD_ID, value = Dist.CLIENT)
*///?}
public final class StyleWeaponLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static AbstractClientPlayer renderingPlayer;
    private static ItemStack firstPersonBlade = ItemStack.EMPTY;
    private static boolean drawingReplacement;
    public StyleWeaponLayer(PlayerRenderer renderer) { super(renderer); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void firstPerson(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.player == null
                || mc.getCameraEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()
                || event.getItemStack() != mc.player.getMainHandItem()
                || !GreatSwordStyle.selected(event.getItemStack()) && !DualBladeStyle.selected(event.getItemStack()) && !SwitchAxeStyle.selected(event.getItemStack())) return;
        event.setCanceled(true);
        if (mc.options.hideGui || mc.player.isSleeping() || mc.player.isSpectator()) return;
        var animation = ((IAnimatedPlayer) mc.player).playerAnimator_getAnimation();
        animation.setTickDelta(event.getPartialTick());
        if ((ClientAnimations.hasStyleAnimation(mc.player) || SwitchAxeStyle.selected(event.getItemStack())) && !nativeHandPose(mc.player)) {
            var state = BladePlatform.state(event.getItemStack()).orElseThrow();
            var model = BladeModelManager.getInstance().getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));
            var texture = state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
            float pitch = Mth.lerp(event.getPartialTick(), mc.player.xRotO, mc.player.getXRot()) * Mth.DEG_TO_RAD;
            float yaw = Mth.rotLerp(event.getPartialTick(), mc.player.yRotO, mc.player.getYRot());
            float bodyYaw = Mth.rotLerp(event.getPartialTick(), mc.player.yBodyRotO, mc.player.yBodyRot);
            boolean dual = DualBladeStyle.selected(event.getItemStack());
            var pose = event.getPoseStack();
            pose.pushPose();
            boolean previousDrawing = drawingReplacement;
            drawingReplacement = true;
            try {
                // RenderHandEvent runs before vanilla swing/equip transforms. Use
                // the live third-person player animation, not a second VMD clock.
                // Do not read PlayerRenderer's model: first person may never update
                // that model, or it may contain the last other player rendered.
                for (boolean left : new boolean[]{false, true}) {
                    if (left && !dual) continue;
                    pose.last().pose().identity();
                    pose.last().normal().identity();
                    multiply(pose, WeaponPose.firstPerson(animation, left, pitch, (yaw - bodyYaw) * Mth.DEG_TO_RAD));
                    String part = left ? "sheath" : mainPart(model, state.isBroken(), SwitchAxeStyle.selected(event.getItemStack()));
                    BladeRenderState.renderOverrided(event.getItemStack(), model, part, texture, pose, event.getMultiBufferSource(), event.getPackedLight());
                    BladeRenderState.renderOverridedLuminous(event.getItemStack(), model, part + "_luminous", texture, pose, event.getMultiBufferSource(), event.getPackedLight());
                    if (!left && SwitchAxeStyle.selected(event.getItemStack()))
                        renderComponents(event.getItemStack(), model, texture, pose, event.getMultiBufferSource(), event.getPackedLight(), animation, state.isBroken(), !ClientAnimations.hasStyleAnimation(mc.player));
                }
            } finally {
                drawingReplacement = previousDrawing;
                pose.popPose();
            }
            return;
        }
        // Native SA and the brief interval before our animation is installed keep
        // SlashBlade's own renderer. Scope its clearance to this hand pass only.
        var previous = firstPersonBlade;
        firstPersonBlade = event.getItemStack();
        try {
            BladeFirstPersonRender.getInstance().render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        } finally { firstPersonBlade = previous; }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderPlayerEvent.Pre event) { renderingPlayer = (AbstractClientPlayer) event.getEntity(); }
    @SubscribeEvent public static void after(RenderPlayerEvent.Post event) { renderingPlayer = null; }
    @SubscribeEvent public static void replaceNativeSheath(RenderOverrideEvent event) {
        boolean great = GreatSwordStyle.selected(event.getStack());
        boolean dual = DualBladeStyle.selected(event.getStack());
        boolean switchaxe = SwitchAxeStyle.selected(event.getStack());
        if (drawingReplacement || !great && !dual && !switchaxe) return;
        if (switchaxe && event.getTarget().startsWith("sheath")) { event.setCanceled(true); return; }
        boolean thirdPerson = renderingPlayer != null && event.getStack() == renderingPlayer.getMainHandItem();
        boolean firstPerson = !firstPersonBlade.isEmpty() && event.getStack() == firstPersonBlade;
        if (thirdPerson && (event.getTarget().startsWith("blade") || event.getTarget().startsWith("sheath")))
            event.setCanceled(true);
        if ((great || switchaxe) && firstPerson && event.getTarget().startsWith("sheath")) event.setCanceled(true);
        boolean firstPersonWeapon = event.getTarget().startsWith("blade")
                || dual && event.getTarget().startsWith("sheath");
        if (firstPerson && !thirdPerson && firstPersonWeapon) {
            event.setCanceled(true);
            var pose = event.getPoseStack();
            pose.pushPose();
            drawingReplacement = true;
            try {
                // Native first-person rendering resets the incoming hand matrix. Apply
                // clearance here, after that reset and the original animated hardpoint.
                // Move down in render space: model-local Y would rotate with the swing.
                var matrix = pose.last().pose();
                matrix.m31(matrix.m31() - WeaponPose.FIRST_PERSON_DROP);
                pose.scale(WeaponPose.FIRST_PERSON_SCALE, WeaponPose.FIRST_PERSON_SCALE, WeaponPose.FIRST_PERSON_SCALE);
                BladeRenderState.renderOverrided(event.getStack(), event.getModel(), event.getTarget(),
                        event.getTexture(), pose, event.getBuffer(), event.getPackedLightIn(),
                        event.getGetRenderType(), event.isEnableEffect());
            } finally {
                drawingReplacement = false;
                pose.popPose();
            }
        }
    }
    private static String mainPart(mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject model, boolean broken, boolean articulated) {
        boolean components = articulated && model.groupObjects.stream().anyMatch(g -> g.name.equals("blade_core"));
        return (components ? "blade_core" : "blade") + (broken ? "_damaged" : "");
    }
    private static final java.util.List<com.tonywww.slashblade_neomotions.combat.SourceAction.ComponentPart> SWITCH_AXE_PARTS =
            com.tonywww.slashblade_neomotions.combat.SourceAction.components("switchaxe");
    private static void renderComponents(ItemStack stack,
            mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject model,
            net.minecraft.resources.ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light,
            dev.kosmx.playerAnim.core.impl.AnimationProcessor animation, boolean broken, boolean restPose) {
        if (restPose) {
            var root = SwitchAxeStyle.sword(stack) ? SwitchAxeStyle.SWORD_ROOT : SwitchAxeStyle.AXE_ROOT;
            Object loaded = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(root);
            if (loaded instanceof dev.kosmx.playerAnim.core.data.KeyframeAnimation data) {
                // Native dodge/other external combos may not define our component
                // tracks. Use the current form's imported rest pose, never zeros.
                animation = new dev.kosmx.playerAnim.core.impl.AnimationProcessor(
                        new dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer(data, 0));
                animation.setTickDelta(0);
            }
        }
        for (var part : SWITCH_AXE_PARTS) {
            pose.pushPose();
            try {
                multiply(pose, WeaponPose.component(animation, part.track()));
                String group = part.group() + (broken ? "_damaged" : "");
                BladeRenderState.renderOverrided(stack, model, group, texture, pose, buffers, light);
                if (SwitchAxeStyle.gauge(stack).amped())
                    BladeRenderState.renderOverridedLuminous(stack, model, group + "_luminous", texture, pose, buffers, light);
            } finally { pose.popPose(); }
        }
    }
    private static void multiply(PoseStack pose, Matrix4f matrix) {
        pose.last().pose().mul(matrix);
        // PoseStack.mulPoseMatrix does not update normals on both target versions.
        // These rig matrices contain only rotations and positive uniform scale
        // (the two-axis model reflection is a proper rotation).
        pose.last().normal().mul(WeaponPose.normal(matrix));
    }
    private static boolean nativeHandPose(AbstractClientPlayer player) {
        return BladePlatform.state(player.getMainHandItem()).map(state -> {
            var combo = BladePlatform.combo(state.getComboSeq());
            return !state.getComboSeq().equals(ComboStateRegistry.NONE.getId())
                    && GreatSwordStyle.move(state.getComboSeq()) == null && DualBladeStyle.move(state.getComboSeq()) == null
                    && SwitchAxeStyle.move(state.getComboSeq()) == null && combo != null
                    && state.getElapsedTime(player) * 50 <= combo.getTimeoutMS();
        }).orElse(false);
    }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                 float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        var stack = player.getMainHandItem();
        boolean great = GreatSwordStyle.selected(stack);
        boolean switchaxe = SwitchAxeStyle.selected(stack);
        if (!great && !DualBladeStyle.selected(stack) && !switchaxe) return;
        var state = BladePlatform.state(stack).orElseThrow();
        var model = BladeModelManager.getInstance().getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));
        var texture = state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
        pose.pushPose();
        drawingReplacement = true;
        try {
            // RenderLayer already inherits PlayerAnimator's global body transform.
            // Applying LayerMainBlade.setUserPose again would rotate/translate twice.
            var animation = ((IAnimatedPlayer) player).playerAnimator_getAnimation();
            animation.setTickDelta(partialTick);
            boolean nativePose = nativeHandPose(player);
            for (boolean left : new boolean[]{false, true}) {
                if (left && (great || switchaxe)) continue;
                pose.pushPose();
                var arm = left ? getParentModel().leftArm : getParentModel().rightArm;
                if (nativePose) {
                    arm.translateAndRotate(pose);
                    pose.translate(0, 9.0 / 16, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(-90));
                    pose.scale(WeaponPose.MODEL_SCALE, WeaponPose.MODEL_SCALE, WeaponPose.MODEL_SCALE);
                }
                else {
                    // Keep the actual wrist origin, with the extracted weapon orientation
                    // in model space. This also respects root blending and partial ticks.
                    var rotation = animation.get3DTransform(left ? "leftWeapon" : "rightWeapon", TransformType.ROTATION, Vec3f.ZERO);
                    multiply(pose, WeaponPose.hand(new Vec3f(arm.x, arm.y, arm.z),
                            new Vec3f(arm.xRot, arm.yRot, arm.zRot), rotation));
                }
                String part = left ? "sheath" : mainPart(model, state.isBroken(), switchaxe);
                BladeRenderState.renderOverrided(stack, model, part, texture, pose, buffers, light);
                BladeRenderState.renderOverridedLuminous(stack, model, part + "_luminous", texture, pose, buffers, light);
                if (!left && switchaxe) renderComponents(stack, model, texture, pose, buffers, light, animation, state.isBroken(), nativePose || !ClientAnimations.hasStyleAnimation(player));
                pose.popPose();
            }
            if (!great) return;
            getParentModel().body.translateAndRotate(pose);
            pose.translate(4.0 / 16, -1.0 / 16, 3.5 / 16);
            pose.mulPose(Axis.ZP.rotationDegrees(-56.7F));
            pose.scale(.0078125F, .0078125F, .0078125F);
            BladeRenderState.renderOverrided(stack, model, "sheath", texture, pose, buffers, light);
            BladeRenderState.renderOverridedLuminous(stack, model, "sheath_luminous", texture, pose, buffers, light);
        } finally {
            drawingReplacement = false;
            pose.popPose();
        }
    }
}
