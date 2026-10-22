package com.tonywww.slashblade_neomotions.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonywww.slashblade_neomotions.combat.SwitchAxeMove.*;

class SwitchAxeRulesTest {
    private SwitchAxeMove input(SwitchAxeMove current, long tick, boolean sword, SwitchAxeGauge gauge, boolean right, boolean sneak) {
        return SwitchAxeRules.follow(current, tick, sword, gauge, right, sneak, false, false);
    }
    @Test void morphRequiresTheSourceThresholdButReturningToAxeDoesNot() {
        var low = SwitchAxeGauge.initial().spend(70.01F);
        assertFalse(low.canMorph());
        assertEquals(AXE_READY, input(AXE_READY, 0, false, low, true, false));
        assertEquals(MORPH_SWORD, input(AXE_READY, 0, false, SwitchAxeGauge.initial().spend(70), true, false));
        assertEquals(MORPH_AXE, input(SWORD_READY, 0, true, low.spend(100), true, false));
    }
    @Test void recoveryUsesDiscreteSourceIntervalsAndStopsWhileSwordIsHeld() {
        var empty = SwitchAxeGauge.initial().spend(100);
        assertEquals(0, empty.advance(39, false).slash());
        assertEquals(3, empty.advance(39, false).advance(1, false).slash());
        assertEquals(6, empty.advance(80, false).slash());
        assertEquals(0, empty.advance(80, true).slash());
        assertEquals(100, empty.advance(36000, false).slash());
    }
    @Test void hitsBuildAmpWhileElapsedTimeDoesNotAndActivationIsNotRefreshable() {
        var gauge = SwitchAxeGauge.initial().spend(80);
        assertEquals(0, gauge.advance(200, true).awake());
        gauge = gauge.hit(90, 0).hit(10, 0);
        assertEquals(900, gauge.ampTicks()); assertEquals(50, gauge.slash());
        gauge = gauge.advance(400, true).hit(80, 0);
        assertEquals(500, gauge.ampTicks(), "Further hits must not renew the 45-second timer");
        gauge = gauge.advance(500, true);
        assertFalse(gauge.amped()); assertEquals(0, gauge.awake());
        assertEquals(10, gauge.hit(10, 0).awake());
    }
    @Test void inactiveAmpDecaysAndAxeBuffExpiresAtItsSourceDuration() {
        var gauge = SwitchAxeGauge.initial().spend(100).hit(20, 0);
        assertEquals(20, gauge.advance(79, true).awake());
        assertEquals(18, gauge.advance(80, true).awake());
        gauge = gauge.enhanceAxe();
        assertEquals(12, gauge.hit(0, 10).slash(), .001);
        assertEquals(900, gauge.axeTicks());
        assertEquals(0, gauge.advance(900, true).axeTicks());
    }
    @Test void swordChainsRespectSourceWindowsAndDepletionRoutesToAxe() {
        var full = SwitchAxeGauge.initial();
        assertEquals(SWORD_OVERHEAD, input(SWORD_OVERHEAD, 0, true, full, false, false));
        assertEquals(SWORD_RIGHT, input(SWORD_OVERHEAD, 100, true, full, false, false));
        assertEquals(SWORD_LEFT, input(SWORD_RIGHT, 100, true, full, false, false));
        assertEquals(SWORD_OVERHEAD, input(SWORD_LEFT, 100, true, full, false, false));
        assertEquals(SWORD_HEAVEN, input(SWORD_DOUBLE, 100, true, full, false, true));
        assertEquals(MORPH_AXE, input(SWORD_READY, 0, true, full.spend(97), false, false));
        assertEquals(MORPH_SWORD, input(MORPH_SWORD, 22, true, full, false, false), "Do not branch before the mode commit");
    }
    @Test void basicChainsComeFromTheExtractedActionGuide() {
        var gauge = SwitchAxeGauge.initial();
        assertEquals(AXE_SIDE, input(AXE_OVERHEAD, 100, false, gauge, false, false));
        assertEquals(AXE_SPIRAL, input(AXE_SIDE, 100, false, gauge, false, false));
        assertEquals(AXE_OVERHEAD, input(AXE_SPIRAL, 100, false, gauge, false, false));
        assertEquals(SWORD_RIGHT, input(SWORD_HEAVEN, 100, true, gauge, false, false));
    }
    @Test void fullReleaseRequiresAmpAndEnoughGaugeAndReturnsToAxe() {
        var full = SwitchAxeGauge.initial();
        assertEquals(DISCHARGE, input(SWORD_READY, 0, true, full, true, true));
        assertEquals(FULL_RELEASE, input(SWORD_READY, 0, true, full.hit(100, 0), true, true));
        assertEquals(DISCHARGE, input(SWORD_READY, 0, true, full.hit(100, 0).spend(75), true, true));
        assertEquals(MORPH_AXE, input(SWORD_READY, 0, true, full.spend(81), true, true));
        assertTrue(FULL_RELEASE.endsInAxe()); assertTrue(DISCHARGE.endsInAxe());
        assertEquals(23, MORPH_SWORD.source.modeChangeTick());
        assertEquals(17, MORPH_AXE.source.modeChangeTick());
    }
}
