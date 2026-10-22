package com.tonywww.slashblade_neomotions.combat;

/** Pure gauge transitions. Timings/thresholds come from wp08globalactionparam.
 * Gauge capacity and per-move costs/gains are explicitly Minecraft balance. */
public record SwitchAxeGauge(float slash, float awake, int ampTicks, int axeTicks, int recoveryTicks, int decayTicks) {
    public static final float CAPACITY = 100;
    public static final float MORPH_REQUIRED = parameter("_Transform_RequiredGauge");
    public static final int RECOVERY_INTERVAL = ticks("_SlashGaugeAutoRecoveryInterval");
    public static final int DECAY_INTERVAL = ticks("_SwordAwakeGaugeReduceInterval");
    public static final int AMP_DURATION = ticks("_SwordAwakeTime");
    public static final int AXE_DURATION = ticks("_AxeEnhancedTime");
    public static final float POWER_RATE = parameter("_Bin_Power_AttackRate");
    public SwitchAxeGauge {
        slash = clamp(slash); awake = clamp(awake);
        ampTicks = Math.max(0, Math.min(AMP_DURATION, ampTicks));
        axeTicks = Math.max(0, Math.min(AXE_DURATION, axeTicks));
        recoveryTicks = Math.floorMod(recoveryTicks, RECOVERY_INTERVAL);
        decayTicks = Math.floorMod(decayTicks, DECAY_INTERVAL);
    }
    private static float parameter(String name) { return SourceAction.switchAxeParameter(name); }
    private static int ticks(String name) { return Math.round(parameter(name) * 20); }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(CAPACITY, value)) : 0; }
    public static SwitchAxeGauge initial() { return new SwitchAxeGauge(CAPACITY, 0, 0, 0, 0, 0); }
    public boolean canMorph() { return slash >= MORPH_REQUIRED; }
    public boolean amped() { return ampTicks > 0; }
    public boolean canSpend(float amount) { return slash >= amount; }
    public SwitchAxeGauge spend(float amount) {
        return new SwitchAxeGauge(slash - Math.max(0, amount), awake, ampTicks, axeTicks, recoveryTicks, decayTicks);
    }
    public SwitchAxeGauge advance(int elapsed, boolean sword) {
        elapsed = Math.max(0, Math.min(36000, elapsed));
        int recovery = sword ? 0 : recoveryTicks + elapsed;
        float nextSlash = slash + recovery / RECOVERY_INTERVAL * parameter("_SlashGaugeAutoRecoveryValue");
        int amp = Math.max(0, ampTicks - elapsed), axe = Math.max(0, axeTicks - elapsed);
        // The active amp timer replaces accumulation decay. Expiry clears the bar.
        int decay = amped() ? 0 : decayTicks + elapsed;
        float nextAwake = amped() ? (amp > 0 ? CAPACITY : 0)
                : awake - decay / DECAY_INTERVAL * parameter("_SwordAwakeGaugeReduceValue");
        return new SwitchAxeGauge(nextSlash, nextAwake, amp, axe, recovery, decay);
    }
    public SwitchAxeGauge hit(float ampGain, float slashGain) {
        float nextSlash = slash + slashGain * (axeTicks > 0 ? parameter("_AxeEnhancedSlashGaugeGainRate") : 1);
        float nextAwake = amped() ? CAPACITY : awake + ampGain;
        int amp = ampTicks;
        if (!amped() && nextAwake >= CAPACITY) {
            amp = AMP_DURATION;
            nextSlash += parameter("_OnSwordAwakenSlashGaugeRecovery");
        }
        return new SwitchAxeGauge(nextSlash, nextAwake, amp, axeTicks, recoveryTicks, ampGain > 0 ? 0 : decayTicks);
    }
    public SwitchAxeGauge enhanceAxe() { return new SwitchAxeGauge(slash, awake, ampTicks, AXE_DURATION, recoveryTicks, decayTicks); }
}
