package org.academy.internal.common.ability.darkmatter;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.ability.darkmatter.DarkmatterPhaseSnapshot;

/** Persistent, server-authoritative state shared by all dark-matter skills. */
public final class DarkmatterStateData {
    private static final int CURRENT_SCHEMA = 2;

    @SerializedName("schemaVersion")
    private int schemaVersion;
    @SerializedName("alphaPoints")
    private Integer alphaPoints;
    @SerializedName("pointCapacity")
    private int pointCapacity;
    @SerializedName("phaseRemainder")
    private float phaseRemainder;
    /** Kept only for migration from the normalized phase implementation. */
    @SerializedName("phase")
    private Float legacyPhase;

    @SerializedName("resourceInitialized")
    private boolean resourceInitialized;
    @SerializedName("naturalMatter")
    private float naturalMatter;
    @SerializedName("createdMatter")
    private float createdMatter;
    @SerializedName("createdCpDebt")
    private float createdCpDebt;
    @SerializedName("reservedMatter")
    private float reservedMatter;

    public boolean reconcilePhase(int abilityLevel) {
        var total = Math.clamp(abilityLevel, 0, 5) * 50;
        var changed = false;
        if (alphaPoints == null) {
            var phase = legacyPhase == null || !Float.isFinite(legacyPhase)
                    ? 0.0f : Math.clamp(legacyPhase, -1.0f, 1.0f);
            alphaPoints = Math.round((1.0f - phase) * 0.5f * total);
            pointCapacity = total;
            legacyPhase = null;
            changed = true;
        } else if (pointCapacity != total) {
            var ratio = pointCapacity <= 0 ? 0.5f : alphaPoints / (float) pointCapacity;
            alphaPoints = Math.round(Math.clamp(ratio, 0.0f, 1.0f) * total);
            pointCapacity = total;
            phaseRemainder = 0.0f;
            changed = true;
        }
        var normalized = Math.clamp(alphaPoints, 0, total);
        if (normalized != alphaPoints) {
            alphaPoints = normalized;
            changed = true;
        }
        if (schemaVersion != CURRENT_SCHEMA) {
            schemaVersion = CURRENT_SCHEMA;
            changed = true;
        }
        return changed;
    }

    public DarkmatterPhaseSnapshot phaseSnapshot(int abilityLevel, boolean gammaActive) {
        reconcilePhase(abilityLevel);
        return DarkmatterPhaseSnapshot.of(abilityLevel, alphaPoints == null ? 0 : alphaPoints, gammaActive);
    }

    public int getAlphaPoints(int abilityLevel) {
        reconcilePhase(abilityLevel);
        return alphaPoints == null ? 0 : alphaPoints;
    }

    public boolean setAlphaPoints(int abilityLevel, int points) {
        var changed = reconcilePhase(abilityLevel);
        var total = Math.clamp(abilityLevel, 0, 5) * 50;
        var normalized = Math.clamp(points, 0, total);
        if (alphaPoints == null || alphaPoints != normalized) {
            alphaPoints = normalized;
            phaseRemainder = 0.0f;
            changed = true;
        }
        return changed;
    }

    /** Applies a fractional point delta while keeping the serialized allocation integral. */
    public boolean tuneAlphaPoints(int abilityLevel, float deltaPoints) {
        if (!Float.isFinite(deltaPoints) || deltaPoints == 0.0f) return false;
        reconcilePhase(abilityLevel);
        phaseRemainder += deltaPoints;
        var whole = phaseRemainder > 0.0f
                ? (int) Math.floor(phaseRemainder)
                : (int) Math.ceil(phaseRemainder);
        if (whole == 0) return false;
        phaseRemainder -= whole;
        var total = Math.clamp(abilityLevel, 0, 5) * 50;
        var current = alphaPoints == null ? 0 : alphaPoints;
        var next = Math.clamp(current + whole, 0, total);
        if (next == current) {
            phaseRemainder = 0.0f;
            return false;
        }
        alphaPoints = next;
        return true;
    }

    public boolean isResourceInitialized() { return resourceInitialized; }

    public void initializeResource(float natural, float created, float cpDebt) {
        naturalMatter = finiteNonNegative(natural);
        createdMatter = finiteNonNegative(created);
        createdCpDebt = createdMatter <= 0.0f ? 0.0f : finiteNonNegative(cpDebt);
        resourceInitialized = true;
    }

    public float getNaturalMatter() { return finiteNonNegative(naturalMatter); }
    public void setNaturalMatter(float value) { naturalMatter = finiteNonNegative(value); }
    public float getCreatedMatter() { return finiteNonNegative(createdMatter); }
    public void setCreatedMatter(float value) {
        createdMatter = finiteNonNegative(value);
        if (createdMatter <= 0.0f) createdCpDebt = 0.0f;
    }
    public float getCreatedCpDebt() { return finiteNonNegative(createdCpDebt); }
    public void setCreatedCpDebt(float value) {
        createdCpDebt = getCreatedMatter() <= 0.0f ? 0.0f : finiteNonNegative(value);
    }
    public float getReservedMatter() { return finiteNonNegative(reservedMatter); }
    public void setReservedMatter(float value) { reservedMatter = finiteNonNegative(value); }
    public float totalMatter() { return getNaturalMatter() + getCreatedMatter(); }

    public boolean repair() {
        var changed = false;
        var natural = finiteNonNegative(naturalMatter);
        var created = finiteNonNegative(createdMatter);
        var debt = created <= 0.0f ? 0.0f : finiteNonNegative(createdCpDebt);
        var reserved = finiteNonNegative(reservedMatter);
        if (Float.compare(naturalMatter, natural) != 0) { naturalMatter = natural; changed = true; }
        if (Float.compare(createdMatter, created) != 0) { createdMatter = created; changed = true; }
        if (Float.compare(createdCpDebt, debt) != 0) { createdCpDebt = debt; changed = true; }
        if (Float.compare(reservedMatter, reserved) != 0) { reservedMatter = reserved; changed = true; }
        if (!Float.isFinite(phaseRemainder)) { phaseRemainder = 0.0f; changed = true; }
        return changed;
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
