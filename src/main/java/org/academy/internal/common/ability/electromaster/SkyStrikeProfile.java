package org.academy.internal.common.ability.electromaster;

public enum SkyStrikeProfile {
    LIGHTNING_STORM(
            0, 6.0f, 24.0f, 2.5f,
            5, 0, 8,
            1.0f, 4.5f, 4.0f,
            0.08f, 3.0f, 24.0f, 0.18f,
            0.06f, 6.0f, 24.0f, 0.20f,
            1.8f, 1.0f, false
    ),
    THUNDERCLAP(
            1, 10.0f, 40.0f, 6.0f,
            12, 20, 24,
            1.5f, 10.0f, 6.0f,
            0.22f, 4.0f, 32.0f, 0.22f,
            0.35f, 6.0f, 32.0f, 0.35f,
            5.0f, 2.0f, true
    );

    private final int wireId;
    private final float lifetimeTicks;
    private final float columnHeight;
    private final float columnWidth;
    private final int aerialArcCount;
    private final int inwardArcCount;
    private final int groundArcCount;
    private final float ringStartRadius;
    private final float ringEndRadius;
    private final float ringDurationTicks;
    private final float flashAlpha;
    private final float flashDurationTicks;
    private final float feedbackRange;
    private final float flashCap;
    private final float shakeDegrees;
    private final float shakeDurationTicks;
    private final float shakeRange;
    private final float shakeCapDegrees;
    private final float thunderVolume;
    private final float impactVolume;
    private final boolean restrike;

    SkyStrikeProfile(
            int wireId,
            float lifetimeTicks,
            float columnHeight,
            float columnWidth,
            int aerialArcCount,
            int inwardArcCount,
            int groundArcCount,
            float ringStartRadius,
            float ringEndRadius,
            float ringDurationTicks,
            float flashAlpha,
            float flashDurationTicks,
            float feedbackRange,
            float flashCap,
            float shakeDegrees,
            float shakeDurationTicks,
            float shakeRange,
            float shakeCapDegrees,
            float thunderVolume,
            float impactVolume,
            boolean restrike
    ) {
        this.wireId = wireId;
        this.lifetimeTicks = lifetimeTicks;
        this.columnHeight = columnHeight;
        this.columnWidth = columnWidth;
        this.aerialArcCount = aerialArcCount;
        this.inwardArcCount = inwardArcCount;
        this.groundArcCount = groundArcCount;
        this.ringStartRadius = ringStartRadius;
        this.ringEndRadius = ringEndRadius;
        this.ringDurationTicks = ringDurationTicks;
        this.flashAlpha = flashAlpha;
        this.flashDurationTicks = flashDurationTicks;
        this.feedbackRange = feedbackRange;
        this.flashCap = flashCap;
        this.shakeDegrees = shakeDegrees;
        this.shakeDurationTicks = shakeDurationTicks;
        this.shakeRange = shakeRange;
        this.shakeCapDegrees = shakeCapDegrees;
        this.thunderVolume = thunderVolume;
        this.impactVolume = impactVolume;
        this.restrike = restrike;
    }

    public static SkyStrikeProfile fromWireId(int wireId) {
        for (var profile : values()) {
            if (profile.wireId == wireId) return profile;
        }
        return LIGHTNING_STORM;
    }

    public int wireId() { return wireId; }
    public float lifetimeTicks() { return lifetimeTicks; }
    public float columnHeight() { return columnHeight; }
    public float columnWidth() { return columnWidth; }
    public int aerialArcCount() { return aerialArcCount; }
    public int inwardArcCount() { return inwardArcCount; }
    public int groundArcCount() { return groundArcCount; }
    public float ringStartRadius() { return ringStartRadius; }
    public float ringEndRadius() { return ringEndRadius; }
    public float ringDurationTicks() { return ringDurationTicks; }
    public float flashAlpha() { return flashAlpha; }
    public float flashDurationTicks() { return flashDurationTicks; }
    public float feedbackRange() { return feedbackRange; }
    public float flashCap() { return flashCap; }
    public float shakeDegrees() { return shakeDegrees; }
    public float shakeDurationTicks() { return shakeDurationTicks; }
    public float shakeRange() { return shakeRange; }
    public float shakeCapDegrees() { return shakeCapDegrees; }
    public float thunderVolume() { return thunderVolume; }
    public float impactVolume() { return impactVolume; }
    public boolean restrike() { return restrike; }
}
