package com.tonywww.slashblade_neomotions.combat;

/** Native SlashEffectRenderer rotates its horizontal XZ mesh about local X.
 * At the forward point its tangent, in player-model coordinates, is
 * (-cos(roll), sin(roll), 0): +Y is down. Do not use blade Euler angles here. */
public enum SlashDirection {
    DOWN(90), UP(-90), SWEEP_RIGHT(180), SWEEP_LEFT(0),
    DIAGONAL_RIGHT(120), DIAGONAL_LEFT(60), IMPACT(0);

    public final float roll;
    SlashDirection(float roll) { this.roll = roll; }
}
