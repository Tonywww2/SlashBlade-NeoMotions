package com.tonywww.slashblade_neomotions.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonywww.slashblade_neomotions.combat.GreatSwordMove.*;

class GreatSwordRulesTest {
    @Test void chargeWindowsAndOvercharge() {
        assertEquals(0,GreatSwordRules.chargeLevel(15));assertEquals(1,GreatSwordRules.chargeLevel(16));
        assertEquals(1,GreatSwordRules.chargeLevel(30));assertEquals(2,GreatSwordRules.chargeLevel(31));
        assertEquals(2,GreatSwordRules.chargeLevel(45));assertEquals(3,GreatSwordRules.chargeLevel(46));
        assertEquals(3,GreatSwordRules.chargeLevel(59));assertEquals(2,GreatSwordRules.chargeLevel(60));
    }
    @Test void chargeChainAndTackleSkip() {
        assertEquals(CHARGE_2,GreatSwordRules.follow(CHARGED_SLASH,16,true,false));
        assertEquals(CHARGE_2,GreatSwordRules.follow(OVERHEAD,16,true,false));
        assertEquals(CHARGE_3,GreatSwordRules.follow(STRONG_SLASH,20,true,false));
        assertEquals(CHARGE_2,GreatSwordRules.follow(TACKLE_1,14,true,false));
        assertEquals(CHARGE_3,GreatSwordRules.follow(TACKLE_3,14,true,false));
        assertEquals(CHARGE_1,GreatSwordRules.follow(TRUE_SLASH,62,true,false));
        assertEquals(LEAPING_SWEEP,GreatSwordRules.follow(TACKLE_1,14,false,false));
        assertEquals(STRONG_SWEEP,GreatSwordRules.follow(STRONG_SLASH,20,false,false));
        assertEquals(CHARGE_3,GreatSwordRules.follow(STRONG_SWEEP,28,true,false));
        assertEquals(11,GreatSwordRules.preFor(CHARGED_SLASH,CHARGE_2));
    }
    @Test void slashArtPreparationIsAvailableFromEachChargeAndDoesNotRestart() {
        for (int stage = 1; stage <= 3; stage++) {
            var charge = GreatSwordMove.charge(stage);
            var sa = GreatSwordMove.prepareSlashArt(stage);
            assertEquals(sa, GreatSwordRules.follow(charge, 5, false, false));
            assertEquals(charge, GreatSwordRules.follow(charge, 5, true, false));
            assertEquals(sa, GreatSwordRules.follow(sa, 20, false, false));
            assertEquals(sa, GreatSwordRules.follow(sa, 20, true, false));
            assertEquals(stage, sa.stage());
        }
    }
    @Test void recoveryCannotBeSkippedByClickSpam() {
        for (GreatSwordMove move : values()) if (!move.charging() && move.cancelTick>0) {
            assertSame(move,GreatSwordRules.follow(move,move.cancelTick-1,true,false));
            assertSame(move,GreatSwordRules.follow(move,move.cancelTick-1,false,true));
        }
    }
    @Test void entryPriorityAndNormalChain() {
        assertEquals(AERIAL,GreatSwordRules.entry(false,true,true,true));
        assertEquals(RISING,GreatSwordRules.entry(true,true,true,true));
        assertEquals(SWEEP,GreatSwordRules.entry(true,false,true,false));
        assertEquals(CHARGE_1,GreatSwordRules.entry(true,true,false,false));
        assertEquals(OVERHEAD,GreatSwordRules.entry(true,false,false,false));
        assertEquals(SWEEP,GreatSwordRules.follow(OVERHEAD,16,false,false));
        assertEquals(RISING,GreatSwordRules.follow(SWEEP,26,false,false));
    }
}
