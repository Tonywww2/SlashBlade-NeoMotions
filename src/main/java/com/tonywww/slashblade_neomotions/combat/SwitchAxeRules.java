package com.tonywww.slashblade_neomotions.combat;

import static com.tonywww.slashblade_neomotions.combat.SwitchAxeMove.*;

/** Two source-mode graphs, mapped onto Minecraft L/R/sneak/direction inputs. */
public final class SwitchAxeRules {
    private SwitchAxeRules() {}
    private static SwitchAxeMove primary(SwitchAxeMove current, SwitchAxeMove fallback) {
        String path = current.source.primaryNext();
        for (var move : SwitchAxeMove.values()) if (move.path.equals(path)) return move;
        return fallback;
    }
    public static SwitchAxeMove follow(SwitchAxeMove current, long elapsed, boolean sword, SwitchAxeGauge gauge,
                                       boolean right, boolean sneak, boolean forward, boolean back) {
        String field = right && !sneak ? "_MODE_CHANGE" : sword
                ? right ? "_SWORD_DISCHARGE_STAB" : sneak ? "_SWORD_01_COMBO" : "_SWORD_00_COMBO"
                : sneak ? "_AXE_01_COMBO" : "_AXE_00_COMBO";
        int window = current.source.window(field, current.cancelTick);
        if (!current.ready() && elapsed < Math.max(window, current.source.modeChangeTick())) return current;
        if (right && !sneak) return sword ? MORPH_AXE : gauge.canMorph() ? MORPH_SWORD : current;
        SwitchAxeMove result;
        if (sword) {
            if (right) result = gauge.amped() && gauge.canSpend(FULL_RELEASE.cost) ? FULL_RELEASE : DISCHARGE;
            else if (sneak) result = current == SWORD_DOUBLE ? SWORD_HEAVEN : SWORD_DOUBLE;
            else result = primary(current, SWORD_OVERHEAD);
        } else {
            if (right) result = AXE_SPIRAL;
            else if (sneak) result = back ? AXE_FADE : forward ? AXE_FORWARD : AXE_WILD;
            else if (back) result = AXE_RISING;
            else result = current == AXE_WILD ? AXE_SLAM : primary(current, AXE_OVERHEAD);
        }
        return sword && !gauge.canSpend(result.cost) ? MORPH_AXE : result;
    }
}
