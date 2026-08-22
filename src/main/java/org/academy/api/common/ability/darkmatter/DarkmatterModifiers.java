package org.academy.api.common.ability.darkmatter;

import java.util.EnumSet;
import java.util.Set;

/** Built-in modifier catalogue and its compatibility matrix. */
public final class DarkmatterModifiers {
    public static final String SHEAR = "shear";
    public static final String HARVEST = "harvest";
    public static final String TILL = "till";
    public static final String TELEPORT_SUPPRESSION = "teleport_suppression";
    public static final String TELEPORT_PROTECTION = "teleport_protection";
    public static final String PULL = "pull";
    public static final String KNOCKBACK = "knockback";
    public static final String EXPLOSIVE = "explosive";
    public static final String MAGNETIC = "magnetic";
    public static final String ANTIGRAVITY = "antigravity";
    public static final String EDIBLE = "edible";
    public static final String HOLY = "holy";
    public static final String DISMEMBER = "dismember";
    public static final String SLAUGHTER = "slaughter";
    public static final String DRYING = "drying";
    public static final String EXTINGUISH = "extinguish";
    public static final String FREEZING = "freezing";
    public static final String BURNING = "burning";
    public static final String LUCKY = "lucky";
    public static final String REACH = "reach";
    public static final String ECHO = "echo";
    public static final String LIGHTNING = "lightning";
    public static final String LAW_EROSION = "law_erosion";
    public static final String FEATHER_PURSUIT = "feather_pursuit";
    public static final String STRUCTURAL_GUARD = "structural_guard";

    private static boolean bootstrapped;

    private DarkmatterModifiers() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        var allOffense = EnumSet.complementOf(EnumSet.of(DarkmatterShape.ARMOR));
        var melee = EnumSet.of(DarkmatterShape.TOOL, DarkmatterShape.SWORD,
                DarkmatterShape.SPEAR, DarkmatterShape.TRIDENT, DarkmatterShape.MACE);
        var equipment = EnumSet.complementOf(EnumSet.of(DarkmatterShape.ARROW));
        register(SHEAR, 1, 1, EnumSet.of(DarkmatterShape.TOOL));
        register(HARVEST, 3, 1, EnumSet.of(DarkmatterShape.TOOL));
        register(TILL, 1, 1, EnumSet.of(DarkmatterShape.TOOL));
        register(TELEPORT_SUPPRESSION, 3, 1, allOffense);
        register(TELEPORT_PROTECTION, 2, 2,
                EnumSet.of(DarkmatterShape.ARMOR, DarkmatterShape.ARROW));
        register(PULL, 3, 1, allOffense, Set.of(KNOCKBACK));
        register(KNOCKBACK, 3, 1, allOffense, Set.of(PULL));
        register(EXPLOSIVE, 3, 2, allOffense);
        register(MAGNETIC, 3, 1, EnumSet.complementOf(
                EnumSet.of(DarkmatterShape.ARMOR, DarkmatterShape.ARROW)));
        register(ANTIGRAVITY, 3, 1, equipment);
        register(EDIBLE, 1, 1, EnumSet.complementOf(EnumSet.of(DarkmatterShape.ARMOR)));
        register(HOLY, 3, 1, allOffense);
        register(DISMEMBER, 3, 1, allOffense);
        register(SLAUGHTER, 3, 1, allOffense);
        register(DRYING, 3, 1, allOffense);
        register(EXTINGUISH, 3, 1, allOffense);
        register(FREEZING, 3, 1, allOffense, Set.of(BURNING));
        register(BURNING, 3, 1, allOffense, Set.of(FREEZING));
        register(LUCKY, 3, 1, allOffense);
        register(REACH, 3, 1, melee);
        register(ECHO, 3, 2, allOffense);
        register(LIGHTNING, 3, 2, allOffense);
        register(LAW_EROSION, 3, 1, allOffense);
        register(FEATHER_PURSUIT, 3, 2, allOffense);
        register(STRUCTURAL_GUARD, 3, 2, EnumSet.of(DarkmatterShape.ARMOR));
    }

    private static void register(String id, int maxLevel, int cost,
                                 Set<DarkmatterShape> shapes) {
        register(id, maxLevel, cost, shapes, Set.of());
    }

    private static void register(String id, int maxLevel, int cost,
                                 Set<DarkmatterShape> shapes, Set<String> conflicts) {
        DarkmatterShapingRegistries.register(new DarkmatterModifierType(
                id, maxLevel, cost, shapes, conflicts));
    }
}
