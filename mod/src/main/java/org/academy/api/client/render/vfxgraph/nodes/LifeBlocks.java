package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.graph.type.CurveSampler;
import org.academy.api.client.render.graph.type.GradientSampler;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

final class LifeBlocks {
    private LifeBlocks() {
    }

    static SimNode color(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var gradientId = BlockProps.getString(properties, "gradient", "");
        var filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            var gradient = ctx.gradient(gradientId);
            if (gradient == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                var c = GradientSampler.sample(gradient, BlockSim.lifeT(buf, i));
                buf.setColorRgb(i, c.x, c.y, c.z);
            }
        };
    }

    static SimNode alpha(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var curveId = BlockProps.getString(properties, "curve", "");
        var filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                buf.setAlpha(i, buf.startAlpha(i) * CurveSampler.sample(curve, BlockSim.lifeT(buf, i)));
            }
        };
    }

    static SimNode size(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var curveId = BlockProps.getString(properties, "curve", "");
        var filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                buf.setSizeScaled(i, buf.startSize(i) * CurveSampler.sample(curve, BlockSim.lifeT(buf, i)));
            }
        };
    }

    static SimNode velocity(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        var curveId = BlockProps.getString(properties, "curve", "");
        var filter = BlockSim.layerFilter(properties);
        return (buf, ctx) -> {
            var curve = ctx.curve(curveId);
            if (curve == null) return;
            for (var i = 0; i < buf.count(); i++) {
                if (filter >= 0 && buf.layer(i) != filter) continue;
                var s = CurveSampler.sample(curve, BlockSim.lifeT(buf, i));
                buf.setVelocity(i,
                        buf.velocityX(i) * s,
                        buf.velocityY(i) * s,
                        buf.velocityZ(i) * s);
            }
        };
    }
}
