package com.tonywww.slashblade_neomotions.animation;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared model-space weapon rig. Sampling never advances the animation clock. */
public final class WeaponPose {
    /** Named rigid component in OBJ units, nested under the already scaled hand. */
    public static Matrix4f component(AnimationProcessor animation, String track) {
        var p = animation.get3DTransform(track, dev.kosmx.playerAnim.api.TransformType.POSITION, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
        var r = animation.get3DTransform(track, dev.kosmx.playerAnim.api.TransformType.ROTATION, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
        return new Matrix4f().translation(p.getX(), p.getY(), p.getZ()).rotateZYX(r.getZ(), r.getY(), r.getX());
    }
    public static final float MODEL_SCALE = .0078125F;
    public static final float FIRST_PERSON_SCALE = .65F;
    public static final float FIRST_PERSON_DROP = .18F;
    private static final float PLAYER_SCALE = .9375F;
    private static final float MODEL_OFFSET = 1.501F;

    private WeaponPose() {}

    public static Matrix4f hand(Vec3f position, Vec3f armRotation, Vec3f weaponRotation) {
        // The wrist follows the arm; the extracted weapon rotation is absolute in
        // model space, not relative to the arm. Never apply that rotation twice.
        var arm = new Matrix4f().translate(position.getX() / 16, position.getY() / 16, position.getZ() / 16)
                .rotate(new Quaternionf().rotationZYX(armRotation.getZ(), armRotation.getY(), armRotation.getX()));
        var wrist = arm.transformPosition(new Vector3f(0, 9F / 16, 0));
        return new Matrix4f().translation(wrist)
                .rotate(new Quaternionf().rotationZYX(weaponRotation.getZ(), weaponRotation.getY(), weaponRotation.getX()))
                .scale(MODEL_SCALE);
    }

    public static Matrix3f normal(Matrix4f pose) {
        // Only uniform positive scale and proper rotations occur in this rig.
        // Strip scale directly, avoiding an unnecessary matrix -> quaternion
        // conversion near the 180-degree phases of a full-body turn.
        var scale = pose.getScale(new Vector3f());
        return new Matrix3f(pose).scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
    }

    public static Matrix4f sampleHand(AnimationProcessor animation, boolean left) {
        String arm = left ? "leftArm" : "rightArm";
        return hand(animation.get3DTransform(arm, TransformType.POSITION, new Vec3f(left ? 5 : -5, 2, 0)),
                animation.get3DTransform(arm, TransformType.ROTATION, Vec3f.ZERO),
                animation.get3DTransform(left ? "leftWeapon" : "rightWeapon", TransformType.ROTATION, Vec3f.ZERO));
    }

    public static Matrix4f playerModel(AnimationProcessor animation) {
        // PlayerRendererMixin applies body BEFORE vanilla's reflected model axes,
        // player scale and model offset. The old native first-person path applied
        // it after the reflection, reversing rolls and rotating about another pivot.
        var p = animation.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
        var r = animation.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
        return new Matrix4f().translate(p.getX(), p.getY() + .7F, p.getZ())
                .rotateZYX(r.getZ(), r.getY(), r.getX()).translate(0, -.7F, 0)
                .scale(-PLAYER_SCALE, -PLAYER_SCALE, PLAYER_SCALE).translate(0, -MODEL_OFFSET, 0);
    }

    public static Matrix4f firstPerson(AnimationProcessor animation, boolean left, float pitch, float relativeYaw) {
        // Fixed head-height framing + view clearance. Camera rotations precede
        // the body so a spinning move turns the weapons, never the camera.
        return new Matrix4f().translate(0, -FIRST_PERSON_DROP, -.5F)
                .rotateX(pitch).rotateY(relativeYaw).translate(0, -MODEL_OFFSET * PLAYER_SCALE, 0)
                .mul(playerModel(animation)).mul(sampleHand(animation, left)).scale(FIRST_PERSON_SCALE);
    }
}
