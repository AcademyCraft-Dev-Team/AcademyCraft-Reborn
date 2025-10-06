package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.util.Mth;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

public final class WidgetRenderContext {
    private final List<SubmittedCommand> submittedCommands;
    private final Deque<Float> alphaStack;
    private final PoseStack pose;
    public final ScissorStack scissorStack;
    private final UboFactory uboFactory;

    @FunctionalInterface
    public interface UboFactory {
        <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getOrCreate(Class<T> uboClass, int size);
    }

    public WidgetRenderContext(UboFactory uboFactory) {
        submittedCommands = new ArrayList<>();
        alphaStack = new ArrayDeque<>();
        pose = new PoseStack();
        scissorStack = new ScissorStack();
        this.uboFactory = uboFactory;
        alphaStack.push(1.0F);
    }

    public void submit(DrawCommand command) {
        var currentPose = pose.last().copy();
        var currentScissor = scissorStack.peek();
        submittedCommands.add(new SubmittedCommand(command, currentPose, currentScissor));
    }

    public <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getDynamicUbo(Class<T> uboClass, int size) {
        return uboFactory.getOrCreate(uboClass, size);
    }

    public PoseStack pose() {
        return pose;
    }

    public ScissorStack scissorStack() {
        return scissorStack;
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        var poseMatrix = pose.last().pose();

        var v0 = poseMatrix.transformPosition(minX, minY, 0, new Vector3f());
        var v1 = poseMatrix.transformPosition(maxX, minY, 0, new Vector3f());
        var v2 = poseMatrix.transformPosition(minX, maxY, 0, new Vector3f());
        var v3 = poseMatrix.transformPosition(maxX, maxY, 0, new Vector3f());

        float finalMinX = Math.min(Math.min(v0.x(), v2.x()), Math.min(v1.x(), v3.x()));
        float finalMaxX = Math.max(Math.max(v0.x(), v2.x()), Math.max(v1.x(), v3.x()));
        float finalMinY = Math.min(Math.min(v0.y(), v2.y()), Math.min(v1.y(), v3.y()));
        float finalMaxY = Math.max(Math.max(v0.y(), v2.y()), Math.max(v1.y(), v3.y()));

        var scissorRect = new ScissorRect(
                Mth.floor(finalMinX),
                Mth.floor(finalMinY),
                Mth.ceil(finalMaxX - finalMinX),
                Mth.ceil(finalMaxY - finalMinY)
        );
        scissorStack.push(scissorRect);
    }

    public void disableScissor() {
        scissorStack.pop();
    }

    public List<SubmittedCommand> getCommands() {
        return Collections.unmodifiableList(submittedCommands);
    }

    public float getAccumulatedAlpha() {
        var alpha =  alphaStack.peek();
        return alpha == null ? 1 : alpha;
    }

    public void pushAlpha(float alpha) {
        alphaStack.push(getAccumulatedAlpha() * alpha);
    }

    public void popAlpha() {
        if (alphaStack.size() > 1) alphaStack.pop();
    }

    public void clear() {
        submittedCommands.clear();
    }

    public static final class ScissorStack {
        private final Deque<ScissorRect> stack = new ArrayDeque<>();

        public void push(ScissorRect scissor) {
            var currentScissor = stack.peekLast();
            if (currentScissor != null) {
                var intersection = scissor.intersection(currentScissor);
                stack.addLast(Objects.requireNonNullElseGet(intersection, ScissorRect::empty));
            } else {
                stack.addLast(scissor);
            }
        }

        public void pop() {
            if (stack.isEmpty())
                throw new IllegalStateException("Scissor stack underflow");

            stack.removeLast();
        }

        @Nullable
        public ScissorRect peek() {
            return stack.peekLast();
        }

        public boolean containsPoint(int x, int y) {
            if (stack.isEmpty())
                return true;

            return stack.peekLast().containsPoint(x, y);
        }
    }
}