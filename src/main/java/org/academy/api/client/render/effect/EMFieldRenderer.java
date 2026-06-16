package org.academy.api.client.render.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.Branch;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.Knot;
import org.academy.internal.client.renderer.arc.PathProcessor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class EMFieldRenderer {
    private static final float GLOW_ALPHA_MULT = 0.25f;
    private static final float GLOW_WIDTH_MULT = 2.5f;

    private final List<FieldLine> fieldLines = new ArrayList<>();
    private float time;
    private boolean active = true;
    private long seed = 42;

    public EMFieldRenderer() {
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public FieldLine addFieldLine() {
        var line = new FieldLine();
        fieldLines.add(line);
        return line;
    }

    public void clearFieldLines() {
        fieldLines.clear();
    }

    public void update(float deltaTime) {
        if (active) time += deltaTime;
    }

    public void render(PoseStack poseStack, Camera camera, float partialTick) {
        if (!active) return;
        var t = time + partialTick;
        for (var i = 0; i < fieldLines.size(); i++) {
            var line = fieldLines.get(i);
            renderFieldLine(poseStack, line, t, camera, seed + i);
        }
    }

    private void renderFieldLine(PoseStack poseStack, FieldLine line, float currentTime, Camera camera, long lineSeed) {
        if (line.to.distance(line.from) < 0.1f) return;

        // Time-varying waviness: crackling effect
        var crackle = 1.0f + (float) Math.sin(currentTime * 8.0f + lineSeed * 0.1f) * 0.3f
                + (float) Math.sin(currentTime * 13.7f + lineSeed * 0.23f) * 0.2f;
        var dynamicWaviness = line.waviness * Math.max(0.3f, crackle);

        // Build branch paths
        var branches = new ArrayList<Branch>();
        if (line.branchCount > 0) {
            for (var b = 0; b < line.branchCount; b++) {
                var branchProgress = 0.3f + hash(lineSeed + b * 100 + 1) * 0.5f;
                var branchDir = new Vector3f(line.to).sub(line.from).normalize();
                var perp = new Vector3f(-branchDir.z, 0, branchDir.x).normalize();
                if (perp.length() < 0.01f) perp = new Vector3f(1, 0, 0);
                var spreadAngle = (hash(lineSeed + b * 200 + 2) - 0.5f) * 1.2f;
                var spreadLen = line.to.distance(line.from) * (0.15f + hash(lineSeed + b * 300 + 3) * 0.15f);
                var branchEnd = new Vector3f(perp).mul(spreadAngle * spreadLen);

                var branchPath = new ArcPath(
                        new LinePath(new Vector3f(0, 0, 0), branchEnd),
                        List.of(new JaggedModifier(dynamicWaviness * 0.7f, 3, lineSeed * 7 + b)),
                        2.0f,
                        List.of()
                );
                branches.add(new Branch(branchProgress, branchPath));
            }
        }

        var arcPath = new ArcPath(
                new LinePath(line.from, line.to),
                List.of(
                        new JaggedModifier(dynamicWaviness, line.segments, lineSeed),
                        new TaperModifier(new AttributeCurve(java.util.List.of(
                                new Knot(0.0f, 0.0f), new Knot(0.1f, 1.0f),
                                new Knot(0.9f, 1.0f), new Knot(1.0f, 0.0f)
                        )), 1.0f)
                ),
                line.segments,
                branches
        );

        var camPos = camera.position();
        var worldOffset = new Vector3f((float) camPos.x, (float) camPos.y, (float) camPos.z);

        // Glow layer: wider, more transparent, rendered first
        var glowData = PathProcessor.process(arcPath, currentTime, worldOffset);
        ArcFactory.render(poseStack, glowData,
                line.color.x * 0.6f, line.color.y * 0.6f, line.color.z * 0.6f,
                line.alpha * GLOW_ALPHA_MULT);

        // Main layer
        var renderData = PathProcessor.process(arcPath, currentTime, worldOffset);
        ArcFactory.render(poseStack, renderData,
                line.color.x, line.color.y, line.color.z, line.alpha);
    }

    private static float hash(long seed) {
        var x = (seed ^ 0x9E3779B9L) * 0x9E3779B9L;
        return (float) ((x ^ (x >>> 16)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    public static final class FieldLine {
        Vector3f from = new Vector3f();
        Vector3f to = new Vector3f(0, 1, 0);
        Vector3f color = new Vector3f(0.2f, 0.4f, 1.0f);
        float thickness = 0.03f;
        float alpha = 0.6f;
        float waviness = 1.0f;
        int segments = 16;
        int branchCount = 0;

        public FieldLine setPoints(Vector3f from, Vector3f to) {
            this.from.set(from);
            this.to.set(to);
            return this;
        }

        public FieldLine setPoints(float fx, float fy, float fz, float tx, float ty, float tz) {
            from.set(fx, fy, fz);
            to.set(tx, ty, tz);
            return this;
        }

        public FieldLine setColor(float r, float g, float b) {
            color.set(r, g, b);
            return this;
        }

        public FieldLine setThickness(float thickness) {
            this.thickness = thickness;
            return this;
        }

        public FieldLine setAlpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public FieldLine setWaviness(float waviness, int segments) {
            this.waviness = waviness;
            this.segments = segments;
            return this;
        }

        public FieldLine setBranchCount(int count) {
            branchCount = count;
            return this;
        }
    }
}
