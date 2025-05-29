package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION;

@SuppressWarnings("DuplicatedCode")
public class WirelessNodeModel extends HierarchicalModel<Entity> {
    private final ModelPart all;
    private final ModelPart core_li;
    private final ModelPart base2;
    private final ModelPart innner_ef;

    public WirelessNodeModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.all = root.getChild("all");
        this.core_li = all.getChild("core_li");
        this.base2 = all.getChild("base2");
        this.innner_ef = all.getChild("innner_ef");
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int i, int i1, float v, float v1, float v2, float v3) {
    }

    @Override
    public @NotNull ModelPart root() {
        return all;
    }

    public static final AnimationDefinition empty = AnimationDefinition.Builder.withLength(0.0F).looping()
            .build();

    public static final AnimationDefinition quarter = AnimationDefinition.Builder.withLength(4.0F).looping()
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, -180.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(4.0F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(3.0F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(4.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .build();

    public static final AnimationDefinition half = AnimationDefinition.Builder.withLength(2.5F).looping()
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.degreeVec(0.0F, -180.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.5F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.625F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.875F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(2.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .build();

    public static final AnimationDefinition quarters = AnimationDefinition.Builder.withLength(1.5F).looping()
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, -180.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.375F, KeyframeAnimations.posVec(0.0F, -0.67F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.125F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .build();

    public static final AnimationDefinition full = AnimationDefinition.Builder.withLength(1.0F).looping()
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, -360.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("core_li", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .build();

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition core_li = all.addOrReplaceChild("core_li", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        core_li.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, -0.4656F, 0.422F, 0.6799F));

        all.addOrReplaceChild("base2", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition innner_ef = all.addOrReplaceChild("innner_ef", CubeListBuilder.create().texOffs(2, 60).addBox(-10.0F, -9.0F, 7.9F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-11.0F, -10.0F, 7.9F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 56).addBox(-15.0F, -12.0F, 7.9F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-3.0F, -12.0F, 7.9F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-5.0F, -9.0F, 7.9F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-8.0F, -11.0F, 7.9F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-9.0F, -12.0F, 7.9F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-8.0F, -6.0F, 7.9F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-9.0F, -5.0F, 7.9F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-15.0F, -4.0F, 7.9F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-15.0F, -16.0F, 7.9F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-4.0F, -10.0F, 7.9F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-10.0F, -9.0F, -7.9F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-11.0F, -10.0F, -7.9F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 56).addBox(-15.0F, -12.0F, -7.9F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-3.0F, -12.0F, -7.9F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-5.0F, -9.0F, -7.9F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-8.0F, -11.0F, -7.9F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-9.0F, -12.0F, -7.9F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-8.0F, -6.0F, -7.9F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-9.0F, -5.0F, -7.9F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-15.0F, -4.0F, -7.9F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-15.0F, -16.0F, -7.9F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-4.0F, -10.0F, -7.9F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 52).addBox(-4.0F, -15.9F, -5.0F, 1.0F, 0.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 52).addBox(-4.0F, -15.9F, 1.0F, 1.0F, 0.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(-3, 61).addBox(-5.0F, -15.9F, -5.0F, 1.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(-3, 61).addBox(-5.0F, -15.9F, 2.0F, 1.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(-3, 61).addBox(-10.0F, -15.9F, -5.0F, 1.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(-3, 61).addBox(-10.0F, -15.9F, 2.0F, 1.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 52).addBox(-11.0F, -15.9F, -5.0F, 1.0F, 0.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(-16, 48).addBox(-15.0F, -15.9F, -8.0F, 4.0F, 0.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(-16, 48).addBox(-3.0F, -15.9F, -8.0F, 4.0F, 0.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(0, 52).addBox(-11.0F, -15.9F, 1.0F, 1.0F, 0.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(-2, 62).addBox(-6.0F, -15.9F, -5.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(-2, 62).addBox(-6.0F, -15.9F, 3.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(-2, 62).addBox(-9.0F, -15.9F, -5.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(-2, 62).addBox(-9.0F, -15.9F, 3.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(-1, 63).addBox(-8.0F, -15.9F, 4.0F, 2.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(-1, 63).addBox(-8.0F, -15.9F, -5.0F, 2.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(-3, 61).addBox(-11.0F, -15.9F, -8.0F, 8.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(-3, 61).addBox(-11.0F, -15.9F, 5.0F, 8.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(-16, 48).addBox(-15.0F, -0.1F, -8.0F, 16.0F, 0.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(7.0F, 0.0F, 0.0F));

        innner_ef.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(2, 60).addBox(3.0F, -2.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-8.0F, -8.0F, 0.0F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-8.0F, 4.0F, 0.0F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-2.0F, 3.0F, 0.0F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-1.0F, 2.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-2.0F, -4.0F, 0.0F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-1.0F, -3.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(2.0F, -1.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(4.0F, -4.0F, 0.0F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 56).addBox(-8.0F, -4.0F, 0.0F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-4.0F, -2.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-3.0F, -1.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.9F, -8.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        innner_ef.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(2, 60).addBox(3.0F, -2.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-8.0F, -8.0F, 0.0F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(-8.0F, 4.0F, 0.0F, 16.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-2.0F, 3.0F, 0.0F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-1.0F, 2.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(1, 56).addBox(-2.0F, -4.0F, 0.0F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(-1.0F, -3.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(2.0F, -1.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 56).addBox(4.0F, -4.0F, 0.0F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 56).addBox(-8.0F, -4.0F, 0.0F, 4.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-4.0F, -2.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(2, 60).addBox(-3.0F, -1.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-14.9F, -8.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    public void setupAnim(@NotNull WirelessNodeBlockEntity wirelessNodeBlockEntity, float partialTick) {
        core_li.resetPose();

        if (wirelessNodeBlockEntity.connectedUsersCount == 0) {
            animate(wirelessNodeBlockEntity.coreState, empty, wirelessNodeBlockEntity.ticks + partialTick);
        } else {
            int ratio100 = wirelessNodeBlockEntity.connectedUsersCount * 100 / wirelessNodeBlockEntity.maxConnectedUsers;
            int level = (ratio100 - 1) / 25 + 1;
            int progress = Math.min(level, 4);
            switch (progress) {
                case 1: {
                    animate(wirelessNodeBlockEntity.coreState, quarter, wirelessNodeBlockEntity.ticks + partialTick);
                    break;
                }
                case 2: {
                    animate(wirelessNodeBlockEntity.coreState, half, wirelessNodeBlockEntity.ticks + partialTick);
                    break;
                }
                case 3: {
                    animate(wirelessNodeBlockEntity.coreState, quarters, wirelessNodeBlockEntity.ticks + partialTick);
                    break;
                }
                case 4: {
                    animate(wirelessNodeBlockEntity.coreState, full, wirelessNodeBlockEntity.ticks + partialTick);
                    break;
                }
            }
        }
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType.apply(TextureResources.TEXTURE_WIRELESS_NODE_MODEL));
        base2.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        core_li.render(poseStack, vertexConsumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        VertexConsumer effect;
        if (!RenderUtil.IS_SHADER_PACK_IN_USE.get()) {
            effect = bufferSource.getBuffer(RenderType.endGateway());
        } else {
            effect = bufferSource.getBuffer(RenderType.entitySolid(END_PORTAL_LOCATION));
        }
        innner_ef.render(poseStack, effect, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }
}