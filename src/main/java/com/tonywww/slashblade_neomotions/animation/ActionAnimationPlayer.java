package com.tonywww.slashblade_neomotions.animation;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.joml.Quaternionf;

/** Preserve the outgoing support stance when the combat state machine branches.
 * Root and legs blend; the root carries body AND native weapon together.
 * Hand/torso tracks remain synchronized with their VMD. */
public final class ActionAnimationPlayer extends KeyframeAnimationPlayer {
    private final int blendTicks;
    private final Vec3f[][] outgoing = new Vec3f[3][2];
    private final boolean holdFinalPose;
    private int transitionAge;
    private float partialTick;

    public ActionAnimationPlayer(KeyframeAnimation data, int elapsed, KeyframeAnimationPlayer previous, boolean holdFinalPose) {
        this(data, elapsed, previous, holdFinalPose, 4);
    }
    public ActionAnimationPlayer(KeyframeAnimation data, int elapsed, KeyframeAnimationPlayer previous, boolean holdFinalPose, int blendTicks) {
        super(data, holdFinalPose ? Math.min(elapsed, data.endTick) : elapsed);
        this.blendTicks = blendTicks;
        this.holdFinalPose = holdFinalPose;
        this.transitionAge = elapsed;
        if (previous != null) {
            for (int side = 0; side < 3; side++) {
                String name = side == 0 ? "rightLeg" : side == 1 ? "leftLeg" : "body";
                outgoing[side][0] = previous.get3DTransform(name, TransformType.ROTATION, 0, Vec3f.ZERO);
                outgoing[side][1] = previous.get3DTransform(name, TransformType.POSITION, 0,
                        side == 2 ? Vec3f.ZERO : new Vec3f(side == 0 ? -1.9f : 1.9f, 12, .1f));
            }
        }
    }

    @Override public void tick() {
        transitionAge++;
        // Airborne/charge holds end only when the server changes the combo state.
        if (!holdFinalPose || getCurrentTick() < getData().endTick) super.tick();
    }

    @Override public void setupAnim(float partialTick) {
        this.partialTick = partialTick;
        super.setupAnim(holdFinalPose && getCurrentTick() >= getData().endTick ? 0 : partialTick);
    }

    @Override public Vec3f get3DTransform(String name, TransformType type, float tickDelta, Vec3f vanilla) {
        var target = super.get3DTransform(name, type, tickDelta, vanilla);
        // Euler component interpolation can take a long detour through a wrist flip.
        // Sample the existing PlayerAnimator keys, then interpolate their orientation
        // on the sphere, matching the source converter and native VMD sampling.
        if (type == TransformType.ROTATION && this.tickDelta > 0 && this.tickDelta < 1) {
            float fraction = this.tickDelta;
            try {
                this.tickDelta = 0;
                var a = super.get3DTransform(name, type, 0, vanilla);
                this.tickDelta = 1;
                var b = super.get3DTransform(name, type, 1, vanilla);
                target = interpolateRotation(a, b, fraction);
            } finally { this.tickDelta = fraction; }
        }
        int side = name.equals("rightLeg") ? 0 : name.equals("leftLeg") ? 1 : name.equals("body") ? 2 : -1;
        if (side < 0 || transitionAge >= blendTicks || type != TransformType.ROTATION && type != TransformType.POSITION)
            return target;
        var from = outgoing[side][type == TransformType.ROTATION ? 0 : 1];
        if (from == null) from = vanilla;
        float blend = Math.min(1, (transitionAge + partialTick) / blendTicks);
        blend = blend * blend * (3 - 2 * blend);
        if (type == TransformType.ROTATION) {
            return interpolateRotation(from, target, blend);
        }
        return new Vec3f(lerp(from.getX(), target.getX(), blend), lerp(from.getY(), target.getY(), blend),
                lerp(from.getZ(), target.getZ(), blend));
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static Vec3f interpolateRotation(Vec3f from, Vec3f to, float t) {
        var a = new Quaternionf().rotationZYX(from.getZ(), from.getY(), from.getX());
        var b = new Quaternionf().rotationZYX(to.getZ(), to.getY(), to.getX());
        a.slerp(b, t).normalize();
        // JOML 1.10.5 (Forge) has the wrong Y² sign in getEulerAnglesZYX's
        // pitch denominator. Use the corrected conversion on both loaders.
        return new Vec3f(
                (float) Math.atan2(2 * (a.w * a.x + a.y * a.z), 1 - 2 * (a.x * a.x + a.y * a.y)),
                (float) Math.asin(Math.max(-1, Math.min(1, 2 * (a.w * a.y - a.z * a.x)))),
                (float) Math.atan2(2 * (a.w * a.z + a.x * a.y), 1 - 2 * (a.y * a.y + a.z * a.z)));
    }
}
