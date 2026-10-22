package com.tonywww.slashblade_neomotions.combat;

/** Pure rules, shared by both loaders and unit tests. */
public final class GreatSwordRules {
    public static final int CHARGE_1_TICK = SourceAction.chargeThreshold(1),
            CHARGE_2_TICK = SourceAction.chargeThreshold(2), CHARGE_3_TICK = SourceAction.chargeThreshold(3),
            OVERCHARGE_TICK = SourceAction.overheatTick(), AUTO_RELEASE_TICK = OVERCHARGE_TICK;
    private GreatSwordRules() {}

    public static int chargeLevel(long ticks) {
        if (ticks >= OVERCHARGE_TICK) return SourceAction.overheatLevel();
        if (ticks >= CHARGE_3_TICK) return 3;
        return ticks >= CHARGE_2_TICK ? 2 : ticks >= CHARGE_1_TICK ? 1 : 0;
    }

    public static double chargeMultiplier(int level) {
        return switch (level) { case 1 -> 1.15; case 2 -> 1.35; case 3 -> 1.75; default -> 1.0; };
    }

    public static GreatSwordMove entry(boolean grounded, boolean rightClick, boolean sneak, boolean back) {
        if (!grounded) return GreatSwordMove.AERIAL;
        if (sneak && back) return GreatSwordMove.RISING;
        if (sneak) return GreatSwordMove.SWEEP;
        return rightClick ? GreatSwordMove.CHARGE_1 : GreatSwordMove.OVERHEAD;
    }

    public static GreatSwordMove follow(GreatSwordMove move, long elapsed, boolean rightClick, boolean sneak) {
        if (move.preparingSlashArt()) return move;
        if (move.charging()) return rightClick ? move : GreatSwordMove.prepareSlashArt(move.stage());
        GreatSwordMove target = target(move, rightClick, sneak);
        return elapsed < cancelFor(move, target) ? move : target;
    }
    private static GreatSwordMove target(GreatSwordMove move, boolean rightClick, boolean sneak) {
        if (move.tackling()) return rightClick ? GreatSwordMove.charge(Math.min(3, move.stage() + 1)) : GreatSwordMove.LEAPING_SWEEP;
        if (sneak) return move.stage() == 2 ? GreatSwordMove.STRONG_SWEEP : GreatSwordMove.SWEEP;
        if (rightClick) {
            return switch (move) {
                case OVERHEAD, CHARGED_SLASH -> GreatSwordMove.CHARGE_2;
                case STRONG_SLASH, STRONG_SWEEP -> GreatSwordMove.CHARGE_3;
                default -> GreatSwordMove.CHARGE_1;
            };
        }
        return switch (move) {
            case STRONG_SLASH -> GreatSwordMove.STRONG_SWEEP;
            case OVERHEAD, CHARGED_SLASH, TRUE_SLASH, LANDING -> GreatSwordMove.SWEEP;
            case SWEEP -> GreatSwordMove.RISING;
            default -> GreatSwordMove.OVERHEAD;
        };
    }
    private static String field(GreatSwordMove target) {
        return switch (target) {
            case CHARGE_1 -> "_CHARGE";
            case CHARGE_2 -> "_STORNG_CHARGE"; // Original field spelling.
            case CHARGE_3 -> "_SPIRIT_CHARGE";
            case SWEEP -> "_SIDE_SLASH";
            case STRONG_SWEEP -> "_STRONG_SIDE_SLASH";
            case LEAPING_SWEEP -> "_SPIRIT_SIDE_SLASH";
            case RISING -> "_UP_SLASH_FROM_STRONG";
            default -> "_ATTACK_TO_WALK";
        };
    }
    public static int cancelFor(GreatSwordMove from, GreatSwordMove to) {
        return from.source.window(field(to), from.cancelTick);
    }
    public static int preFor(GreatSwordMove from, GreatSwordMove to) {
        return from.source.window("_Pre" + field(to), cancelFor(from, to));
    }
}

