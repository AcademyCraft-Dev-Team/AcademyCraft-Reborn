package org.academy.api.common.ability.darkmatter;

/**
 * Immutable, level-scaled view of a dark-matter user's current phase allocation.
 * Alpha and beta are real points; their power values are expressed in 50-point units.
 */
public record DarkmatterPhaseSnapshot(
        int abilityLevel,
        int totalPoints,
        int alphaPoints,
        int betaPoints,
        int gammaPoints,
        float alphaPower,
        float betaPower,
        float gammaPower,
        boolean gammaActive
) {
    public static DarkmatterPhaseSnapshot of(int abilityLevel, int alphaPoints, boolean gammaActive) {
        var level = Math.clamp(abilityLevel, 0, 5);
        var total = level * 50;
        var alpha = Math.clamp(alphaPoints, 0, total);
        var beta = total - alpha;
        // Gamma is an independent, fully available phase reserve. Alpha/beta tuning only
        // redistributes their shared pool; it must never weaken the ultimate phase.
        var gamma = total;
        return new DarkmatterPhaseSnapshot(
                level, total, alpha, beta, gamma,
                alpha / 50.0f, beta / 50.0f, gamma / 50.0f, gammaActive
        );
    }

    public float alphaRatio() {
        return totalPoints <= 0 ? 0.5f : alphaPoints / (float) totalPoints;
    }

    public float betaRatio() {
        return totalPoints <= 0 ? 0.5f : betaPoints / (float) totalPoints;
    }

    public float gammaRatio() {
        return totalPoints <= 0 ? 0.0f : gammaPoints / (float) totalPoints;
    }

    public float activeGammaPower() {
        return gammaActive ? gammaPower : 0.0f;
    }
}
