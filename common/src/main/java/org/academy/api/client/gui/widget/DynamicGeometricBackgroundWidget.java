package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class DynamicGeometricBackgroundWidget extends AbstractWidget {
    private static class Point {
        float x, y;
        float vx, vy;

        Point(float x, float y, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }

    private final List<Point> points;

    private final int numPoints;
    private final float pointSpeed;
    private final float connectionDistanceSq;
    private final int lineColor;
    private final boolean mouseInteraction;
    private final float mouseInteractionRadiusSq;

    private double relativeMouseX, relativeMouseY;
    private boolean isInteracting = false;
    private int interactingButton = -1;


    public DynamicGeometricBackgroundWidget(float x, float y, float width, float height,
                                            int numPoints, float pointSpeed, float connectionDistance,
                                            int lineColor, boolean mouseInteraction, float mouseInteractionRadius) {
        super(x, y, width, height);
        this.numPoints = numPoints;
        this.pointSpeed = pointSpeed;
        this.connectionDistanceSq = connectionDistance * connectionDistance;
        this.lineColor = lineColor;
        this.mouseInteraction = mouseInteraction;
        this.mouseInteractionRadiusSq = mouseInteractionRadius * mouseInteractionRadius;
        this.points = new ArrayList<>(numPoints);
        initPoints();
    }

    private void initPoints() {
        points.clear();
        for (int i = 0; i < numPoints; i++) {
            Random random = MathUtil.RANDOM;
            float px = random.nextFloat() * getWidth();
            float py = random.nextFloat() * getHeight();
            float angle = random.nextFloat() * 2 * (float) Math.PI;
            float speed = this.pointSpeed * (0.7f + random.nextFloat() * 0.6f);
            float pvx = (float) Math.cos(angle) * speed;
            float pvy = (float) Math.sin(angle) * speed;
            points.add(new Point(px, py, pvx, pvy));
        }
    }

    private void updatePoints() {
        float dt = ClientUtil.animationFactor(1);

        for (Point p : points) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;

            if (p.x < 0) {
                p.x = 0;
                p.vx *= -1;
            } else if (p.x > getWidth()) {
                p.x = getWidth();
                p.vx *= -1;
            }
            if (p.y < 0) {
                p.y = 0;
                p.vy *= -1;
            } else if (p.y > getHeight()) {
                p.y = getHeight();
                p.vy *= -1;
            }

            if (mouseInteraction && isInteracting && isHovered()) {
                float dx = p.x - (float) relativeMouseX;
                float dy = p.y - (float) relativeMouseY;
                float distSq = dx * dx + dy * dy;

                if (distSq < mouseInteractionRadiusSq && distSq > 0.001f) {
                    float dist = (float) Math.sqrt(distSq);
                    float forceMagnitude = (1f - dist / (float) Math.sqrt(mouseInteractionRadiusSq)) * pointSpeed * 0.5f;

                    if (interactingButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        forceMagnitude *= -1;
                    }

                    p.vx -= (dx / dist) * forceMagnitude * dt * 20;
                    p.vy -= (dy / dist) * forceMagnitude * dt * 20;

                    float speedSq = p.vx * p.vx + p.vy * p.vy;
                    float maxSpeed = pointSpeed * 2.0f;
                    if (speedSq > maxSpeed * maxSpeed) {
                        float actualSpeed = (float) Math.sqrt(speedSq);
                        p.vx = (p.vx / actualSpeed) * maxSpeed;
                        p.vy = (p.vy / actualSpeed) * maxSpeed;
                    }
                }
            }
        }
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (mouseInteraction && isHovered() && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            this.isInteracting = true;
            this.interactingButton = button;
            this.relativeMouseX = mouseX - getAbsoluteX();
            this.relativeMouseY = mouseY - getAbsoluteY();
            return true;
        }
        return super.mousePressed(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mouseInteraction && isInteracting && button == this.interactingButton) {
            this.isInteracting = false;
            this.interactingButton = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        if (isHovered()) {
            this.relativeMouseX = mouseX - getAbsoluteX();
            this.relativeMouseY = mouseY - getAbsoluteY();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        updatePoints();

        VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(RenderType.lines());
        Matrix4f matrix = guiGraphics.pose().last().pose();

        float r = (float) (lineColor >> 16 & 255) / 255.0F;
        float g = (float) (lineColor >> 8 & 255) / 255.0F;
        float b = (float) (lineColor & 255) / 255.0F;
        float a = (float) (lineColor >> 24 & 255) / 255.0F;
        if (a == 0 && (r != 0 || g != 0 || b != 0)) a = 1.0f;
        if (r == 0 && g == 0 && b == 0 && a == 0) {
            r = g = b = a = 1.0f;
        }

        points.sort(Comparator.comparingDouble(p -> p.x));
        float connectionDistance = (float) Math.sqrt(connectionDistanceSq);

        for (int i = 0; i < points.size(); i++) {
            Point p1 = points.get(i);
            for (int j = i + 1; j < points.size(); j++) {
                Point p2 = points.get(j);

                if (p2.x - p1.x > connectionDistance) {
                    break;
                }

                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distanceSq = dx * dx + dy * dy;

                if (distanceSq < connectionDistanceSq) {
                    float dist = (float) Math.sqrt(distanceSq);
                    float lineAlpha = a * (1f - dist / connectionDistance);
                    lineAlpha = Math.max(0, Math.min(lineAlpha, 1.0f));


                    vertexConsumer.vertex(matrix, getX() + p1.x, getY() + p1.y, getZ())
                            .color(r, g, b, lineAlpha).normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrix, getX() + p2.x, getY() + p2.y, getZ())
                            .color(r, g, b, lineAlpha).normal(0, 0, 1).endVertex();
                }
            }
        }
    }
}