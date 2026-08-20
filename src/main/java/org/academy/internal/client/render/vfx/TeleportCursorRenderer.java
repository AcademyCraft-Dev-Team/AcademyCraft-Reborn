package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the supplied GeckoLib teleport-cursor model in world space.
 *
 * <p>The project does not ship GeckoLib at runtime, so the five model elements,
 * their per-face UVs, bone pivots, and the two-second animation are represented
 * directly here using GeckoLib/Blockbench coordinates.</p>
 */
public final class TeleportCursorRenderer {
    private static final float MODEL_SCALE = 1.0f / 16.0f;
    private static final float TEXTURE_SIZE = 64.0f;
    private static final float ANIMATION_LENGTH = 2.0f;
    private static final float HALO_TRAVEL_END = 0.70833f;

    private static final Point ALL_ORIGIN = new Point(0, 0, 0);
    private static final Point LOWER_ORIGIN = new Point(0, 0, 0);
    private static final Point UPPER_ORIGIN = new Point(0, 31, 0);

    private static final Element BODY = new Element(
            new Point(-17, 0, 0), new Point(-7, 31, 10),
            new Point(0, 17, 17), new Point(0, -45, 0),
            faces(
                    face(Direction.NORTH, 0, 17, 14, 48),
                    face(Direction.EAST, 0, 17, 14, 48),
                    face(Direction.SOUTH, 0, 17, 14, 48),
                    face(Direction.WEST, 0, 17, 14, 48),
                    face(Direction.UP, 15, 17, 29, 31),
                    face(Direction.DOWN, 15, 17, 29, 31)
            ),
            true
    );
    private static final Element LOWER_HALO = plane(
            new Point(0, 1, 0),
            Direction.UP,
            new FaceUv(0, 0, 16, 16)
    );
    private static final Element UPPER_HALO = plane(
            new Point(0, 32, 0),
            Direction.DOWN,
            new FaceUv(0, 0, 16, 16)
    );
    private static final Element LOWER_RING = plane(
            new Point(0, 1, 0),
            Direction.UP,
            new FaceUv(17, 0, 33, 16)
    );
    private static final Element UPPER_RING = plane(
            new Point(0, 32, 0),
            Direction.DOWN,
            new FaceUv(17, 0, 33, 16)
    );

    private TeleportCursorRenderer() {
    }

    /**
     * Submits a cursor whose model origin is the target entity's feet.
     */
    public static void render(LevelRenderEvent event, Vec3 feetPosition, boolean validDestination) {
        if (feetPosition == null || !isFinite(feetPosition)) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;

        var camera = minecraft.gameRenderer.mainCamera().position();
        var playerPosition = player.getPosition(event.getPartialTick());
        var toPlayerX = playerPosition.x - feetPosition.x;
        var toPlayerZ = playerPosition.z - feetPosition.z;
        var facingRotation = playerFacingRotation(toPlayerX, toPlayerZ);
        var animationTime = ((player.level().getGameTime() + event.getPartialTick()) / 20.0f)
                % ANIMATION_LENGTH;
        var red = 1.0f;
        var green = validDestination ? 1.0f : 0.0f;
        var blue = validDestination ? 1.0f : 0.0f;

        event.submitPoseGeometry(Render.RenderTypes.TELEPORT_CURSOR, (pose, consumer) -> {
            var poseStack = new PoseStack();
            poseStack.last().set(pose);
            poseStack.translate(
                    feetPosition.x - camera.x,
                    feetPosition.y - camera.y,
                    feetPosition.z - camera.z
            );
            poseStack.mulPose(new Quaternionf().rotationY(facingRotation));
            renderModel(poseStack, consumer, animationTime, red, green, blue);
        });
    }

    private static void renderModel(PoseStack poseStack, VertexConsumer consumer, float time,
                                    float red, float green, float blue) {
        var haloProgress = easeInOutQuad(Math.clamp(time / HALO_TRAVEL_END, 0.0f, 1.0f));
        if (time <= HALO_TRAVEL_END) {
            renderBone(poseStack, consumer, LOWER_ORIGIN,
                    new Point(0, 31.0f * haloProgress, 0), 1.0f, LOWER_HALO,
                    red, green, blue);
            renderBone(poseStack, consumer, UPPER_ORIGIN,
                    new Point(0, -31.0f * haloProgress, 0), 1.0f, UPPER_HALO,
                    red, green, blue);
        }

        var ringScale = 1.1f - 0.1f * (float) Math.cos(Math.PI * 2.0 * time);
        renderBone(poseStack, consumer, LOWER_ORIGIN, Point.ZERO, ringScale, LOWER_RING,
                red, green, blue);
        renderBone(poseStack, consumer, UPPER_ORIGIN, Point.ZERO, ringScale, UPPER_RING,
                red, green, blue);
        renderElement(poseStack, consumer, ALL_ORIGIN, BODY, red, green, blue);
    }

    private static void renderBone(PoseStack poseStack, VertexConsumer consumer,
                                   Point boneOrigin, Point animatedPosition, float horizontalScale,
                                   Element element, float red, float green, float blue) {
        poseStack.pushPose();
        poseStack.translate(
                (boneOrigin.x + animatedPosition.x) * MODEL_SCALE,
                (boneOrigin.y + animatedPosition.y) * MODEL_SCALE,
                (boneOrigin.z + animatedPosition.z) * MODEL_SCALE
        );
        poseStack.scale(horizontalScale, 1.0f, horizontalScale);
        renderElement(poseStack, consumer, boneOrigin, element, red, green, blue);
        poseStack.popPose();
    }

    private static void renderElement(PoseStack poseStack, VertexConsumer consumer,
                                      Point boneOrigin, Element element,
                                      float red, float green, float blue) {
        poseStack.pushPose();
        poseStack.translate(
                (element.origin.x - boneOrigin.x) * MODEL_SCALE,
                (element.origin.y - boneOrigin.y) * MODEL_SCALE,
                (element.origin.z - boneOrigin.z) * MODEL_SCALE
        );
        poseStack.mulPose(new Quaternionf().rotationZYX(
                element.rotation.z * (float) (Math.PI / 180.0),
                element.rotation.y * (float) (Math.PI / 180.0),
                element.rotation.x * (float) (Math.PI / 180.0)
        ));

        var fromX = element.from.x - element.origin.x;
        var fromY = element.from.y - element.origin.y;
        var fromZ = element.from.z - element.origin.z;
        var toX = element.to.x - element.origin.x;
        var toY = element.to.y - element.origin.y;
        var toZ = element.to.z - element.origin.z;
        for (var entry : element.faces.entrySet()) {
            renderFace(poseStack, consumer, entry.getKey(), entry.getValue(),
                    fromX, fromY, fromZ, toX, toY, toZ,
                    element.inwardFacing, red, green, blue);
        }
        poseStack.popPose();
    }

    private static void renderFace(PoseStack poseStack, VertexConsumer consumer,
                                   Direction direction, FaceUv uv,
                                   float fromX, float fromY, float fromZ,
                                   float toX, float toY, float toZ,
                                   boolean inwardFacing,
                                   float red, float green, float blue) {
        switch (direction) {
            case DOWN -> quad(poseStack, consumer, uv,
                    toX, fromY, toZ, fromX, fromY, toZ,
                    fromX, fromY, fromZ, toX, fromY, fromZ,
                    inwardFacing, red, green, blue);
            case UP -> quad(poseStack, consumer, uv,
                    toX, toY, fromZ, fromX, toY, fromZ,
                    fromX, toY, toZ, toX, toY, toZ,
                    inwardFacing, red, green, blue);
            case WEST -> quad(poseStack, consumer, uv,
                    fromX, fromY, fromZ, fromX, fromY, toZ,
                    fromX, toY, toZ, fromX, toY, fromZ,
                    inwardFacing, red, green, blue);
            case NORTH -> quad(poseStack, consumer, uv,
                    toX, fromY, fromZ, fromX, fromY, fromZ,
                    fromX, toY, fromZ, toX, toY, fromZ,
                    inwardFacing, red, green, blue);
            case EAST -> quad(poseStack, consumer, uv,
                    toX, fromY, toZ, toX, fromY, fromZ,
                    toX, toY, fromZ, toX, toY, toZ,
                    inwardFacing, red, green, blue);
            case SOUTH -> quad(poseStack, consumer, uv,
                    fromX, fromY, toZ, toX, fromY, toZ,
                    toX, toY, toZ, fromX, toY, toZ,
                    inwardFacing, red, green, blue);
        }
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer, FaceUv uv,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             boolean reverseWinding,
                             float red, float green, float blue) {
        if (reverseWinding) {
            vertex(poseStack, consumer, x0, y0, z0, uv.u1, uv.v0, red, green, blue);
            vertex(poseStack, consumer, x3, y3, z3, uv.u1, uv.v1, red, green, blue);
            vertex(poseStack, consumer, x2, y2, z2, uv.u0, uv.v1, red, green, blue);
            vertex(poseStack, consumer, x1, y1, z1, uv.u0, uv.v0, red, green, blue);
            return;
        }
        vertex(poseStack, consumer, x0, y0, z0, uv.u1, uv.v0, red, green, blue);
        vertex(poseStack, consumer, x1, y1, z1, uv.u0, uv.v0, red, green, blue);
        vertex(poseStack, consumer, x2, y2, z2, uv.u0, uv.v1, red, green, blue);
        vertex(poseStack, consumer, x3, y3, z3, uv.u1, uv.v1, red, green, blue);
    }

    private static void vertex(PoseStack poseStack, VertexConsumer consumer,
                               float x, float y, float z,
                               float u, float v, float red, float green, float blue) {
        consumer.addVertex(
                        poseStack.last().pose(),
                        x * MODEL_SCALE,
                        y * MODEL_SCALE,
                        z * MODEL_SCALE
                )
                .setUv(u / TEXTURE_SIZE, v / TEXTURE_SIZE)
                .setColor(red, green, blue, 1.0f);
    }

    private static Element plane(Point origin, Direction visibleFace, FaceUv uv) {
        var y = origin.y - 1;
        return new Element(
                new Point(-8, y, -8), new Point(8, y, 8),
                origin, new Point(0, -45, 0),
                faces(face(visibleFace, uv)),
                false
        );
    }

    @SafeVarargs
    private static Map<Direction, FaceUv> faces(Map.Entry<Direction, FaceUv>... entries) {
        var result = new EnumMap<Direction, FaceUv>(Direction.class);
        for (var entry : entries) result.put(entry.getKey(), entry.getValue());
        return result;
    }

    private static Map.Entry<Direction, FaceUv> face(Direction direction,
                                                     float u0, float v0, float u1, float v1) {
        return face(direction, new FaceUv(u0, v0, u1, v1));
    }

    private static Map.Entry<Direction, FaceUv> face(Direction direction, FaceUv uv) {
        return Map.entry(direction, uv);
    }

    private static float easeInOutQuad(float value) {
        return value < 0.5f
                ? 2.0f * value * value
                : 1.0f - (float) Math.pow(-2.0f * value + 2.0f, 2.0) / 2.0f;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static float playerFacingRotation(double toPlayerX, double toPlayerZ) {
        if (toPlayerX * toPlayerX + toPlayerZ * toPlayerZ < 1.0e-8) return 0.0f;
        // The authored -45 degree element rotation puts the selected vertical edge on local +Z.
        return (float) Math.atan2(toPlayerX, toPlayerZ);
    }

    private record Element(Point from, Point to, Point origin, Point rotation,
                           Map<Direction, FaceUv> faces, boolean inwardFacing) {
    }

    private record FaceUv(float u0, float v0, float u1, float v1) {
    }

    private record Point(float x, float y, float z) {
        private static final Point ZERO = new Point(0, 0, 0);
    }
}
