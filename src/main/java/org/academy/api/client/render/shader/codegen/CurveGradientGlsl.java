package org.academy.api.client.render.shader.codegen;

import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;

/**
 * 曲线/渐变 → GLSL 采样函数生成（M12-02）。
 * 生成的分段函数与 CPU 侧 {@code CurveSampler}/{@code GradientSampler} 语义一致。
 */
public final class CurveGradientGlsl {
    private CurveGradientGlsl() {
    }

    public static String curveFunction(Curve curve, String id) {
        var kfs = curve.keyframes();
        if (kfs.isEmpty()) return "float " + curveName(id) + "(float t) { return 0.0; }";
        var w = new StringBuilder();
        w.append("float ").append(curveName(id)).append("(float t) {\n");
        w.append("    if (t <= ").append(f(kfs.get(0).time())).append(") return ").append(f(kfs.get(0).value())).append(";\n");
        for (var i = 1; i < kfs.size(); i++) {
            var a = kfs.get(i - 1);
            var b = kfs.get(i);
            if (b.time() <= a.time()) continue;
            var u = "(t - " + f(a.time()) + ") / (" + f(b.time() - a.time()) + ")";
            var interp = switch (b.interpolation()) {
                case STEP -> f(a.value());
                case SMOOTH -> "mix(" + f(a.value()) + ", " + f(b.value()) + ", smoothstep(0.0, 1.0, " + u + "))";
                case BEZIER -> {
                    var m0 = a.outTangent() * (b.time() - a.time());
                    var m1 = b.inTangent() * (b.time() - a.time());
                    yield "(2.0*pow(" + u + ",3.0) - 3.0*pow(" + u + ",2.0) + 1.0) * " + f(a.value())
                            + " + (pow(" + u + ",3.0) - 2.0*pow(" + u + ",2.0) + " + u + ") * " + f(m0)
                            + " + (-2.0*pow(" + u + ",3.0) + 3.0*pow(" + u + ",2.0)) * " + f(b.value())
                            + " + (pow(" + u + ",3.0) - pow(" + u + ",2.0)) * " + f(m1);
                }
                default -> "mix(" + f(a.value()) + ", " + f(b.value()) + ", clamp(" + u + ", 0.0, 1.0))";
            };
            w.append("    if (t < ").append(f(b.time())).append(") return ").append(interp).append(";\n");
        }
        w.append("    return ").append(f(kfs.get(kfs.size() - 1).value())).append(";\n");
        w.append("}\n");
        return w.toString();
    }

    public static String gradientFunction(Gradient gradient, String id) {
        var stops = gradient.stops();
        if (stops.isEmpty()) return "vec4 " + gradientName(id) + "(float t) { return vec4(1.0); }";
        var w = new StringBuilder();
        w.append("vec4 ").append(gradientName(id)).append("(float t) {\n");
        w.append("    if (t <= ").append(f(stops.get(0).position())).append(") return ").append(color(stops.get(0))).append(";\n");
        for (var i = 1; i < stops.size(); i++) {
            var a = stops.get(i - 1);
            var b = stops.get(i);
            if (b.position() <= a.position()) continue;
            var u = "(t - " + f(a.position()) + ") / (" + f(b.position() - a.position()) + ")";
            w.append("    if (t < ").append(f(b.position())).append(") return mix(")
                    .append(color(a)).append(", ").append(color(b)).append(", clamp(").append(u).append(", 0.0, 1.0));\n");
        }
        w.append("    return ").append(color(stops.get(stops.size() - 1))).append(";\n");
        w.append("}\n");
        return w.toString();
    }

    public static String curveName(String parameterId) {
        return "_academy_curve_" + GlslNames.sanitize(parameterId);
    }

    public static String gradientName(String parameterId) {
        return "_academy_gradient_" + GlslNames.sanitize(parameterId);
    }

    private static String color(Gradient.ColorStop stop) {
        return "vec4(" + f(stop.r()) + ", " + f(stop.g()) + ", " + f(stop.b()) + ", " + f(stop.a()) + ")";
    }

    private static String f(float v) {
        if (v == Math.floor(v) && !Float.isInfinite(v) && Math.abs(v) < 1e7f) {
            return (long) v + ".0";
        }
        return Float.toString(v);
    }
}
