package com.tonywww.slashblade_neomotions.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.List;

/** Client-neutral table data; font measurement and drawing stay on the client. */
public record ManualMovesTooltip(String style) implements TooltipComponent {
    public record Row(String key, String move) {}
    public List<Row> rows() { return style.equals("sa") ? SWITCH_AXE : style.equals("gs") ? GREAT_SWORD : DUAL_BLADES; }
    public ManualMovesTooltip(boolean greatsword) { this(greatsword ? "gs" : "db"); }
    private static final List<Row> SWITCH_AXE = List.of(
        new Row("sa.axe", "sa_axe_overhead"), new Row("sa.side", "sa_axe_side"),
        new Row("sa.rise", "sa_axe_rising"), new Row("sa.wild", "sa_axe_wild"),
        new Row("sa.slam", "sa_axe_slam"), new Row("sa.forward", "sa_axe_forward"),
        new Row("sa.fade", "sa_axe_fade"), new Row("sa.spiral", "sa_axe_spiral"),
        new Row("sa.morph_sword", "sa_morph_sword"), new Row("sa.sword", "sa_sword_overhead"),
        new Row("sa.right", "sa_sword_right"), new Row("sa.left", "sa_sword_left"),
        new Row("sa.double", "sa_sword_double"), new Row("sa.heaven", "sa_sword_heaven"),
        new Row("sa.discharge", "sa_discharge"), new Row("sa.full", "sa_full_release"),
        new Row("sa.morph_axe", "sa_morph_axe"));
    private static final List<Row> GREAT_SWORD = List.of(
            new Row("gs.overhead", "gs_overhead"), new Row("gs.sweep", "gs_sweep"),
            new Row("gs.rising", "gs_rising"), new Row("gs.charge", "gs_charged_slash"),
            new Row("gs.strong", "gs_strong_slash"), new Row("gs.true", "gs_true_slash"),
            new Row("gs.strong_sweep", "gs_strong_sweep"), new Row("gs.tackle", "gs_tackle_1"),
            new Row("gs.leaping", "gs_leaping_sweep"), new Row("gs.sneak", "gs_sweep"),
            new Row("gs.back", "gs_rising"), new Row("gs.air", "gs_aerial"),
            new Row("gs.sa", "manual_sa"));
    private static final List<Row> DUAL_BLADES = List.of(
            new Row("db.first", "db_blade_cut"), new Row("db.second", "db_sheath_cut"),
            new Row("db.wheel", "db_cross_cut"), new Row("db.flurry", "db_flurry"),
            new Row("db.finish", "db_finisher"), new Row("db.spin", "db_spin"),
            new Row("db.dash", "db_dash"), new Row("db.rising", "db_retreat"),
            new Row("db.air", "db_aerial"), new Row("db.sa", "manual_sa"));
}
