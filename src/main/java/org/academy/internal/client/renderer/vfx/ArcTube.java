package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.lightning.*;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.api.common.profiler.AcademyProfiler;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 异步电弧网格：路径采样与网格构建在后台线程执行，完成后发布不可变快照。
 * 渲染线程只读最新已发布快照，绝不触碰正在计算的缓冲。
 */
public final class ArcTube implements TubeMesh {
    private static final int SEGMENT_RESOLUTION = 4;
    private static final float BASE_RADIUS = 0.004f;
    private static final TubeMeshView EMPTY = new TubeMeshView() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int vertexCount() {
            return 0;
        }

        @Override
        public int indexCount() {
            return 0;
        }

        @Override
        public float[] positions() {
            return new float[0];
        }

        @Override
        public float[] uvs() {
            return new float[0];
        }

        @Override
        public int[] indices() {
            return new int[0];
        }

        @Override
        public long version() {
            return -1;
        }

        @Override
        public void packIndices(ByteBuffer buffer) {
            buffer.clear();
            buffer.flip();
        }
    };
    private final Executor executor;
    private final Object lock = new Object();
    private @Nullable ArcPath pendingPath;
    private float pendingTime;
    private boolean hasRequest;
    private boolean scheduled;
    private volatile @Nullable LightningMeshBuilder published;
    private long publishedVersion = -1;

    public ArcTube() {
        this(ArcExecutor.get());
    }

    ArcTube(Executor executor) {
        this.executor = executor;
    }

    private static Vector3f initialRight(Vector3fc tangent) {
        var right = new Vector3f(0, 1, 0).cross(tangent).normalize();
        if (right.lengthSquared() < 1.0e-6f) {
            right = new Vector3f(1, 0, 0);
        }
        return right;
    }

    private static void collectFrames(
            ArcPath currentPath,
            float time,
            Matrix4f transform,
            List<List<Frame>> out
    ) {
        var worldSpacePath = currentPath.path().transform(transform);
        var data = worldSpacePath.generate(currentPath.resolution());
        for (var modifier : currentPath.modifiers()) {
            data = modifier.apply(data, time);
        }
        var frames = data.getFrames();
        var thickness = data.getProperty(PropertyType.THICKNESS);
        if (frames.size() >= 2) {
            var collected = new ArrayList<Frame>(frames.size());
            for (var i = 0; i < frames.size(); i++) {
                var frame = frames.get(i);
                var radius = thickness != null ? Math.max(0.0f, thickness.get(i)) : 1.0f;
                collected.add(new Frame(frame.position(), frame.tangent(), radius));
            }
            out.add(collected);
        }

        if (!currentPath.branches().isEmpty() && !frames.isEmpty()) {
            var frameCount = frames.size();
            for (var branch : currentPath.branches()) {
                var frameIndex = Math.min(frameCount - 1, (int) (frameCount * branch.attachmentProgress()));
                var attachmentFrame = frames.get(frameIndex);
                var childTransform = calculateChildTransform(attachmentFrame);
                collectFrames(branch.child(), time, childTransform, out);
            }
        }
    }

    private static Matrix4f calculateChildTransform(PathFrame frame) {
        var position = frame.position();
        var tangent = frame.tangent();
        var normal = frame.normal();
        var binormal = new Vector3f(tangent).cross(normal);

        var transform = new Matrix4f();
        transform.set(
                binormal.x(), binormal.y(), binormal.z(), 0.0f,
                normal.x(), normal.y(), normal.z(), 0.0f,
                tangent.x(), tangent.y(), tangent.z(), 0.0f,
                position.x(), position.y(), position.z(), 1.0f
        );
        return transform;
    }

    /**
     * 渲染线程调用：记录最新输入并提交一次后台计算，不阻塞。
     */
    public void build(ArcPath rootPath, float time) {
        boolean submit;
        synchronized (lock) {
            pendingPath = rootPath;
            pendingTime = time;
            hasRequest = true;
            submit = !scheduled;
            scheduled = true;
        }
        if (submit) {
            executor.execute(this::processUpdate);
        }
    }

    private void processUpdate() {
        ArcPath path;
        float time;
        boolean hasWork;
        synchronized (lock) {
            hasWork = hasRequest;
            hasRequest = false;
            if (!hasWork) {
                scheduled = false;
                if (hasRequest) {
                    scheduled = true;
                    executor.execute(this::processUpdate);
                }
                return;
            }
            path = pendingPath;
            time = pendingTime;
        }

        var rebuilt = new LightningMeshBuilder();
        var version = buildMesh(path, time, rebuilt);
        if (version != -1) {
            rebuilt.setVersion(publishedVersion + 1);
            publishedVersion++;
            published = rebuilt;
        }

        synchronized (lock) {
            if (hasRequest) {
                scheduled = true;
                executor.execute(this::processUpdate);
            } else {
                scheduled = false;
            }
        }
    }

    /**
     * 构建网格；无有效帧返回 -1（不发布）。
     */
    private long buildMesh(ArcPath rootPath, float time, LightningMeshBuilder target) {
        var paths = new ArrayList<List<Frame>>();
        var branches = new ArrayList<LightningBranch>();
        var pointRadii = new ArrayList<Float>();
        AcademyProfiler.runZone("academy.vfx.arc.shape", () -> {
            collectFrames(rootPath, time, new Matrix4f().identity(), paths);
            for (var frames : paths) {
                if (frames.size() < 2) continue;
                var points = new ArrayList<LightningPoint>(frames.size());
                Vector3f prevTangent = null;
                Vector3f prevRight = null;
                for (var frame : frames) {
                    var tangent = frame.tangent();
                    Vector3f right;
                    if (prevRight == null) {
                        right = initialRight(tangent);
                    } else {
                        var rotation = new Quaternionf().rotationTo(prevTangent, tangent);
                        right = new Vector3f(prevRight).rotate(rotation);
                        if (right.lengthSquared() < 1.0e-8f) {
                            right = initialRight(tangent);
                        }
                    }
                    var up = new Vector3f(tangent).cross(right);
                    points.add(new LightningPoint(
                            new Vector3f(frame.position()),
                            new Vector3f(tangent),
                            right,
                            up,
                            true
                    ));
                    pointRadii.add(frame.radius());
                    prevTangent = new Vector3f(tangent);
                    prevRight = right;
                }
                branches.add(new LightningBranch(0, 0, 1.0f, 1.0f, points));
            }
        });

        if (branches.isEmpty()) {
            return -1;
        }
        var radii = new float[pointRadii.size()];
        for (var i = 0; i < radii.length; i++) {
            radii[i] = pointRadii.get(i);
        }
        AcademyProfiler.runZone("academy.vfx.arc.mesh", () -> target.update(branches, SEGMENT_RESOLUTION, BASE_RADIUS, radii));
        return 1;
    }

    @Override
    public TubeMeshView mesh() {
        var current = published;
        return current == null ? EMPTY : current;
    }

    private record Frame(Vector3fc position, Vector3f tangent, float radius) {
    }
}
