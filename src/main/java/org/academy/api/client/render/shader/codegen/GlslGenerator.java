package org.academy.api.client.render.shader.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.academy.api.client.render.graph.compile.CompiledGraph;
import org.academy.api.client.render.graph.model.Edge;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.model.Port;
import org.academy.api.client.render.graph.model.PortDirection;
import org.academy.api.client.render.graph.type.Curve;
import org.academy.api.client.render.graph.type.Gradient;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.graph.type.ValueType;
import org.academy.api.client.render.shader.pipeline.SamplerBinding;

/**
 * GLSL 代码生成器。把编译后的图翻译为片段着色器（配固定全屏顶点着色器）。
 *
 * <p>黑板参数打包进单个 std140 uniform 块 {@link #UNIFORM_BLOCK_NAME}，成员名
 * 由 {@link GlslNames#uniformName} 派生，与 {@code UniformLayout} 保持一致。</p>
 */
public final class GlslGenerator {
    public static final String UNIFORM_BLOCK_NAME = "GraphUniforms";
    public static final String TIME_MEMBER = "Time";
    public static final String UV_VARYING = "texCoord";
    public static final String SAMPLER_UNIFORM = "Sampler0";
    public static final String OUTPUT_TYPE_ID = "output.color";
    public static final String OUTPUT_PORT = "color";

    public static final String VERTEX_TEMPLATE = """
            #version 330

            in vec3 Position;
            out vec2 texCoord;

            void main() {
                gl_Position = vec4(Position.x, Position.y, 0.0, 1.0);
                texCoord = Position.xy * 0.5 + 0.5;
            }
            """;

    private final GlslNodeRegistry generators;

    public GlslGenerator(GlslNodeRegistry generators) {
        this.generators = generators;
    }

    public GlslProgram generate(Graph graph, CompiledGraph compiled) {
        Map<String, Uniform> paramUniforms = new LinkedHashMap<>();
        Map<String, Curve> curves = new LinkedHashMap<>();
        Map<String, Gradient> gradients = new LinkedHashMap<>();
        for (var p : graph.parameters()) {
            if (p.type() == ValueType.SAMPLER) continue; // sampler 走纹理绑定，不进 UBO
            if (p.type() == ValueType.CURVE) {
                curves.put(p.id(), p.defaultValue().asCurve());
                continue;
            }
            if (p.type() == ValueType.GRADIENT) {
                gradients.put(p.id(), p.defaultValue().asGradient());
                continue;
            }
            paramUniforms.put(p.id(), new Uniform(GlslNames.uniformName(p.id()), p.type()));
        }

        var samplerBindings = samplePlan(graph);
        Map<String, String> samplerNames = new LinkedHashMap<>();
        for (var b : samplerBindings) {
            samplerNames.put(b.identifier(), b.uniformName());
        }

        Set<String> helpers = new LinkedHashSet<>();
        var ctx = new Context(paramUniforms, curves, gradients, helpers, samplerNames);

        Map<String, Edge> inputEdges = new HashMap<>();
        for (var e : graph.edges()) {
            inputEdges.put(e.to().nodeId() + ':' + e.to().portId(), e);
        }

        Map<String, Expr> outputExprs = new HashMap<>();
        for (var entry : compiled.foldedOutputs().entrySet()) {
            for (var pv : entry.getValue().entrySet()) {
                outputExprs.put(entry.getKey() + ':' + pv.getKey(), GlslLiterals.of(pv.getValue()));
            }
        }

        Set<String> outputIds = new HashSet<>(graph.outputs());
        Map<String, GraphNode> byId = new HashMap<>();
        for (var n : graph.nodes()) {
            byId.put(n.id(), n);
        }

        var body = new GlslWriter();
        body.push();
        for (var node : compiled.execOrder()) {
            if (outputIds.contains(node.id())) continue;
            var gen = generators.find(node.type());
            if (gen == null) {
                throw new IllegalStateException("no GLSL generator for node type: " + node.type());
            }

            Map<String, Expr> inputs = new LinkedHashMap<>();
            for (var port : node.ports()) {
                if (port.direction() != PortDirection.INPUT) continue;
                inputs.put(port.id(), inputExpr(node, port, inputEdges, outputExprs));
            }

            var outputs = gen.generate(node, inputs, ctx);
            for (var out : outputs.entrySet()) {
                var varName = GlslNames.varName(node.id(), out.getKey());
                body.line(GlslType.of(out.getValue().type()) + ' ' + varName + " = " + out.getValue().code() + ';');
                outputExprs.put(node.id() + ':' + out.getKey(), new Expr(varName, out.getValue().type()));
            }
        }

        Expr color = new Expr("vec4(0.0)", ValueType.VEC4);
        if (!outputIds.isEmpty()) {
            var outNode = byId.get(graph.outputs().get(0));
            if (outNode != null) {
                color = inputExpr(outNode, requirePort(outNode, OUTPUT_PORT), inputEdges, outputExprs);
                color = GlslType.convert(color, ValueType.VEC4);
            }
        }
        body.line("fragColor = " + color.code() + ';');

        return new GlslProgram(VERTEX_TEMPLATE, assembleFragment(paramUniforms, helpers, body, samplerBindings));
    }

    /**
     * 计算图所需的 sampler 绑定槽位：SAMPLER 黑板参数（按声明序）与
     * {@code texture.sample} 节点的 {@code texture} 属性，按标识去重分配
     * {@code Sampler0..SamplerN-1}。空串标识也占位（运行时绑兜底纹理）。
     */
    public static List<SamplerBinding> samplePlan(Graph graph) {
        var seen = new LinkedHashSet<String>();
        for (var p : graph.parameters()) {
            if (p.type() == ValueType.SAMPLER) {
                var value = p.defaultValue();
                seen.add(value.type() == ValueType.SAMPLER ? value.asSampler() : "");
            }
        }
        for (var n : graph.nodes()) {
            if ("texture.sample".equals(n.type())) {
                seen.add(n.properties().getOrDefault("texture", ""));
            }
        }
        var bindings = new ArrayList<SamplerBinding>();
        var index = 0;
        for (var identifier : seen) {
            bindings.add(new SamplerBinding(SamplerBinding.uniformName(index++), identifier));
        }
        return List.copyOf(bindings);
    }

    private static Expr inputExpr(GraphNode node, Port port, Map<String, Edge> inputEdges, Map<String, Expr> outputExprs) {
        var edge = inputEdges.get(node.id() + ':' + port.id());
        if (edge == null) {
            return GlslLiterals.of(port.defaultValue());
        }
        var expr = outputExprs.get(edge.from().nodeId() + ':' + edge.from().portId());
        if (expr == null) {
            throw new IllegalStateException("missing expression for " + edge.from().nodeId() + ':' + edge.from().portId());
        }
        return expr;
    }

    private static Port requirePort(GraphNode node, String portId) {
        for (var port : node.ports()) {
            if (port.id().equals(portId)) return port;
        }
        throw new IllegalStateException("missing port " + portId + " on node " + node.id());
    }

    private static String assembleFragment(Map<String, Uniform> paramUniforms, Set<String> helpers,
            GlslWriter body, List<SamplerBinding> samplers) {
        var w = new GlslWriter();
        w.line("#version 330");
        w.blank();
        w.line("layout(std140) uniform " + UNIFORM_BLOCK_NAME + " {");
        w.push();
        w.line("float " + TIME_MEMBER + ';');
        for (var u : paramUniforms.values()) {
            w.line(GlslType.of(u.type()) + ' ' + u.name() + ';');
        }
        w.pop();
        w.line("};");
        w.blank();
        for (var s : samplers) {
            w.line("uniform sampler2D " + s.uniformName() + ';');
        }
        w.line("in vec2 " + UV_VARYING + ';');
        w.line("out vec4 fragColor;");
        for (var helper : helpers) {
            w.blank();
            w.raw(helper);
        }
        w.blank();
        w.line("void main() {");
        w.raw(body.toString());
        w.line("}");
        return w.toString();
    }

    private record Uniform(String name, ValueType type) {
    }

    private static final class Context implements GlslGenContext {
        private final Map<String, Uniform> paramUniforms;
        private final Map<String, Curve> curves;
        private final Map<String, Gradient> gradients;
        private final Map<String, String> samplerNames;
        private final Set<String> helpers;

        Context(Map<String, Uniform> paramUniforms, Map<String, Curve> curves, Map<String, Gradient> gradients,
                Set<String> helpers, Map<String, String> samplerNames) {
            this.paramUniforms = paramUniforms;
            this.curves = curves;
            this.gradients = gradients;
            this.helpers = helpers;
            this.samplerNames = samplerNames;
        }

        @Override
        public String parameterUniform(String parameterId) {
            var u = paramUniforms.get(parameterId);
            if (u == null) {
                throw new IllegalStateException("unknown parameter: " + parameterId);
            }
            return u.name();
        }

        @Override
        public void addHelper(String functionSource) {
            helpers.add(functionSource);
        }

        @Override
        public Curve curve(String parameterId) {
            return curves.get(parameterId);
        }

        @Override
        public Gradient gradient(String parameterId) {
            return gradients.get(parameterId);
        }

        @Override
        public String samplerName(String identifier) {
            var name = samplerNames.get(identifier);
            if (name == null) {
                throw new IllegalStateException("no sampler binding for texture: " + identifier);
            }
            return name;
        }
    }
}
