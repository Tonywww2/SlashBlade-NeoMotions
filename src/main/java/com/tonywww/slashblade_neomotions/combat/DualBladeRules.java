package com.tonywww.slashblade_neomotions.combat;

import static com.tonywww.slashblade_neomotions.combat.DualBladeMove.*;

public final class DualBladeRules {
    public static final int SA_TIMEOUT = 54;
    private DualBladeRules() {}
    public static DualBladeMove entry(boolean ground, boolean right, boolean sneak, boolean forward, boolean back) {
        if (!ground) return AERIAL;
        if (right) return SA_PREPARE;
        if (sneak) return back ? RETREAT : forward ? DASH : SPIN;
        return BLADE_CUT;
    }
    public static DualBladeMove follow(DualBladeMove current, long elapsed, boolean right,
                                       boolean sneak, boolean forward, boolean back) {
        if (current == SA_PREPARE || current == AERIAL || elapsed < current.cancelTick) return current;
        if (right) return entry(true, true, sneak, forward, back); // Native SA is an addon extension.
        if (sneak) return elapsed < current.source.window("_ATTACK_01_COMBO", current.cancelTick)
                ? current : entry(true, false, true, forward, back);
        if (elapsed < current.source.window("_ATTACK_00_COMBO", current.cancelTick)) return current;
        return switch (current) {
            case BLADE_CUT -> SHEATH_CUT;
            case SHEATH_CUT, DASH, LANDING -> CROSS_CUT;
            case CROSS_CUT, SPIN -> FLURRY;
            case FLURRY -> FINISHER;
            default -> BLADE_CUT;
        };
    }
}
