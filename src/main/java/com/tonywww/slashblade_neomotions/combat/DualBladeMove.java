package com.tonywww.slashblade_neomotions.combat;

import java.util.List;

/** Both-hand hit windows are imported from the source collision on/off tracks. */
public enum DualBladeMove {
    READY("dualblade", 0),
    BLADE_CUT("db_blade_cut", .55), SHEATH_CUT("db_sheath_cut", .50),
    CROSS_CUT("db_cross_cut", .55), FLURRY("db_flurry", .35), FINISHER("db_finisher", .55),
    SPIN("db_spin", .45), DASH("db_dash", .55), RETREAT("db_retreat", .50),
    AERIAL("db_aerial", .45), LANDING("db_landing", 0), SA_PREPARE("db_sa_prepare", 0);

    public enum Hand { BLADE, SHEATH }
    public record Strike(int tick, double damage, float roll, float pitch, Hand hand) {}
    public final String path;
    public final int duration, cancelTick;
    public final List<Strike> strikes;
    public final SourceAction source;
    DualBladeMove(String path, double damage) {
        this.path = path; source = SourceAction.get(path);
        duration = source.duration; cancelTick = source.cancelTick;
        strikes = source.hits.stream().map(h -> new Strike(h.tick(), damage, h.roll(), h.pitch(), Hand.valueOf(h.hand()))).toList();
    }
}
