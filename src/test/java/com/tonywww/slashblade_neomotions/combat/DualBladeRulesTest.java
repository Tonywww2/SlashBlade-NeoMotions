package com.tonywww.slashblade_neomotions.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonywww.slashblade_neomotions.combat.DualBladeMove.*;

class DualBladeRulesTest {
    @Test void normalChainAlternatesThenFinishes() {
        var move = DualBladeRules.entry(true, false, false, false, false);
        assertEquals(BLADE_CUT, move);
        for (var expected : new DualBladeMove[]{SHEATH_CUT, CROSS_CUT, FLURRY, FINISHER, BLADE_CUT}) {
            move = DualBladeRules.follow(move, move.cancelTick, false, false, false, false);
            assertEquals(expected, move);
        }
    }
    @Test void directionalBranchesAndAirHaveDeterministicPriority() {
        assertEquals(SPIN, DualBladeRules.entry(true, false, true, false, false));
        assertEquals(DASH, DualBladeRules.entry(true, false, true, true, false));
        assertEquals(RETREAT, DualBladeRules.entry(true, false, true, true, true));
        assertEquals(SA_PREPARE, DualBladeRules.entry(true, true, true, true, true));
        assertEquals(AERIAL, DualBladeRules.entry(false, true, true, true, true));
        assertEquals(CROSS_CUT, DualBladeRules.follow(DASH, DASH.cancelTick, false, false, false, false));
        assertEquals(SPIN, DualBladeRules.follow(SPIN, 17, false, false, false, false));
        assertEquals(FLURRY, DualBladeRules.follow(SPIN, 18, false, false, false, false));
    }
    @Test void attacksCannotBeCanceledBeforeLastStrikeOrIntoEarlySa() {
        for (var move : values()) {
            if (move == READY) continue;
            assertEquals(move, DualBladeRules.follow(move, move.cancelTick - 1, true, true, true, true));
            for (var strike : move.strikes) assertTrue(strike.tick() < move.cancelTick, move.path);
        }
        assertEquals(SA_PREPARE, DualBladeRules.follow(SA_PREPARE, 100, false, true, true, true));
        assertEquals(AERIAL, DualBladeRules.follow(AERIAL, 100, true, false, false, false));
    }
}
