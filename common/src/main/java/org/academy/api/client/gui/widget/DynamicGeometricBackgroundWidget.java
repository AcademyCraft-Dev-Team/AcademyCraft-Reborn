package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.util.MathUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
                                            int newNumPoints, float newPointSpeed, float connectionDistance,
                                            int newLineColor, boolean newMouseInteraction, float mouseInteractionRadius) {
        super(x, y, width, height);
        numPoints = newNumPoints;
        pointSpeed = newPointSpeed;
        connectionDistanceSq = connectionDistance * connectionDistance;
        lineColor = newLineColor;
        mouseInteraction = newMouseInteraction;
        mouseInteractionRadiusSq = mouseInteractionRadius * mouseInteractionRadius;
        points = new ArrayList<>(newNumPoints);
        initPoints();
    }

    private void initPoints() {
        points.clear();
        for (var i = 0; i < numPoints; i++) {
            var random = MathUtil.RANDOM;
            var px = random.nextFloat() * getWidth();
            var py = random.nextFloat() * getHeight();
            var angle = random.nextFloat() * 2 * (float) Math.PI;
            var speed = pointSpeed * (0.7f + random.nextFloat() * 0.6f);
            var pvx = (float) Math.cos(angle) * speed;
            var pvy = (float) Math.sin(angle) * speed;
            points.add(new Point(px, py, pvx, pvy));
        }
    }

    private void updatePoints() {
        var dt = ClientUtil.animationFactor(1);

        for (var p : points) {
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
                var dx = p.x - (float) relativeMouseX;
                var dy = p.y - (float) relativeMouseY;
                var distSq = dx * dx + dy * dy;

                if (distSq < mouseInteractionRadiusSq && distSq > 0.001f) {
                    var dist = (float) Math.sqrt(distSq);
                    var forceMagnitude = (1f - dist / (float) Math.sqrt(mouseInteractionRadiusSq)) * pointSpeed * 0.5f;

                    if (interactingButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        forceMagnitude *= -1;
                    }

                    p.vx -= (dx / dist) * forceMagnitude * dt * 20;
                    p.vy -= (dy / dist) * forceMagnitude * dt * 20;

                    var speedSq = p.vx * p.vx + p.vy * p.vy;
                    var maxSpeed = pointSpeed * 2.0f;
                    if (speedSq > maxSpeed * maxSpeed) {
                        var actualSpeed = (float) Math.sqrt(speedSq);
                        p.vx = (p.vx / actualSpeed) * maxSpeed;
                        p.vy = (p.vy / actualSpeed) * maxSpeed;
                    }
                }
            }
        }
    }

    @Override
    public boolean mousePressed(double newMouseX, double newMouseY, int button) {
        if (mouseInteraction && isHovered() && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            isInteracting = true;
            interactingButton = button;
            relativeMouseX = newMouseX - getAbsoluteX();
            relativeMouseY = newMouseY - getAbsoluteY();
            return true;
        }
        return super.mousePressed(newMouseX, newMouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mouseInteraction && isInteracting && button == interactingButton) {
            isInteracting = false;
            interactingButton = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double newMouseX, double newMouseY) {
        super.mouseMoved(newMouseX, newMouseY);
        if (isHovered()) {
            relativeMouseX = newMouseX - getAbsoluteX();
            relativeMouseY = newMouseY - getAbsoluteY();
        }
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        updatePoints();

        var vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        var matrix = stack.lastMatrix();

        var r = (float) (lineColor >> 16 & 255) / 255.0F;
        var g = (float) (lineColor >> 8 & 255) / 255.0F;
        var b = (float) (lineColor & 255) / 255.0F;
        var a = (float) (lineColor >> 24 & 255) / 255.0F;
        if (a == 0 && (r != 0 || g != 0 || b != 0)) a = 1.0f;
        if (r == 0 && g == 0 && b == 0 && a == 0) {
            r = g = b = a = 1.0f;
        }
        var finalBaseAlpha = a * getAbsoluteAlpha();

        points.sort(Comparator.comparingDouble(p -> p.x));
        var connectionDistance = (float) Math.sqrt(connectionDistanceSq);

        for (var i = 0; i < points.size(); i++) {
            var p1 = points.get(i);
            for (var j = i + 1; j < points.size(); j++) {
                var p2 = points.get(j);

                if (p2.x - p1.x > connectionDistance) {
                    break;
                }

                var dx = p1.x - p2.x;
                var dy = p1.y - p2.y;
                var distanceSq = dx * dx + dy * dy;

                if (distanceSq < connectionDistanceSq) {
                    var dist = (float) Math.sqrt(distanceSq);
                    var lineAlpha = finalBaseAlpha * (1f - dist / connectionDistance);
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