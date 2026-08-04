package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.resources.R;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import static org.academy.AcademyCraft.academy;

public final class DarkmatterSixWingsEffectRenderer implements EffectRenderer {
    public static final ContextKey<Boolean> CONTEXT_KEY =
            new ContextKey<>(academy("darkmatter_six_wings"));
    public static final DarkmatterSixWingsEffectRenderer INSTANCE =
            new DarkmatterSixWingsEffectRenderer();
    private static final Matrix4f BASE_MATRIX = new Matrix4f().translate(0.0f, -0.20f, 0.10f);
    private static final float BASE_SCALE = 0.84f;
    private static final AvianWingsModel[] MODELS = {
            new AvianWingsModel(AvianWingsModel.createBodyLayer().bakeRoot()),
            new AvianWingsModel(AvianWingsModel.createBodyLayer().bakeRoot()),
            new AvianWingsModel(AvianWingsModel.createBodyLayer().bakeRoot())
    };

    private DarkmatterSixWingsEffectRenderer() {
    }

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (!state.getRenderDataOrDefault(CONTEXT_KEY, false)) return;
        poseStack.pushPose();
        poseStack.mulPose(BASE_MATRIX);
        poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);
        renderPair(poseStack, collector, packedLight, state, MODELS[0],
                0.92f, 0.0f, -58.0f);
        renderPair(poseStack, collector, packedLight, state, MODELS[1],
                1.10f, 0.0f, 0.0f);
        renderPair(poseStack, collector, packedLight, state, MODELS[2],
                0.92f, 0.0f, 58.0f);
        poseStack.popPose();
    }

    private static void renderPair(PoseStack poseStack, SubmitNodeCollector collector,
                                   int packedLight, AvatarRenderState state,
                                   AvianWingsModel model, float scale,
                                   float xRotOffset, float zRotOffset) {
        poseStack.pushPose();
        if (zRotOffset == -58.0f) poseStack.translate(-0.30f, 0.25f, 0.0f);
        if (zRotOffset == 58.0f) poseStack.translate(0.30f, 0.25f, 0.0f);
        poseStack.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(zRotOffset))
                .rotateX((float) Math.toRadians(xRotOffset)));
        poseStack.scale(scale, scale, scale);
        collector.submitModel(model, state, poseStack,
                R.textures.darkmatter_six_wings_effect,
                packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
        poseStack.popPose();
    }

    private static final class AvianWingsModel extends Model<AvatarRenderState> {
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
            coracoidLeft = root.getChild("coracoidLeft");
            humerusLeft = coracoidLeft.getChild("humerusLeft");
            ulnaLeft = humerusLeft.getChild("ulnaLeft");
            carpalsLeft = ulnaLeft.getChild("carpalsLeft");
            coracoidRight = root.getChild("coracoidRight");
            humerusRight = coracoidRight.getChild("humerusRight");
            ulnaRight = humerusRight.getChild("ulnaRight");
            carpalsRight = ulnaRight.getChild("carpalsRight");
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state);
            var s = Mth.sin(state.ageInTicks * 0.05f);
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
            var x = (float) Math.toRadians(xDeg);
            var y = (float) Math.toRadians(yDeg);
            var z = (float) Math.toRadians(zDeg);
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
