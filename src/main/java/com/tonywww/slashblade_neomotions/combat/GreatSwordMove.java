package com.tonywww.slashblade_neomotions.combat;

/** Timings are server ticks. Each style owns its graph; registration is separate. */
public enum GreatSwordMove {
    READY("greatsword", 0),
    OVERHEAD("gs_overhead", 1.15),
    SWEEP("gs_sweep", 0.95),
    STRONG_SWEEP("gs_strong_sweep", 1.45),
    LEAPING_SWEEP("gs_leaping_sweep", 1.30),
    RISING("gs_rising", 1.25),
    CHARGE_1("gs_charge_1", 0),
    CHARGE_2("gs_charge_2", 0),
    CHARGE_3("gs_charge_3", 0),
    SA_PREPARE_1("gs_sa_prepare_1", 0),
    SA_PREPARE_2("gs_sa_prepare_2", 0),
    SA_PREPARE_3("gs_sa_prepare_3", 0),
    CHARGED_SLASH("gs_charged_slash", 1.3),
    STRONG_SLASH("gs_strong_slash", 1.7),
    TRUE_SLASH("gs_true_slash", 2.5),
    TACKLE_1("gs_tackle_1", 0.4),
    TACKLE_2("gs_tackle_2", 0.4),
    TACKLE_3("gs_tackle_3", 0.4),
    AERIAL("gs_aerial", 1.2),
    LANDING("gs_landing", 0.7);

    public final String path;
    public final int duration, cancelTick, hitTick;
    public final double damage;
    public final float roll;
    public final SourceAction source;
    GreatSwordMove(String path, double damage) {
        this.path = path; this.damage = damage; source = SourceAction.get(path);
        duration = source.duration; cancelTick = source.cancelTick;
        var hit = source.hits.isEmpty() ? null : source.hits.get(source.hits.size() - 1);
        hitTick = hit == null ? 0 : hit.tick(); roll = hit == null ? 0 : hit.roll();
    }

    public boolean charging() { return this == CHARGE_1 || this == CHARGE_2 || this == CHARGE_3 || preparingSlashArt(); }
    public boolean preparingSlashArt() { return this == SA_PREPARE_1 || this == SA_PREPARE_2 || this == SA_PREPARE_3; }
    public boolean tackling() { return this == TACKLE_1 || this == TACKLE_2 || this == TACKLE_3; }
    public boolean aerial() { return this == AERIAL; }
    public int stage() {
        return switch (this) {
            case CHARGE_2, SA_PREPARE_2, STRONG_SLASH, STRONG_SWEEP, TACKLE_2 -> 2;
            case CHARGE_3, SA_PREPARE_3, TRUE_SLASH, TACKLE_3 -> 3;
            default -> 1;
        };
    }
    public static GreatSwordMove charge(int stage) { return stage == 1 ? CHARGE_1 : stage == 2 ? CHARGE_2 : CHARGE_3; }
    public static GreatSwordMove prepareSlashArt(int stage) { return stage == 1 ? SA_PREPARE_1 : stage == 2 ? SA_PREPARE_2 : SA_PREPARE_3; }
    public static GreatSwordMove tackle(int stage) { return stage == 1 ? TACKLE_1 : stage == 2 ? TACKLE_2 : TACKLE_3; }
    public static GreatSwordMove slash(int stage) { return stage == 1 ? CHARGED_SLASH : stage == 2 ? STRONG_SLASH : TRUE_SLASH; }
}

