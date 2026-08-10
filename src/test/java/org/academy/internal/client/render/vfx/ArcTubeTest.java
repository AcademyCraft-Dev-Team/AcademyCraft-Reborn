package org.academy.internal.client.render.vfx;

import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcTubeTest {
    private static ArcPath path(Vector3f start, Vector3f end) {
        return new ArcPath(
                new LinePath(start, end),
                List.of(new JaggedModifier(0.18f, 4, 42L)),
                2.0f,
                List.of()
        );
    }

    private static void updateUntilVisible(ArcTube tube, ArcPath path, float time) {
        for (var frame = 0; frame < 200; frame++) {
            tube.build(path, time);
            if (!tube.mesh().isEmpty()) return;
        }
        throw new AssertionError("mesh never became non-empty within 200 frames");
    }

    @Test
    void meshIsBuiltSynchronouslyWithDirectExecutor() {
        var tube = new ArcTube(Runnable::run);
        updateUntilVisible(tube, path(new Vector3f(0, 0, 0), new Vector3f(10, 0, 0)), 0.0f);

        var mesh = tube.mesh();
        assertTrue(mesh.vertexCount() > 0, "arc mesh should have vertices");
        assertTrue(mesh.indexCount() > 0, "arc mesh should have indices");
        assertTrue(mesh.version() >= 0, "arc mesh should carry a version");
    }

    @Test
    void meshAppearsOnlyAfterWorkerRuns() {
        var executor = new SingleUseExecutor();
        var tube = new ArcTube(executor);

        tube.build(path(new Vector3f(0, 1, 0), new Vector3f(10, 1, 0)), 0.0f);
        assertTrue(tube.mesh().isEmpty(), "mesh must not be visible before the worker runs");

        executor.runAll();
        assertFalse(tube.mesh().isEmpty(), "mesh must be published after the worker runs");
        assertTrue(tube.mesh().vertexCount() > 0);
    }

    @Test
    void emptyPathProducesNoMesh() {
        var tube = new ArcTube(Runnable::run);
        var zeroLength = new ArcPath(
                new LinePath(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0)),
                List.of(new JaggedModifier(0.18f, 4, 7L)),
                2.0f,
                List.of()
        );
        for (var frame = 0; frame < 10; frame++) {
            tube.build(zeroLength, 0.0f);
        }
        assertTrue(tube.mesh().isEmpty(), "degenerate path should yield no mesh");
    }

    /**
     * 记录所有任务，由测试手动触发，模拟真正的后台延迟。
     */
    private static final class SingleUseExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            for (var task : tasks) {
                task.run();
            }
            tasks.clear();
        }
    }
}
