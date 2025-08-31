package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.util.Mth;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.SubmittedCommand;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
        this.submittedCommands = new ArrayList<>();
        this.alphaStack = new ArrayDeque<>();
        this.pose = new PoseStack();
        this.scissorStack = new ScissorStack();
        this.uboFactory = uboFactory;
        this.alphaStack.push(1.0F);
    }

    public void submit(DrawCommand command) {
        var currentPose = this.pose.last().copy();
        var currentScissor = this.scissorStack.peek();
        this.submittedCommands.add(new SubmittedCommand(command, currentPose, currentScissor));
    }

    public <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getDynamicUbo(Class<T> uboClass, int size) {
        return this.uboFactory.getOrCreate(uboClass, size);
    }

    public PoseStack pose() {
        return this.pose;
    }

    public ScissorStack scissorStack() {
        return this.scissorStack;
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        Matrix4f poseMatrix = this.pose.last().pose();

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
        this.scissorStack.push(scissorRect);
    }

    public void disableScissor() {
        this.scissorStack.pop();
    }

    public List<SubmittedCommand> getCommands() {
        return Collections.unmodifiableList(this.submittedCommands);
    }

    public float getAccumulatedAlpha() {
        return this.alphaStack.peek();
    }

    public void pushAlpha(float alpha) {
        this.alphaStack.push(this.getAccumulatedAlpha() * alpha);
    }

    public void popAlpha() {
        if (this.alphaStack.size() > 1)
            this.alphaStack.pop();
    }

    public void clear() {
        this.submittedCommands.clear();
    }

    public static final class ScissorStack {
        private final Deque<ScissorRect> stack = new ArrayDeque<>();

        public void push(ScissorRect scissor) {
            var currentScissor = this.stack.peekLast();
            if (currentScissor != null) {
                var intersection = scissor.intersection(currentScissor);
                this.stack.addLast(Objects.requireNonNullElseGet(intersection, ScissorRect::empty));
            } else {
                this.stack.addLast(scissor);
            }
        }

        public void pop() {
            if (this.stack.isEmpty())
                throw new IllegalStateException("Scissor stack underflow");

            this.stack.removeLast();
        }

        @Nullable
        public ScissorRect peek() {
            return this.stack.peekLast();
        }

        public boolean containsPoint(int x, int y) {
            if (this.stack.isEmpty())
                return true;

            return this.stack.peekLast().containsPoint(x, y);
        }
    }
}