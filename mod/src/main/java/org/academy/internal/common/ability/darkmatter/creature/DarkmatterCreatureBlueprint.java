package org.academy.internal.common.ability.darkmatter.creature;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.darkmatter.DarkmatterCreaturePartType;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Server-validated, persistable blueprint. Values are snapshots and never follow the owner after spawning.
 */
public final class DarkmatterCreatureBlueprint {
    public static final int VERSION = 1;

    @SerializedName("version")
    private final int version = VERSION;
    @SerializedName("name")
    private String name = "Blueprint";
    @SerializedName("investment")
    private int investment = 5;
    @SerializedName("head")
    private String head = DarkmatterCreatureRegistries.HEAD_JAW.toString();
    @SerializedName("torso")
    private String torso = DarkmatterCreatureRegistries.TORSO_WALK.toString();
    @SerializedName("limbs")
    private String limbs = DarkmatterCreatureRegistries.LIMBS_GUARD.toString();
    @SerializedName("additional")
    private String additional = DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString();
    @SerializedName("headAlpha")
    private int headAlpha = 25;
    @SerializedName("torsoAlpha")
    private int torsoAlpha = 25;
    @SerializedName("limbsAlpha")
    private int limbsAlpha = 25;
    @SerializedName("additionalAlpha")
    private int additionalAlpha = 25;
    @SerializedName("modules")
    private List<String> modules = new ArrayList<>();

    public DarkmatterCreatureBlueprint() {
    }

    public DarkmatterCreatureBlueprint(String name, int investment, String head, String torso,
                                       String limbs, String additional, int headAlpha, int torsoAlpha,
                                       int limbsAlpha, int additionalAlpha, List<String> modules) {
        this.name = name;
        this.investment = investment;
        this.head = head;
        this.torso = torso;
        this.limbs = limbs;
        this.additional = additional;
        this.headAlpha = headAlpha;
        this.torsoAlpha = torsoAlpha;
        this.limbsAlpha = limbsAlpha;
        this.additionalAlpha = additionalAlpha;
        this.modules = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
    }

    public static DarkmatterCreatureBlueprint defaultFor(int slot, int level) {
        var total = Math.clamp(level, 1, 5) * 50;
        var blueprint = new DarkmatterCreatureBlueprint();
        blueprint.name = "Blueprint " + (Math.clamp(slot, 0, 3) + 1);
        blueprint.headAlpha = blueprint.torsoAlpha = blueprint.limbsAlpha
                = blueprint.additionalAlpha = total / 2;
        return blueprint;
    }

    public DarkmatterCreatureBlueprint copy() {
        return new DarkmatterCreatureBlueprint(name, investment, head, torso, limbs, additional,
                headAlpha, torsoAlpha, limbsAlpha, additionalAlpha, modules);
    }

    public List<String> validate(int level) {
        var errors = new ArrayList<String>();
        var safeLevel = Math.clamp(level, 1, 5);
        if (investment < 5 || investment > 25 * safeLevel || investment % 5 != 0) {
            errors.add("investment");
        }
        validatePart(errors, head, DarkmatterCreaturePartType.BodySlot.HEAD, "head");
        validatePart(errors, torso, DarkmatterCreaturePartType.BodySlot.TORSO, "torso");
        validatePart(errors, limbs, DarkmatterCreaturePartType.BodySlot.LIMBS, "limbs");
        validatePart(errors, additional, DarkmatterCreaturePartType.BodySlot.ADDITIONAL, "additional");
        var total = 50 * safeLevel;
        if (headAlpha < 0 || headAlpha > total) errors.add("head_phase");
        if (torsoAlpha < 0 || torsoAlpha > total) errors.add("torso_phase");
        if (limbsAlpha < 0 || limbsAlpha > total) errors.add("limbs_phase");
        if (additionalAlpha < 0 || additionalAlpha > total) errors.add("additional_phase");
        var seen = new LinkedHashSet<String>();
        var used = 0;
        if (modules == null) modules = new ArrayList<>();
        for (var raw : modules) {
            var id = Identifier.tryParse(raw);
            if (id == null || !seen.add(raw)) {
                errors.add("module:" + raw);
                continue;
            }
            var type = DarkmatterCreatureRegistries.module(id);
            if (type.isEmpty()) errors.add("module:" + raw);
            else used += type.get().budgetCost();
        }
        if (used > moduleBudget()) errors.add("module_budget:" + used + "/" + moduleBudget());
        return List.copyOf(errors);
    }

    private static void validatePart(List<String> errors, String raw,
                                     DarkmatterCreaturePartType.BodySlot slot, String field) {
        var id = Identifier.tryParse(raw);
        if (id == null || DarkmatterCreatureRegistries.part(id)
                .filter(type -> type.slot() == slot).isEmpty()) errors.add(field);
    }

    public int moduleBudget() {
        return Math.max(0, investment / 5);
    }

    public int moduleCost() {
        if (modules == null) return 0;
        var seen = new LinkedHashSet<String>();
        var used = 0;
        for (var raw : modules) {
            if (!seen.add(raw)) continue;
            var id = Identifier.tryParse(raw);
            if (id != null) used += DarkmatterCreatureRegistries.module(id)
                    .map(type -> type.budgetCost()).orElse(0);
        }
        return used;
    }

    public double effectiveInvestment(int milestone) {
        return investment * (Math.clamp(milestone, 0, 3) >= 1 ? 1.1 : 1.0);
    }

    public DarkmatterCreaturePartType.MutableStats createBaseStats(int milestone) {
        return createBaseStats(milestone, Math.max(1, maximumPhasePoint() / 50));
    }

    public DarkmatterCreaturePartType.MutableStats createBaseStats(int milestone, int level) {
        var strength = effectiveInvestment(milestone) / 5.0;
        var stats = new DarkmatterCreaturePartType.MutableStats(
                8.0 + 2.0 * strength,
                2.0 + 0.4 * strength,
                Math.min(20.0, 0.5 * strength),
                0.20 + 0.004 * strength,
                16.0 + strength);
        var total = Math.clamp(level, 1, 5) * 50;
        applyPart(stats, head, headAlpha, total);
        applyPart(stats, torso, torsoAlpha, total);
        applyPart(stats, limbs, limbsAlpha, total);
        applyPart(stats, additional, additionalAlpha, total);
        return stats;
    }

    private void applyPart(DarkmatterCreaturePartType.MutableStats stats, String raw, int alpha, int total) {
        var id = Identifier.tryParse(raw);
        if (id == null) return;
        var a = Math.clamp(alpha, 0, total) / 50.0f;
        var b = (total - Math.clamp(alpha, 0, total)) / 50.0f;
        DarkmatterCreatureRegistries.part(id).ifPresent(type -> type.statProcessor().apply(stats, a, b));
    }

    public float averageGammaPower(int level) {
        return Math.clamp(level, 1, 5);
    }

    private int maximumPhasePoint() {
        return Math.max(50, Math.max(Math.max(headAlpha, torsoAlpha),
                Math.max(limbsAlpha, additionalAlpha)));
    }

    public int version() {
        return version;
    }

    public String name() {
        return sanitizeName(name);
    }

    public int investment() {
        return investment;
    }

    public String head() {
        return head;
    }

    public String torso() {
        return torso;
    }

    public String limbs() {
        return limbs;
    }

    public String additional() {
        return additional;
    }

    public int headAlpha() {
        return headAlpha;
    }

    public int torsoAlpha() {
        return torsoAlpha;
    }

    public int limbsAlpha() {
        return limbsAlpha;
    }

    public int additionalAlpha() {
        return additionalAlpha;
    }

    public List<String> modules() {
        return modules == null ? List.of() : List.copyOf(modules);
    }

    private static String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) return "Blueprint";
        var stripped = raw.strip();
        return stripped.length() <= 32 ? stripped : stripped.substring(0, 32);
    }
}
