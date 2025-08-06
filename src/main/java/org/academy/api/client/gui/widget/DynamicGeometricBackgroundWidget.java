package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.Tickable;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Renders an interactive background of moving points and connecting lines.
 * The particle simulation is updated on the client tick for stable performance.
 * Note: For performance, this widget sorts its points list on every render frame
 * to optimize line drawing, which can be intensive with a very high number of points.
 */
public class DynamicGeometricBackgroundWidget extends AbstractWidget implements Tickable {
    private static final class Point {
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
        this.initPoints();
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

    @Override
    public void tick() {
        for (var p : points) {
            p.x += p.vx;
            p.y += p.vy;

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

            if (mouseInteraction && this.interactingButton != -1) {
                var dx = p.x - (float) this.relativeMouseX;
                var dy = p.y - (float) this.relativeMouseY;
                var distSq = dx * dx + dy * dy;

                if (distSq < this.mouseInteractionRadiusSq && distSq > 0.001f) {
                    var dist = (float) Math.sqrt(distSq);
                    var forceMagnitude = (1f - dist / (float) Math.sqrt(this.mouseInteractionRadiusSq)) * this.pointSpeed * 0.5f;

                    if (this.interactingButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        forceMagnitude *= -1;
                    }

                    p.vx -= (dx / dist) * forceMagnitude;
                    p.vy -= (dy / dist) * forceMagnitude;

                    var speedSq = p.vx * p.vx + p.vy * p.vy;
                    var maxSpeed = this.pointSpeed * 2.0f;
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
    protected void onMousePressed(@NotNull MouseEvent event) {
        if (this.mouseInteraction && isMouseOver(event.getX(), event.getY()) && (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            this.interactingButton = event.getButton();
            this.relativeMouseX = event.getX() - getAbsoluteX();
            this.relativeMouseY = event.getY() - getAbsoluteY();
            event.consume();
        }
    }

    @Override
    protected void onMouseReleased(@NotNull MouseEvent event) {
        if (this.mouseInteraction && event.getButton() == this.interactingButton) {
            this.interactingButton = -1;
            event.consume();
        }
    }

    @Override
    protected void onMouseDragged(@NotNull MouseEvent event) {
        if (this.mouseInteraction && event.getButton() == this.interactingButton) {
            this.relativeMouseX = event.getX() - getAbsoluteX();
            this.relativeMouseY = event.getY() - getAbsoluteY();
            event.consume();
        }
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        stack.pushPose();
        stack.translate(this.getX(), this.getY(), this.getZ());

        var vertexConsumer = bufferSource.getBuffer(RenderTypes.LINES.apply(OptionalDouble.empty()));
        var matrix = stack.lastMatrix();

        var r = (float) (this.lineColor >> 16 & 255) / 255.0F;
        var g = (float) (this.lineColor >> 8 & 255) / 255.0F;
        var b = (float) (this.lineColor & 255) / 255.0F;
        var a = (float) (this.lineColor >> 24 & 255) / 255.0F;
        if (a == 0 && (r != 0 || g != 0 || b != 0)) a = 1.0f;
        if (r == 0 && g == 0 && b == 0 && a == 0) r = g = b = a = 1.0f;
        var finalBaseAlpha = a * getAbsoluteAlpha();

        this.points.sort(Comparator.comparingDouble(p -> p.x));
        var connectionDistance = (float) Math.sqrt(this.connectionDistanceSq);

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

                if (distanceSq < this.connectionDistanceSq) {
                    var dist = (float) Math.sqrt(distanceSq);
                    var lineAlpha = finalBaseAlpha * (1f - dist / connectionDistance);
                    lineAlpha = Math.max(0, Math.min(lineAlpha, 1.0f));

                    vertexConsumer.addVertex(matrix, p1.x, p1.y, 0)
                            .setColor(r, g, b, lineAlpha);
                    vertexConsumer.addVertex(matrix, p2.x, p2.y, 0)
                            .setColor(r, g, b, lineAlpha);
                }
            }
        }
        stack.popPose();
    }
}