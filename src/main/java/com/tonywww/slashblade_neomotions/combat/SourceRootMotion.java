package com.tonywww.slashblade_neomotions.combat;

import net.minecraft.world.entity.LivingEntity;

/** Apply source trajectory through normal entity physics (walls still collide). */
final class SourceRootMotion {
    private SourceRootMotion() {}
    static void apply(LivingEntity user, SourceAction source, long elapsed, float yaw) {
        if (elapsed < 1 || elapsed > source.duration || !user.onGround()) return;
        double x = source.rootDelta((int) elapsed, 0), z = source.rootDelta((int) elapsed, 1);
        if (Math.abs(x) + Math.abs(z) < 0.0001) return;
        double radians = Math.toRadians(yaw), c = Math.cos(radians), s = Math.sin(radians);
        double length = Math.hypot(x, z), limit = Math.min(1, 0.45 / Math.max(0.0001, length));
        user.setDeltaMovement((x * c - z * s) * limit, user.getDeltaMovement().y, (x * s + z * c) * limit);
        user.hurtMarked = true;
    }
}
