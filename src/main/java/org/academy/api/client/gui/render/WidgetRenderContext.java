package org.academy.api.client.gui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.SubmittedCommand;

import javax.annotation.Nullable;
import java.util.*;

public final class WidgetRenderContext {
    private final List<SubmittedCommand> submittedCommands;
    private final PoseStack pose;
    private final ScissorStack scissorStack;
    private final DrawOrderStack drawOrderStack;
    private final AlphaStack alphaStack;
    private final UboFactory uboFactory;

    @FunctionalInterface
    public interface UboFactory {
        <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getOrCreate(Class<T> uboClass, int size);
    }

    public WidgetRenderContext(UboFactory uboFactory) {
        submittedCommands = new ArrayList<>();
        pose = new PoseStack();
        scissorStack = new ScissorStack();
        drawOrderStack = new DrawOrderStack();
        alphaStack = new AlphaStack();
        this.uboFactory = uboFactory;
    }

    public void submit(DrawCommand command) {
        var currentPose = pose.last().copy();
        var currentScissor = scissorStack.peek();
        var currentDrawOrder = drawOrderStack.peek();
        submittedCommands.add(new SubmittedCommand(command, currentPose, currentScissor, currentDrawOrder));
    }

    public <T extends DynamicUniformStorage.DynamicUniform> DynamicUniformStorage<T> getDynamicUbo(Class<T> uboClass, int size) {
        return uboFactory.getOrCreate(uboClass, size);
    }

    public PoseStack pose() {
        return pose;
    }

    public DrawOrderStack drawOrder() {
        return drawOrderStack;
    }

    public AlphaStack alpha() {
        return alphaStack;
    }

    public void enableScissor(ScissorRect scissorRect) {
        scissorStack.push(scissorRect);
    }

    public void disableScissor() {
        scissorStack.pop();
    }

    public List<SubmittedCommand> getCommands() {
        return submittedCommands;
    }

    public float getAccumulatedAlpha() {
        return alphaStack.peek();
    }

    public void clear() {
        submittedCommands.clear();
        drawOrderStack.clear();
        alphaStack.clear();
    }

    public static final class AlphaStack {
        private final Deque<Float> stack = new ArrayDeque<>();

        private AlphaStack() {
            stack.push(1.0F);
        }

        public void push(float alpha) {
            stack.push(peek() * alpha);
        }

        public void pop() {
            if (stack.size() > 1) {
                stack.pop();
            }
        }

        public float peek() {
            var value = stack.peek();
            return value == null ? 1.0F : value;
        }

        public void clear() {
            stack.clear();
            stack.push(1.0F);
        }
    }

    public static final class DrawOrderStack {
        private final Deque<Integer> stack = new ArrayDeque<>();

        private DrawOrderStack() {
            stack.push(0);
        }

        public void push() {
            stack.push(peek());
        }

        public void pop() {
            if (stack.size() > 1) {
                stack.pop();
            }
        }

        public void advance() {
            stack.push(stack.pop() + 1);
        }

        public int peek() {
            var value = stack.peek();
            return value == null ? 0 : value;
        }

        public void clear() {
            stack.clear();
            stack.push(0);
        }
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