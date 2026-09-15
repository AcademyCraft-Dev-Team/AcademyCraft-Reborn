package org.academy.api.client.render.vfxgraph.arc;

/** Shared railgun discharge palette and tube profile, independent of skill, entity, or topology. */
public final class BlueWhiteArcStyle {
    private BlueWhiteArcStyle() { }

    public static float widthScale(int shell) { return shell == 0 ? 2.5f : 0.7f; }

    public static ArcCurve shell(ArcBuffer buffer, long group, long seed, float opacity, int shell, String layer) {
        var arc = buffer.add(group);
        arc.setColor(shell == 0 ? 0.12f : 0.78f, shell == 0 ? 0.48f : 0.94f, 1,
                Math.clamp(opacity * (shell == 0 ? 0.48f : 0.92f), 0, 1));
        arc.setSeed(seed);
        arc.setLifetime(1);
        arc.setNoiseStrength(0);
        arc.setDriftSpeed(0);
        arc.setMaxTubeSegments(6);
        arc.setLayer(layer);
        return arc;
    }
}
