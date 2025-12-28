package org.academy.internal.client.renderer.arc;

import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.BasePath;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

public final class PathProcessor {
    private PathProcessor() {
    }

    public static ArcFactory.ArcRenderData process(ArcPath rootPath, float time, Vector3fc cameraPos) {
        return processRecursive(rootPath, time, new Matrix4f().identity(), 0, cameraPos);
    }

    private static ArcFactory.ArcRenderData processRecursive(ArcPath currentPath, float time, Matrix4f transform, int depth, Vector3fc cameraPos) {
        var worldSpacePath = currentPath.path().transform(transform);
        var pathData = generateLinearData(worldSpacePath, currentPath.modifiers(), currentPath.resolution(), time);
        var renderData = ArcFactory.Generator.generate(pathData, 0.1f, cameraPos);

        if (!currentPath.branches().isEmpty() && !pathData.getFrames().isEmpty()) {
            for (var branch : currentPath.branches()) {
                var frameCount = pathData.getFrames().size();
                var frameIndex = Math.min(frameCount - 1, (int) (frameCount * branch.attachmentProgress()));
                var attachmentFrame = pathData.getFrames().get(frameIndex);

                var childTransform = calculateChildTransform(attachmentFrame);
                renderData.branches.add(processRecursive(branch.child(), time, childTransform, depth + 1, cameraPos));
            }
        }

        return renderData;
    }

    private static PathData generateLinearData(BasePath path, List<PathModifier> modifiers, float resolution, float time) {
        var currentData = path.generate(resolution);
        for (var modifier : modifiers) {
            currentData = modifier.apply(currentData, time);
        }
        return currentData;
    }

    private static Matrix4f calculateChildTransform(PathFrame frame) {
        var position = frame.position();
        var tangent = frame.tangent();
        var normal = frame.normal();
        var binormal = new Vector3f(tangent).cross(normal);

        var transform = new Matrix4f();
        transform.set(
                binormal.x(), binormal.y(), binormal.z(), 0.0f,
                normal.x(),   normal.y(),   normal.z(),   0.0f,
                tangent.x(),  tangent.y(),  tangent.z(),  0.0f,
                position.x(), position.y(), position.z(), 1.0f
        );
        return transform;
    }
}