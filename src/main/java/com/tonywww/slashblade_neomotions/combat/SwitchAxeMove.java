package com.tonywww.slashblade_neomotions.combat;

/** Source-derived animation/event IDs; numerical damage/cost/gain are addon balance. */
public enum SwitchAxeMove {
    AXE_READY("switchaxe_axe", false, 0, 0, 0, 0),
    SWORD_READY("switchaxe_sword", true, 0, 0, 0, 0),
    AXE_OVERHEAD("sa_axe_overhead", false, 1.1, 0, 0, 3),
    AXE_SIDE("sa_axe_side", false, .8, 0, 0, 3),
    AXE_RISING("sa_axe_rising", false, 1.1, 0, 0, 3),
    AXE_FORWARD("sa_axe_forward", false, 1.2, 0, 0, 4),
    AXE_FADE("sa_axe_fade", false, .8, 0, 0, 3),
    AXE_WILD("sa_axe_wild", false, .6, 0, 0, 2),
    AXE_SLAM("sa_axe_slam", false, 1, 0, 0, 4),
    AXE_SPIRAL("sa_axe_spiral", false, .9, 0, 0, 10),
    MORPH_SWORD("sa_morph_sword", false, 1.1, 5, 10, 0),
    MORPH_AXE("sa_morph_axe", true, 1, 0, 0, 5),
    SWORD_OVERHEAD("sa_sword_overhead", true, .95, 4, 10, 0),
    SWORD_RIGHT("sa_sword_right", true, .85, 4, 10, 0),
    SWORD_LEFT("sa_sword_left", true, .85, 4, 10, 0),
    SWORD_DOUBLE("sa_sword_double", true, .8, 7, 9, 0),
    SWORD_HEAVEN("sa_sword_heaven", true, 1, 10, 14, 0),
    DISCHARGE("sa_discharge", true, 2.4, 20, 16, 0),
    FULL_RELEASE("sa_full_release", true, 2.2, 35, 0, 0);

    public final String path;
    public final boolean sword;
    public final double damage;
    public final float cost, ampGain, slashGain;
    public final SourceAction source;
    public final int duration, cancelTick;
    SwitchAxeMove(String path, boolean sword, double damage, float cost, float ampGain, float slashGain) {
        this.path = path; this.sword = sword; this.damage = damage;
        this.cost = cost; this.ampGain = ampGain; this.slashGain = slashGain;
        source = SourceAction.get(path); duration = source.duration; cancelTick = source.cancelTick;
    }
    public boolean ready() { return this == AXE_READY || this == SWORD_READY; }
    public boolean morph() { return this == MORPH_AXE || this == MORPH_SWORD; }
    public boolean endsInAxe() { return this == MORPH_AXE || this == DISCHARGE || this == FULL_RELEASE; }
}
