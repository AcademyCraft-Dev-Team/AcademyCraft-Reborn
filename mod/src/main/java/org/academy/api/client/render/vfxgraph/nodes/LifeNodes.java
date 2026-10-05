package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.model.GraphNode;
import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.GradientSampler;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

import java.util.Map;

final class LifeNodes {
    private LifeNodes() {
    }

    static SimNode color(GraphNode node) {
        var properties = node.properties();
        var gradientId = BlockProps.getString(properties, "gradient", "");
        var filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            var gradient = ctx.gradient(gradientId);
            if (gradient == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                var t = BlockSim.lifeT(buf, i);
                var c = GradientSampler.sample(gradient, t);
                buf.setColor(i, c.x, c.y, c.z, buf.alpha(i));
            }
        };
    }

    static SimNode alpha(GraphNode node) {
        return curve(node.properties(), (buf, i, s) -> buf.setAlpha(i, buf.startAlpha(i) * s));
    }

    static SimNode size(GraphNode node) {
        return curve(node.properties(), (buf, i, s) -> buf.setSizeScaled(i, buf.startSize(i) * s));
    }

    static SimNode velocity(GraphNode node) {
        return curve(node.properties(), (buf, i, s) -> buf.setVelocity(i,
                buf.velocityX(i) * s,
                buf.velocityY(i) * s,
                buf.velocityZ(i) * s));
    }

    private static SimNode curve(Map<String, String> properties, CurveWriter writer) {
        var curveId = BlockProps.getString(properties, "curve", "");
        var filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                writer.write(buf, i, CurveSampler.sample(curve, BlockSim.lifeT(buf, i)));
            }
        };
    }

    @FunctionalInterface
    private interface CurveWriter {
        void write(org.academy.api.client.render.vfxgraph.sim.ParticleBuffer buf, int i, float s);
    }
}
