package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public final class DarkmatterSixWingsVfx implements Vfx {
    static final int VERTEX_STRIDE = 5 * Float.BYTES;
    private static final int INITIAL_VERTEX_CAPACITY = 4096;
    private static final Matrix4f BASE_MATRIX = new Matrix4f().translate(0.0f, -0.20f, 0.10f);
    private static final float BASE_SCALE = 0.84f;

    private ByteBuffer vertexData = BufferUtils.createByteBuffer(INITIAL_VERTEX_CAPACITY * VERTEX_STRIDE);

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var roots = WingAvatarRegistry.entries();
        if (roots.isEmpty()) return;

        for (var entry : roots.entrySet()) {
            var entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player)) continue;
            if (!player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get())) continue;

            var age = player.tickCount + ctx.partialTick();
            var model = new AvianWingsModel(AvianWingsModel.createBodyLayer().bakeRoot());

            var poseStack = new PoseStack();
            var camera = ctx.camera().pos();
            // Avatar render roots are camera-relative. Prepend the camera translation to recover
            // world space; post-multiplying would rotate/scale the camera vector with the model.
            var root = new Matrix4f()
                    .translation(camera.x, camera.y, camera.z)
                    .mul(entry.getValue());
            poseStack.mulPose(root);
            poseStack.mulPose(BASE_MATRIX);
            poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);

            vertexData.clear();
            ensureCapacity(3072);
            bakeWingPair(model, poseStack, vertexData, age, 0.92f, -58.0f);
            bakeWingPair(model, poseStack, vertexData, age, 1.10f, 0.0f);
            bakeWingPair(model, poseStack, vertexData, age, 0.92f, 58.0f);
            vertexData.flip();
            if (vertexData.hasRemaining()) {
                // Each submitted item must own its bytes. A slice would share this scratch buffer,
                // so sampling the next avatar (or frame) could rewrite vertices still queued for
                // rendering and produce apparent texture/geometry flicker.
                var vertices = BufferUtils.createByteBuffer(vertexData.remaining())
                        .order(ByteOrder.nativeOrder());
                vertices.put(vertexData.duplicate());
                vertices.flip();
                sink.push(new DarkmatterSixWingsData(
                        entry.getValue(),
                        vertices,
                        vertices.remaining() / VERTEX_STRIDE
                ));
            }
        }
    }

    private void ensureCapacity(int requiredVertices) {
        var requiredBytes = (long) requiredVertices * VERTEX_STRIDE;
        if (vertexData.capacity() >= requiredBytes) return;
        var newCapacity = Math.max(requiredVertices,
                (vertexData.capacity() / VERTEX_STRIDE) * 2);
        vertexData = BufferUtils.createByteBuffer(Math.toIntExact((long) newCapacity * VERTEX_STRIDE));
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private static void bakeWingPair(AvianWingsModel model, PoseStack poseStack,
                                     ByteBuffer target, float age, float scale,
                                     float zRotationDegrees) {
        poseStack.pushPose();
        if (zRotationDegrees < 0.0f) {
            poseStack.translate(-0.30f, 0.25f, 0.0f);
        } else if (zRotationDegrees > 0.0f) {
            poseStack.translate(0.30f, 0.25f, 0.0f);
        }
        poseStack.mulPose(new Quaternionf().rotateZ(zRotationDegrees * Mth.DEG_TO_RAD));
        poseStack.scale(scale, scale, scale);
        model.setupAnim(age);
        bakeMesh(model.root, poseStack, target);
        poseStack.popPose();
    }

    private static void bakeMesh(ModelPart root, PoseStack poseStack, ByteBuffer target) {
        root.visit(poseStack, (pose, path, cubeIndex, cube) -> {
            var matrix = pose.pose();
            for (var polygon : cube.polygons) {
                var vertices = polygon.vertices();
                if (vertices.length < 3) continue;
                var v0 = transform(vertices[0], matrix);
                var v1 = transform(vertices[1], matrix);
                var v2 = transform(vertices[2], matrix);
                putVertex(target, v0);
                putVertex(target, v1);
                putVertex(target, v2);
                if (vertices.length < 4) continue;
                var v3 = transform(vertices[3], matrix);
                putVertex(target, v0);
                putVertex(target, v2);
                putVertex(target, v3);
            }
        });
    }

    private static void putVertex(ByteBuffer target, float[] vertex) {
        target.putFloat(vertex[0]);
        target.putFloat(vertex[1]);
        target.putFloat(vertex[2]);
        target.putFloat(vertex[3]);
        target.putFloat(vertex[4]);
    }

    private static float[] transform(ModelPart.Vertex vertex, Matrix4f matrix) {
        // worldX/Y/Z already apply ModelPart.Vertex.SCALE_FACTOR (1 / 16). Applying it a second
        // time collapses each textured face to 1 / 256 model scale, leaving only scattered texels.
        var position = new Vector3f(
                vertex.worldX(),
                vertex.worldY(),
                vertex.worldZ()
        ).mulPosition(matrix);
        return new float[]{
                position.x,
                position.y,
                position.z,
                vertex.u(),
                vertex.v()
        };
    }

    private static final class AvianWingsModel extends Model<AvatarRenderState> {
        private final ModelPart root;
        private final ModelPart coracoidLeft;
        private final ModelPart humerusLeft;
        private final ModelPart ulnaLeft;
        private final ModelPart carpalsLeft;
        private final ModelPart coracoidRight;
        private final ModelPart humerusRight;
        private final ModelPart ulnaRight;
        private final ModelPart carpalsRight;

        private AvianWingsModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.root = root;
            coracoidLeft = root.getChild("coracoidLeft");
            humerusLeft = coracoidLeft.getChild("humerusLeft");
            ulnaLeft = humerusLeft.getChild("ulnaLeft");
            carpalsLeft = ulnaLeft.getChild("carpalsLeft");
            coracoidRight = root.getChild("coracoidRight");
            humerusRight = coracoidRight.getChild("humerusRight");
            ulnaRight = humerusRight.getChild("ulnaRight");
            carpalsRight = ulnaRight.getChild("carpalsRight");
        }

        private void setupAnim(float ageInTicks) {
            var s = Mth.sin(ageInTicks * 0.05f);
            setupSegment(0.0f, -23.5f + (s * 5.0f - 14.0f) * 0.5f, -16.0f,
                    coracoidLeft, coracoidRight);
            setupSegment(0.0f, 13.0f, 29.0f, humerusLeft, humerusRight);
            setupSegment(0.0f, 12.0f + (s * 5.0f - 14.0f) * 0.5f, -28.0f,
                    ulnaLeft, ulnaRight);
            setupSegment(0.0f, 4.0f + (s * 5.0f - 14.0f), 18.3f,
                    carpalsLeft, carpalsRight);
        }

        private static void setupSegment(float xDeg, float yDeg, float zDeg,
                                         ModelPart left, ModelPart right) {
            var x = xDeg * Mth.DEG_TO_RAD;
            var y = yDeg * Mth.DEG_TO_RAD;
            var z = zDeg * Mth.DEG_TO_RAD;
            left.setRotation(x, y, z);
            right.setRotation(x, -y, -z);
        }

        private static LayerDefinition createBodyLayer() {
            var mesh = new MeshDefinition();
            var root = mesh.getRoot();
            var deformation = new CubeDeformation(0.0f);
            var coracoidLeft = root.addOrReplaceChild("coracoidLeft",
                    CubeListBuilder.create().texOffs(0, 28)
                            .addBox(0, -1.5f, -1.5f, 5, 3, 3, deformation),
                    PartPose.offset(1.5f, 5.5f, 2.5f));
            var coracoidRight = root.addOrReplaceChild("coracoidRight",
                    CubeListBuilder.create().texOffs(0, 34)
                            .addBox(-5, -1.5f, -1.5f, 5, 3, 3, deformation),
                    PartPose.offset(-1.5f, 5.5f, 2.5f));
            var humerusLeft = coracoidLeft.addOrReplaceChild("humerusLeft",
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-0.1f, -1.1f, -2, 7, 3, 4, deformation),
                    PartPose.offset(4.7f, -0.6f, 0.1f));
            var humerusRight = coracoidRight.addOrReplaceChild("humerusRight",
                    CubeListBuilder.create().texOffs(0, 7)
                            .addBox(-6.9f, -1.1f, -2, 7, 3, 4, deformation),
                    PartPose.offset(-4.7f, -0.6f, 0.1f));
            var ulnaLeft = humerusLeft.addOrReplaceChild("ulnaLeft",
                    CubeListBuilder.create().texOffs(22, 0)
                            .addBox(0, -1.5f, -1.5f, 9, 3, 3, deformation),
                    PartPose.offset(6.5f, 0.2f, 0.1f));
            var ulnaRight = humerusRight.addOrReplaceChild("ulnaRight",
                    CubeListBuilder.create().texOffs(22, 6)
                            .addBox(-9, -1.5f, -1.5f, 9, 3, 3, deformation),
                    PartPose.offset(-6.5f, 0.2f, 0.1f));
            var carpalsLeft = ulnaLeft.addOrReplaceChild("carpalsLeft",
                    CubeListBuilder.create().texOffs(22, 0)
                            .addBox(0, -1, -1, 5, 2, 2, deformation),
                    PartPose.offset(8.5f, 0, 0));
            var carpalsRight = ulnaRight.addOrReplaceChild("carpalsRight",
                    CubeListBuilder.create().texOffs(22, 0)
                            .addBox(-5, -1, -1, 5, 2, 2, deformation),
                    PartPose.offset(-8.5f, 0, 0));

            addFeathers(coracoidLeft, "feathersCoracoidLeft", 6, 40,
                    0, 0, -1, 6, 8, 0.2f, 0.4f, 0, 1, deformation);
            addFeathers(coracoidRight, "feathersCoracoidRight", 0, 40,
                    -6, 0, -1, 6, 8, 0.2f, -0.4f, 0, 1, deformation);
            addFeathers(humerusLeft, "feathersTertiaryLeft", 10, 14,
                    0, 0, -0.5f, 10, 14, 0.2f, 0, 1.5f, 1, deformation);
            addFeathers(humerusRight, "feathersTertiaryRight", 0, 14,
                    -10, 0, -0.5f, 10, 14, 0.2f, 0, 1.5f, 1, deformation);
            addFeathers(ulnaLeft, "feathersSecondaryLeft", 31, 14,
                    -2, 0, -0.5f, 11, 12, 0.2f, 0, 1, 0, deformation);
            addFeathers(ulnaRight, "feathersSecondaryRight", 20, 14,
                    -9, 0, -0.5f, 11, 12, 0.2f, 0, 1, 0, deformation);
            addFeathers(carpalsLeft, "feathersPrimaryLeft", 53, 14,
                    0, -2.1f, -0.5f, 11, 11, 0.2f, 0, 0, 0, deformation);
            addFeathers(carpalsRight, "feathersPrimaryRight", 42, 14,
                    -11, -2.1f, -0.5f, 11, 11, 0.2f, 0, 0, 0, deformation);
            return LayerDefinition.create(mesh, 64, 64);
        }

        private static void addFeathers(PartDefinition parent, String name, int u, int v,
                                        float x, float y, float z, float width, float height,
                                        float depth, float offsetX, float offsetY, float offsetZ,
                                        CubeDeformation deformation) {
            parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                            .addBox(x, y, z, width, height, depth, deformation),
                    PartPose.offset(offsetX, offsetY, offsetZ));
        }
    }
}
