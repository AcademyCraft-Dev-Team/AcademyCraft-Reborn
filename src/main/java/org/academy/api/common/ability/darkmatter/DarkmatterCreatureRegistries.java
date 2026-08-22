package org.academy.api.common.ability.darkmatter;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Extensible registry used by blueprint validation, runtime stats and client model selection. */
public final class DarkmatterCreatureRegistries {
    private static final Map<Identifier, DarkmatterCreaturePartType> PARTS = new LinkedHashMap<>();
    private static final Map<Identifier, DarkmatterCreatureModuleType> MODULES = new LinkedHashMap<>();

    public static final Identifier HEAD_JAW = id("head_melee_jaw");
    public static final Identifier HEAD_CANNON = id("head_direct_cannon");
    public static final Identifier HEAD_HOMING = id("head_homing_cannon");
    public static final Identifier TORSO_WALK = id("torso_walking");
    public static final Identifier TORSO_FLY = id("torso_flying");
    public static final Identifier TORSO_SWIM = id("torso_swimming");
    public static final Identifier LIMBS_GUARD = id("limbs_guard");
    public static final Identifier LIMBS_MINER = id("limbs_excavation");
    public static final Identifier LIMBS_CARRIER = id("limbs_carrier");
    public static final Identifier ADDITIONAL_NONE = id("additional_none");
    public static final Identifier ADDITIONAL_CARAPACE = id("additional_carapace");
    public static final Identifier ADDITIONAL_SENSOR = id("additional_sensor");
    public static final Identifier ADDITIONAL_WEAPON = id("additional_weapon");

    public static final Identifier MODULE_GUARD = id("module_guard");
    public static final Identifier MODULE_FOCUS = id("module_focus_fire");
    public static final Identifier MODULE_PICKUP = id("module_pickup");
    public static final Identifier MODULE_EXCAVATION = id("module_excavation");
    public static final Identifier MODULE_SCOUT = id("module_scout");
    public static final Identifier MODULE_SELF_REPAIR = id("module_self_repair");
    public static final Identifier MODULE_FORMATION = id("module_formation");

    static {
        registerPart(new DarkmatterCreaturePartType(HEAD_JAW,
                DarkmatterCreaturePartType.BodySlot.HEAD, 0,
                (s, a, b) -> s.attackDamage += 1.2 * a));
        registerPart(new DarkmatterCreaturePartType(HEAD_CANNON,
                DarkmatterCreaturePartType.BodySlot.HEAD, 1,
                (s, a, b) -> s.followRange += 2.0 * b));
        registerPart(new DarkmatterCreaturePartType(HEAD_HOMING,
                DarkmatterCreaturePartType.BodySlot.HEAD, 2,
                (s, a, b) -> s.followRange += 3.0 * b));
        registerPart(new DarkmatterCreaturePartType(TORSO_WALK,
                DarkmatterCreaturePartType.BodySlot.TORSO, 0,
                (s, a, b) -> { s.armor += 1.5 * a; s.movementSpeed += 0.006 * b; }));
        registerPart(new DarkmatterCreaturePartType(TORSO_FLY,
                DarkmatterCreaturePartType.BodySlot.TORSO, 1,
                (s, a, b) -> s.movementSpeed += 0.012 * b));
        registerPart(new DarkmatterCreaturePartType(TORSO_SWIM,
                DarkmatterCreaturePartType.BodySlot.TORSO, 2,
                (s, a, b) -> s.movementSpeed += 0.008 * b));
        registerPart(new DarkmatterCreaturePartType(LIMBS_GUARD,
                DarkmatterCreaturePartType.BodySlot.LIMBS, 0,
                (s, a, b) -> s.attackDamage += 0.6 * a));
        registerPart(new DarkmatterCreaturePartType(LIMBS_MINER,
                DarkmatterCreaturePartType.BodySlot.LIMBS, 1, null));
        registerPart(new DarkmatterCreaturePartType(LIMBS_CARRIER,
                DarkmatterCreaturePartType.BodySlot.LIMBS, 2,
                (s, a, b) -> s.movementSpeed += 0.004 * b));
        registerPart(new DarkmatterCreaturePartType(ADDITIONAL_NONE,
                DarkmatterCreaturePartType.BodySlot.ADDITIONAL, 0, null));
        registerPart(new DarkmatterCreaturePartType(ADDITIONAL_CARAPACE,
                DarkmatterCreaturePartType.BodySlot.ADDITIONAL, 1,
                (s, a, b) -> s.armor += 1.5 * a));
        registerPart(new DarkmatterCreaturePartType(ADDITIONAL_SENSOR,
                DarkmatterCreaturePartType.BodySlot.ADDITIONAL, 2,
                (s, a, b) -> s.followRange += 4.0 * (a + b)));
        registerPart(new DarkmatterCreaturePartType(ADDITIONAL_WEAPON,
                DarkmatterCreaturePartType.BodySlot.ADDITIONAL, 3,
                (s, a, b) -> s.attackDamage += 0.8 * a));

        registerModule(new DarkmatterCreatureModuleType(MODULE_GUARD, 1, null));
        registerModule(new DarkmatterCreatureModuleType(MODULE_FOCUS, 2, null));
        registerModule(new DarkmatterCreatureModuleType(MODULE_PICKUP, 2, null));
        registerModule(new DarkmatterCreatureModuleType(MODULE_EXCAVATION, 3, null));
        registerModule(new DarkmatterCreatureModuleType(MODULE_SCOUT, 2, null));
        registerModule(new DarkmatterCreatureModuleType(MODULE_SELF_REPAIR, 4, null));
        registerModule(new DarkmatterCreatureModuleType(MODULE_FORMATION, 3, null));
    }

    private DarkmatterCreatureRegistries() { }

    public static synchronized DarkmatterCreaturePartType registerPart(DarkmatterCreaturePartType type) {
        if (PARTS.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate darkmatter creature part: " + type.id());
        }
        return type;
    }

    public static synchronized DarkmatterCreatureModuleType registerModule(DarkmatterCreatureModuleType type) {
        if (MODULES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate darkmatter creature module: " + type.id());
        }
        return type;
    }

    public static Optional<DarkmatterCreaturePartType> part(Identifier id) {
        return Optional.ofNullable(PARTS.get(id));
    }

    public static Optional<DarkmatterCreatureModuleType> module(Identifier id) {
        return Optional.ofNullable(MODULES.get(id));
    }

    public static Collection<DarkmatterCreaturePartType> parts() { return List.copyOf(PARTS.values()); }
    public static Collection<DarkmatterCreatureModuleType> modules() { return List.copyOf(MODULES.values()); }

    private static Identifier id(String path) { return AcademyCraft.academy(path); }
}
