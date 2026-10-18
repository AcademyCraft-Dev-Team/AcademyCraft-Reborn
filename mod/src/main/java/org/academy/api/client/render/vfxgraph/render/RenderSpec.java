package org.academy.api.client.render.vfxgraph.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.resources.R;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record RenderSpec(
        Geometry geometry,
        Blend blend,
        Identifier vertexShader,
        Identifier fragmentShader,
        @Nullable Identifier texture,
        @Nullable Identifier texture1,
        String layer,
        ArcRender arc,
        float particleBoundsScale,
        @Nullable DistortionRing distortionRing,
        float flashCap,
        PrimitiveTopology topology,
        Scale scale,
        Color color
) {
    public record Scale(float x, float y, float z) {
    }

    public record Color(float r, float g, float b, float a) {
    }

    public static final Scale UNIT_SCALE = new Scale(1f, 1f, 1f);
    public static final Color WHITE = new Color(1f, 1f, 1f, 1f);

    public RenderSpec(Geometry geometry, Blend blend, Identifier vertexShader, Identifier fragmentShader,
                      @Nullable Identifier texture, String layer, ArcRender arc) {
        this(geometry, blend, vertexShader, fragmentShader, texture, null, layer, arc, 0f, null, 1f,
                PrimitiveTopology.QUADS, UNIT_SCALE, WHITE);
    }

    public RenderSpec(Geometry geometry, Blend blend, Identifier vertexShader, Identifier fragmentShader,
                      @Nullable Identifier texture, String layer, ArcRender arc, float particleBoundsScale) {
        this(geometry, blend, vertexShader, fragmentShader, texture, null, layer, arc, particleBoundsScale, null, 1f,
                PrimitiveTopology.QUADS, UNIT_SCALE, WHITE);
    }

    public static final RenderSpec DEFAULT = new RenderSpec(
            Geometry.QUAD, Blend.TRANSLUCENT,
            R.shaders.core.vfxgraph_particle, R.shaders.core.vfxgraph_particle, null, null, "",
            ArcRender.DEFAULT, 0f, null, 1f, PrimitiveTopology.QUADS, UNIT_SCALE, WHITE);

    public enum Geometry {
        QUAD, MESH, LINE, RIBBON, ARC, DISTORTION_RING, SCREEN_FLASH, INSTANCED_TEX, BLOCK_QUADS
    }

    public record DistortionRing(float strength, float width, float edgeBlur) {
        public static final DistortionRing DEFAULT = new DistortionRing(0.025f, 0.5f, 0.05f);
    }

    public enum Blend {
        TRANSLUCENT, ADDITIVE, GLOW, BLOOM
    }

    public record ArcRender(
            int sparks,
            float sparkSpeed,
            float sparkSize,
            float sparkPeriod,
            float sparkTravel,
            float sparkLength,
            float sparkRadius,
            float sparkCurve,
            float sparkWobble,
            float thickness,
            float emission,
            float driftSpeed,
            float noiseStrength,
            float lifetime,
            int segments,
            float overallScale,
            int branchDepth,
            int branchCount,
            float branchAngle,
            float branchLengthScale,
            float branchWidthScale,
            float branchBrightnessScale
    ) {
        public static final ArcRender DEFAULT = new ArcRender(
                8, 2.2f, 0.02f, 0.6f, 0.5f, 10f, 0.2f, 0.35f, 0.12f, 0.25f, 0.3f,
                0.5f, 0.27f, 1.0f, 12, 1.0f,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f);
    }

    public static RenderSpec fromOutputNode(GraphNode node) {
        var geometry = switch (node.type()) {
            case "vfx.output_mesh" -> Geometry.MESH;
            case "vfx.output_line", "vfx.block.output_line" -> Geometry.LINE;
            case "vfx.output_ribbon", "vfx.block.output_ribbon" -> Geometry.RIBBON;
            case "vfx.output_arc", "vfx.block.output_arc" -> Geometry.ARC;
            case "vfx.block.output_distortion_ring" -> Geometry.DISTORTION_RING;
            case "vfx.output_screen_flash", "vfx.block.output_screen_flash" -> Geometry.SCREEN_FLASH;
            case "vfx.output_instanced_tex", "vfx.block.output_instanced_tex" -> Geometry.INSTANCED_TEX;
            case "vfx.block.output_block_geometry" -> Geometry.BLOCK_QUADS;
            default -> Geometry.QUAD;
        };
        var blend = switch (node.properties().getOrDefault("blend", "").trim()) {
            case "additive" -> Blend.ADDITIVE;
            case "glow" -> Blend.GLOW;
            case "bloom" -> Blend.BLOOM;
            case "translucent" -> Blend.TRANSLUCENT;
            default -> Blend.TRANSLUCENT;
        };
        var distortion = geometry == Geometry.DISTORTION_RING;
        var defaultShader = distortion ? R.shaders.core.vfxgraph_distortion_ring
                : geometry == Geometry.ARC ? R.shaders.core.vfxgraph_arc
                : geometry == Geometry.SCREEN_FLASH ? R.shaders.core.vfx_screen_flash
                : geometry == Geometry.INSTANCED_TEX ? R.shaders.core.vfxgraph_instanced_tex
                : geometry == Geometry.BLOCK_QUADS ? R.shaders.core.vfxgraph_block_mesh
                : R.shaders.core.vfxgraph_particle;
        var vertex = id(node, "vertex", defaultShader);
        var fragment = id(node, "shader", defaultShader);
        var texture = optionalId(node, "texture");
        var texture1 = optionalId(node, "texture1");
        var layer = node.properties().getOrDefault("layer", "").trim();
        var arc = arcRender(node);
        var topology = switch (node.properties().getOrDefault("topology", "").trim()) {
            case "triangles" -> PrimitiveTopology.TRIANGLES;
            default -> PrimitiveTopology.QUADS;
        };
        return new RenderSpec(geometry, blend, vertex, fragment, texture, texture1, layer,
                arc, Math.max(0f, floatProp(node.properties(), "bounds_scale", 0f)),
                distortion ? distortionRing(node) : null,
                Math.max(0f, floatProp(node.properties(), "cap", 1f)),
                geometry == Geometry.INSTANCED_TEX ? topology : PrimitiveTopology.QUADS,
                scaleProp(node), colorProp(node));
    }

    private static Scale scaleProp(GraphNode node) {
        var value = node.properties().getOrDefault("scale", "").trim();
        if (value.isEmpty()) return UNIT_SCALE;
        var parts = value.split(",");
        if (parts.length == 0) return UNIT_SCALE;
        try {
            var x = Float.parseFloat(parts[0].trim());
            var y = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : x;
            var z = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : x;
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return UNIT_SCALE;
            return new Scale(x, y, z);
        } catch (NumberFormatException e) {
            return UNIT_SCALE;
        }
    }

    private static Color colorProp(GraphNode node) {
        var value = node.properties().getOrDefault("color", "").trim();
        if (value.isEmpty()) return WHITE;
        var parts = value.split(",");
        try {
            var r = Float.parseFloat(parts[0].trim());
            var g = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1f;
            var b = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1f;
            var a = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 1f;
            if (!Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b) || !Float.isFinite(a)) return WHITE;
            return new Color(r, g, b, a);
        } catch (NumberFormatException e) {
            return WHITE;
        }
    }

    private static DistortionRing distortionRing(GraphNode node) {
        var p = node.properties();
        var d = DistortionRing.DEFAULT;
        return new DistortionRing(
                Math.max(0f, floatProp(p, "distortion_strength", d.strength())),
                Math.clamp(floatProp(p, "distortion_ring_width", d.width()), 0.001f, 1f),
                Math.clamp(floatProp(p, "distortion_edge_blur", d.edgeBlur()), 0f, 1f));
    }

    private static ArcRender arcRender(GraphNode node) {
        var p = node.properties();
        return new ArcRender(
                intProp(p, "sparks", ArcRender.DEFAULT.sparks()),
                floatProp(p, "spark_speed", ArcRender.DEFAULT.sparkSpeed()),
                floatProp(p, "spark_size", ArcRender.DEFAULT.sparkSize()),
                floatProp(p, "spark_period", ArcRender.DEFAULT.sparkPeriod()),
                floatProp(p, "spark_travel", ArcRender.DEFAULT.sparkTravel()),
                floatProp(p, "spark_length", ArcRender.DEFAULT.sparkLength()),
                floatProp(p, "spark_radius", ArcRender.DEFAULT.sparkRadius()),
                floatProp(p, "spark_curve", ArcRender.DEFAULT.sparkCurve()),
                floatProp(p, "spark_wobble", ArcRender.DEFAULT.sparkWobble()),
                floatProp(p, "thickness", ArcRender.DEFAULT.thickness()),
                floatProp(p, "emission", ArcRender.DEFAULT.emission()),
                floatProp(p, "drift_speed", ArcRender.DEFAULT.driftSpeed()),
                floatProp(p, "noise_strength", ArcRender.DEFAULT.noiseStrength()),
                floatProp(p, "lifetime", ArcRender.DEFAULT.lifetime()),
                intProp(p, "segments", ArcRender.DEFAULT.segments()),
                floatProp(p, "overall_scale", ArcRender.DEFAULT.overallScale()),
                intProp(p, "branch_depth", ArcRender.DEFAULT.branchDepth()),
                intProp(p, "branch_count", ArcRender.DEFAULT.branchCount()),
                floatProp(p, "branch_angle", ArcRender.DEFAULT.branchAngle()),
                floatProp(p, "branch_length_scale", ArcRender.DEFAULT.branchLengthScale()),
                floatProp(p, "branch_width_scale", ArcRender.DEFAULT.branchWidthScale()),
                floatProp(p, "branch_brightness_scale", ArcRender.DEFAULT.branchBrightnessScale())
        );
    }

    private static int intProp(Map<String, String> p, String key, int fallback) {
        var v = p.getOrDefault(key, "").trim();
        try {
            return v.isEmpty() ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float floatProp(Map<String, String> p, String key, float fallback) {
        var v = p.getOrDefault(key, "").trim();
        try {
            return v.isEmpty() ? fallback : Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Identifier id(GraphNode node, String key, Identifier fallback) {
        var v = node.properties().getOrDefault(key, "").trim();
        return v.isEmpty() ? fallback : Identifier.parse(v);
    }

    private static @Nullable Identifier optionalId(GraphNode node, String key) {
        var value = node.properties().getOrDefault(key, "").trim();
        return value.isEmpty() ? null : Identifier.parse(value);
    }

    public boolean matchesLayer(byte particleLayer) {
        return layer.isEmpty() || ParticleBuffer.layerByte(layer) == particleLayer;
    }

    public boolean matchesArcLayer(String arcLayer) {
        return layer.isEmpty() || layer.equals(arcLayer);
    }

    public boolean feedsBloom() {
        return blend == Blend.GLOW || blend == Blend.BLOOM;
    }

    public boolean bloomOnly() {
        return blend == Blend.BLOOM;
    }
}
