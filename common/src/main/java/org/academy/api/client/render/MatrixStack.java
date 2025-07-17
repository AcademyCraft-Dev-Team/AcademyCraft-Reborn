package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;

public final class MatrixStack {
    private final Deque<Matrix4f> matrixStack = new ArrayDeque<>();
    private final Deque<Matrix3f> normalStack = new ArrayDeque<>();

    public MatrixStack() {
        matrixStack.add(new Matrix4f());
        normalStack.add(new Matrix3f());
    }

    public void setFrom(PoseStack.Pose pose) {
        this.matrixStack.getLast().set(pose.pose());
        this.normalStack.getLast().set(pose.normal());
    }

    @NotNull
    public Matrix4f lastMatrix() {
        return matrixStack.getLast();
    }

    @NotNull
    public Matrix3f lastNormal() {
        return normalStack.getLast();
    }

    public void pushPose() {
        matrixStack.addLast(new Matrix4f(lastMatrix()));
        normalStack.addLast(new Matrix3f(lastNormal()));
    }

    public void popPose() {
        if (matrixStack.size() > 1 && normalStack.size() > 1) {
            matrixStack.removeLast();
            normalStack.removeLast();
        }
    }

    public void translate(float x, float y, float z) {
        lastMatrix().translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        lastMatrix().scale(x, y, z);
        if (x == y && y == z) {
            return;
        }
        float invX = 1.0f / x;
        float invY = 1.0f / y;
        float invZ = 1.0f / z;
        lastNormal().scale(invX, invY, invZ);
    }

    public void mulPose(@NotNull Quaternionf quaternion) {
        lastMatrix().rotate(quaternion);
        lastNormal().rotate(quaternion);
    }
}