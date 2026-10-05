package org.academy.api.client.render.vfxgraph.nodes;

import net.minecraft.util.Mth;
import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.shape.FirstPersonBodyOcclusion;
import org.academy.api.client.render.vfxgraph.shape.VortexAttackGeometry;
import org.academy.api.client.render.vfxgraph.shape.VortexJetGeometry;
import org.academy.api.client.render.vfxgraph.sim.SimNode;
import org.academy.api.common.ability.VortexAttackPattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class VortexJetBlock {
    private VortexJetBlock() {
    }

    static SimNode create(VfxBlock block, PortValueSource ports) {
        var properties = block.properties();
        float side = BlockProps.getFloat(properties, "side", 1f) < 0f ? -1f : 1f;
        float root = BlockProps.getFloat(properties, "root_x", 0.18f);
        float length = Math.max(0.1f, BlockProps.getFloat(properties, "length", 5.2f));
        float rise = BlockProps.getFloat(properties, "rise", 3f);
        float back = BlockProps.getFloat(properties, "back", 2.5f);
        float radius = Math.max(0.01f, BlockProps.getFloat(properties, "radius", 0.65f));
        float turns = BlockProps.getFloat(properties, "turns", 5.5f);
        float speed = BlockProps.getFloat(properties, "speed", 8f);
        float phase = BlockProps.getFloat(properties, "phase", 0f);
        int filaments = Math.clamp(BlockProps.getInt(properties, "filaments", 18), 3, 48);
        int segments = Math.clamp(BlockProps.getInt(properties, "segments", 100), 24, 192);
        int flecks = Math.clamp(BlockProps.getInt(properties, "flecks", 24), 0, 96);
        int highlights = Math.clamp(BlockProps.getInt(properties, "highlight_count", 10), 0, 24);
        float highlightSpeed = Math.max(0f, BlockProps.getFloat(properties, "highlight_speed", 0.22f));
        float[] highlightColor = BlockProps.getColor(properties, "highlight_color");
        float hollow = BlockSim.clamp01(BlockProps.getFloat(properties, "hollow", 0.8f));
        String sweepParam = BlockProps.getString(properties, "sweep_param", "sweep_right");
        String pitchParam = BlockProps.getString(properties, "pitch_param", "pitch_right");
        float[] color = BlockProps.getColor(properties, "color");
        long group = ArcSupport.newTransientGroup();
        var point = new Vector3f();
        var spine = new Vector3f();
        var surfaceNormal = new Vector3f();
        var rotation = new Quaternionf();
        var shape = new VortexAttackGeometry();
        var bodyOcclusion = new FirstPersonBodyOcclusion();
        return (buf, ctx) -> {
            ctx.arcs().removeGroup(group);
            float radial = Math.max(0f, ctx.paramFloat("radial_scale", 1f));
            float axial = Math.max(0f, ctx.paramFloat("length_scale", 1f));
            float alpha = BlockSim.clamp01(ctx.paramFloat("opacity", 1f));
            float spread = Math.max(0f, ctx.paramFloat("spread_scale", 1f));
            if (radial < 0.001f || axial < 0.001f || alpha < 0.001f) return;
            float time = ctx.paramFloat("vortex_time", ctx.time()) + phase;
            int liveSegments = Math.clamp(Math.round(ctx.paramFloat("vortex_segments", segments)), 24, segments);
            int liveFilaments = Math.clamp(Math.round(ctx.paramFloat("vortex_filaments", filaments)), 3, filaments);
            int liveFlecks = Math.clamp(Math.round(ctx.paramFloat("vortex_flecks", flecks)), 0, flecks);
            int tubeSegments = Math.clamp(Math.round(ctx.paramFloat("vortex_rings", 8)), 3, 8);
            rotation.identity().rotateY(ctx.paramFloat(sweepParam, 0f) * Mth.DEG_TO_RAD)
                    .rotateZ(ctx.paramFloat(pitchParam, 0f) * Mth.DEG_TO_RAD);
            int attackMode = Math.clamp(Math.round(ctx.paramFloat("attack_mode", 0f)), 0, 5);
            float attackProgress = ctx.paramFloat("attack_progress", 1f);
            boolean clipBody = ctx.paramFloat("first_person_body_clip", 0f) > 0.5f && attackMode == 0;
            if (clipBody) {
                bodyOcclusion.eye.set(ctx.paramVec3("body_clip_eye", 0, 0), ctx.paramVec3("body_clip_eye", 1, 0), ctx.paramVec3("body_clip_eye", 2, 0));
                bodyOcclusion.min.set(ctx.paramVec3("body_clip_min", 0, 0), ctx.paramVec3("body_clip_min", 1, 0), ctx.paramVec3("body_clip_min", 2, 0));
                bodyOcclusion.max.set(ctx.paramVec3("body_clip_max", 0, 0), ctx.paramVec3("body_clip_max", 1, 0), ctx.paramVec3("body_clip_max", 2, 0));
            }
            if (attackProgress < 0f) {
                float duration = VortexAttackPattern.byId(attackMode).durationSeconds();
                attackProgress = (ctx.time() % duration) / duration;
            }
            if (attackMode == 4 && side > 0f || attackMode == 5 && side < 0f) attackMode = 0;
            int branchCount = attackMode == 3 && attackProgress > 0f && attackProgress < 1f ? 2 : 1;
            for (int branch = 0; branch < branchCount; branch++) {
                float targetX = ctx.paramFloat("attack_target_x", 0f);
                float targetY = ctx.paramFloat("attack_target_y", -1.2f);
                float targetZ = ctx.paramFloat("attack_target_z", 12f);
                if (attackMode == 3) {
                    String corner = "attack_corner_" + ((side < 0f ? 0 : 2) + branch);
                    targetX = ctx.paramVec3(corner, 0, side * 6f);
                    targetY = ctx.paramVec3(corner, 1, -1.2f);
                    targetZ = ctx.paramVec3(corner, 2, branch == 0 ? 6f : -6f);
                }
                shape.configure(attackMode, attackProgress, branch,
                        targetX * side - root, targetY, targetZ,
                        length * axial * spread, rise * axial, back * axial,
                        radius * radial, turns, speed, time);
                shape.prepareGrid(liveSegments);
                float branchAlpha = branch == 0 ? 1f : shape.activity();
                if (branchAlpha < 0.001f) continue;
                for (int strand = 0; strand <= liveFilaments; strand++) {
                    boolean core = strand == 0;
                    float strandPhase = strand * 2.399963f + phase;
                    float orbit = core ? 0f : 0.70f + 0.32f * BlockSim.stableUnit(strand, 3.17f);
                    var arc = ctx.arcs().add(group);
                    arc.setMaxTubeSegments(tubeSegments);
                    for (int segment = 0; segment <= liveSegments; segment++) {
                        float u = segment / (float) liveSegments;
                        shape.sample(u, orbit, strandPhase, point);
                        point.x *= side;
                        rotation.transform(point);
                        float localRadius = VortexJetGeometry.radius(u, radius * radial);
                        float width = localRadius * (core ? 0.72f : 0.12f + 0.05f * BlockSim.stableUnit(strand, 6.1f));
                        width *= 0.86f + 0.14f * (float) Math.sin(u * 57f - time * speed + strandPhase);
                        width *= Math.clamp((1f - u) * 28f, 0.015f, 1f) * shape.widthScale(u);
                        boolean hidden = clipBody && bodyOcclusion.occludes(point, side * root);
                        if (hidden) width = 0f;
                        int run = hidden || VortexJetGeometry.hollow(u, time, strand, hollow) ? segment + 1 : 0;
                        arc.addPoint(point.x + side * root, point.y, point.z, width, 0f, run);
                    }
                    float shade = core ? 0.48f : 0.8f + 1.6f * BlockSim.stableUnit(strand, 8.1f);
                    arc.setColor(color[0] * shade, color[1] * shade, color[2] * shade, color[3] * alpha * branchAlpha);
                    arc.setLifetime(1f);
                    arc.setSeed(branch * 10000L + strand + 1);
                    arc.setNoiseStrength(0f);
                    arc.setDriftSpeed(0f);
                }
                for (int highlight = 0; highlight < highlights; highlight++) {
                    int strand = 1 + (highlight * 7) % filaments;
                    if (strand > liveFilaments) continue;
                    float strandPhase = strand * 2.399963f + phase;
                    float orbit = 0.70f + 0.32f * BlockSim.stableUnit(strand, 3.17f);
                    float start = (BlockSim.stableUnit(highlight, 4.71f) + time * highlightSpeed) % 1f;
                    float pulseLength = 1.04f * (0.045f + 0.035f * BlockSim.stableUnit(highlight, 7.31f));
                    float fade = Math.min(1f, start * 16f) * Math.min(1f, (1f - start) * 14f);
                    var arc = ctx.arcs().add(group);
                    arc.setMaxTubeSegments(tubeSegments);
                    for (int j = 0; j <= 12; j++) {
                        float u = Math.min(1f, start + pulseLength * j / 12f);
                        shape.sample(u, orbit, strandPhase, point);
                        float width = VortexJetGeometry.radius(u, radius * radial)
                                * (0.12f + 0.05f * BlockSim.stableUnit(strand, 6.1f));
                        width *= 0.86f + 0.14f * (float) Math.sin(u * 57f - time * speed + strandPhase);
                        width *= Math.clamp((1f - u) * 28f, 0.015f, 1f) * shape.widthScale(u);
                        shape.sample(u, 0f, strandPhase, spine);
                        point.sub(spine, surfaceNormal).normalize();
                        point.fma(width * 0.98f, surfaceNormal);
                        point.x *= side;
                        rotation.transform(point);
                        width = Math.min(width * 0.20f, 0.012f * radial)
                                * (float) Math.sin(Math.PI * j / 12f);
                        boolean hidden = clipBody && bodyOcclusion.occludes(point, side * root);
                        if (hidden) width = 0f;
                        int run = hidden || VortexJetGeometry.hollow(u, time, strand, hollow) ? j + 1 : 0;
                        arc.addPoint(point.x + side * root, point.y, point.z, width, 0f, run);
                    }
                    arc.setColor(highlightColor[0], highlightColor[1], highlightColor[2],
                            highlightColor[3] * alpha * fade * branchAlpha);
                    arc.setLifetime(1f);
                    arc.setSeed(branch * 10000L + 1000 + highlight);
                    arc.setNoiseStrength(0f);
                    arc.setDriftSpeed(0f);
                }
                for (int fleck = 0; fleck < liveFlecks; fleck++) {
                    float u = (BlockSim.stableUnit(fleck, 2.13f) + time * 0.22f) % 1f;
                    float fade = Math.min(1f, u * 12f) * Math.min(1f, (1f - u) * 12f);
                    float orbit = 1.15f + BlockSim.stableUnit(fleck, 9.31f) * 0.6f;
                    var arc = ctx.arcs().add(group);
                    arc.setMaxTubeSegments(tubeSegments);
                    for (int j = 0; j < 3; j++) {
                        float v = Math.min(1f, u + j * 0.006f);
                        shape.sample(v, orbit, fleck * 2.4f, point);
                        point.x *= side;
                        rotation.transform(point);
                        boolean hidden = clipBody && bodyOcclusion.occludes(point, side * root);
                        arc.addPoint(point.x + side * root, point.y, point.z,
                                hidden ? 0f : (j == 1 ? 0.019f : 0.002f) * radial * fade, 0f, hidden ? j + 1 : 0);
                    }
                    arc.setColor(color[0], color[1], color[2], alpha * fade * 0.8f * branchAlpha);
                    arc.setLifetime(1f);
                    arc.setSeed(branch * 10000L + fleck + 100);
                    arc.setNoiseStrength(0f);
                    arc.setDriftSpeed(0f);
                }
            }
        };
    }
}
