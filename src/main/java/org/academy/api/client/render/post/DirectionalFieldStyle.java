package org.academy.api.client.render.post;

import org.academy.api.client.render.vfxgraph.GraphEffect;

/** Graph-authored appearance of a server-defined area; never changes combat membership. */
public record DirectionalFieldStyle(float surfaceStrength, float airStrength, float edgeWidth, int scatterSamples) {
    public static final DirectionalFieldStyle DEFAULT = new DirectionalFieldStyle(0.16f, 0.10f, 0.22f, 32);

    public DirectionalFieldStyle {
        surfaceStrength = finiteClamp(surfaceStrength, 0.16f, 0, 0.24f);
        airStrength = finiteClamp(airStrength, 0.10f, 0, 0.20f);
        edgeWidth = finiteClamp(edgeWidth, 0.22f, 0.01f, 1.0f);
        scatterSamples = Math.clamp(scatterSamples, 8, 48);
    }

    /** Resolves current blackboard values so editor reloads also update the scene-space output. */
    public static DirectionalFieldStyle fromGraph(GraphEffect graph) {
        return new DirectionalFieldStyle(graph.floatParameter("field_surface_strength", DEFAULT.surfaceStrength),
                graph.floatParameter("field_air_strength", DEFAULT.airStrength),
                graph.floatParameter("field_edge_width", DEFAULT.edgeWidth),
                Math.round(graph.floatParameter("field_scatter_count", DEFAULT.scatterSamples)));
    }

    public DirectionalFieldStyle phase(float alpha, float beta) {
        float a = finiteClamp(alpha, 0.5f, 0, 1), b = finiteClamp(beta, 0.5f, 0, 1);
        return new DirectionalFieldStyle(surfaceStrength * (0.55f + 0.45f * b),
                airStrength * (0.60f + 0.40f * a), edgeWidth, scatterSamples);
    }

    private static float finiteClamp(float value, float fallback, float minimum, float maximum) {
        return Math.clamp(Float.isFinite(value) ? value : fallback, minimum, maximum);
    }
}
