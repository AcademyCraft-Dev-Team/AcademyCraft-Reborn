package org.academy.api.client.render.vfxgraph.render;

import java.util.Map;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.resources.R;

/**
 * VFX 效果渲染规格（数据驱动，M21l）：描述粒子缓冲如何被渲染。
 *
 * <p><b>着色器不穷举</b>：顶点/片元着色器与混合全部来自输出节点属性（{@code vertex}/{@code shader}/
 * {@code blend}），图数据显式指定，无按几何/类型的代码枚举、无兼容回退；渲染器只按本规格字段动态构建管线。
 * 几何（quad/mesh/line/ribbon）是结构性的，由节点类型派生。一个效果可有**多个输出节点**（M21n 双输出数据驱动）：
 * 每个输出规格经 {@code layer} 过滤该规格负责渲染的粒子层（{@code ""}=全部），
 * 渲染器对缓冲逐规格绘制——分层外观（如火焰 fire + 烟雾 smoke）由图上多个输出节点表达，代码零 smoke 概念。</p>
 *
 * <p><b>ARC（M22f，改用旧 vfx 电弧渲染）</b>：几何 = `LightningMeshBuilder` 管网格（parallel transport ring，
 * TRIANGLES），`vfxgraph_arc` 颜色 100% 图数据驱动（`Color` 顶点属性）、零代码常量，`ArcLightning` UBO 仅渲染标量；
 * 主 pass 透明 / bloom pass additive。观感参数（sparks/spark_* 等）由图数据 `arcRender`（M22h）驱动。</p>
 *
 * @param geometry            绘制几何（决定顶点缓冲/图元）
 * @param blend               混合与后处理（GLOW 会额外渲进 bloom 输入）
 * @param vertexShader        顶点着色器资源 id（如 {@code academy:core/vfxgraph_particle}）
 * @param fragmentShader      片元着色器资源 id（如 {@code academy:core/vfxgraph_fire}）
 * @param layer               该规格负责渲染的粒子层（空串 = 全部；否则与粒子 {@code layer} 属性精确匹配）
 * @param arc                 ARC 观感参数（仅 {@link Geometry#ARC} 使用；其余几何用 {@link ArcRender#DEFAULT}）
 */
public record RenderSpec(
        Geometry geometry,
        Blend blend,
        Identifier vertexShader,
        Identifier fragmentShader,
        String layer,
        ArcRender arc
) {

    /** 缺省规格：中性软圆斑 quad、全部层（仅"未指定"时的中性兜底，非按类型枚举）。 */
    public static final RenderSpec DEFAULT = new RenderSpec(
            Geometry.QUAD, Blend.TRANSLUCENT,
            R.shaders.core.vfxgraph_particle, R.shaders.core.vfxgraph_particle, "",
            ArcRender.DEFAULT);

    public enum Geometry {
        QUAD, MESH, LINE, RIBBON, ARC
    }

    public enum Blend {
        TRANSLUCENT, ADDITIVE, GLOW
    }

    /**
     * ARC 渲染参数（数据驱动，M22-Rev2）：全部来自 output_arc 块属性，渲染器零硬编码常量。
     * 包含原有 spark 参数 + Blender 式参数（drift/noise/lifetime/segments/branch）。
     */
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
            // M22-Rev2: Blender 式参数
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
        /** 缺省参数。 */
        public static final ArcRender DEFAULT = new ArcRender(
                8, 2.2f, 0.02f, 0.6f, 0.5f, 10f, 0.2f, 0.35f, 0.12f, 0.25f, 0.3f,
                0.5f, 0.27f, 1.0f, 12, 1.0f,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f);
    }

    /** 由输出节点构建：着色器/混合**仅**来自节点属性；几何由节点类型派生（结构性）；层由 {@code layer} 属性过滤。 */
    public static RenderSpec fromOutputNode(GraphNode node) {
        var geometry = switch (node.type()) {
            case "vfx.output_mesh" -> Geometry.MESH;
            case "vfx.output_line" -> Geometry.LINE;
            case "vfx.output_ribbon" -> Geometry.RIBBON;
            case "vfx.output_arc", "vfx.block.output_arc" -> Geometry.ARC;
            default -> Geometry.QUAD;
        };
        var blend = switch (node.properties().getOrDefault("blend", "").trim()) {
            case "additive" -> Blend.ADDITIVE;
            case "glow" -> Blend.GLOW;
            case "translucent" -> Blend.TRANSLUCENT;
            default -> Blend.TRANSLUCENT;
        };
        // 中性兜底按几何结构（非外观枚举）：ARC 顶点格式与 particle 不兼容，缺省走 arc 辉光光带 shader
        var vertex = id(node, "vertex", geometry == Geometry.ARC ? R.shaders.core.vfxgraph_arc : R.shaders.core.vfxgraph_particle);
        var fragment = id(node, "shader", geometry == Geometry.ARC ? R.shaders.core.vfxgraph_arc : R.shaders.core.vfxgraph_particle);
        var layer = node.properties().getOrDefault("layer", "").trim();
        var arc = arcRender(node);
        return new RenderSpec(geometry, blend, vertex, fragment, layer, arc);
    }

    /** ARC 观感参数解析（数据驱动，M22-Rev2）。 */
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
                // M22-Rev2 Blender 参数
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

    /** 该规格是否渲染指定粒子：{@code layer} 空串（全部）或与粒子层字节匹配。 */
    public boolean matchesLayer(byte particleLayer) {
        return layer.isEmpty() || ParticleBuffer.layerByte(layer) == particleLayer;
    }

    /** 是否 GLOW 规格（参与 bloom 输入，供 bloomPass 过滤）。 */
    public boolean feedsBloom() {
        return blend == Blend.GLOW;
    }
}
