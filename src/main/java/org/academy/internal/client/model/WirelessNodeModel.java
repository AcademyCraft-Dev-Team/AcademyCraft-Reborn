package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.animation.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.level.block.Blocks;
import org.academy.api.client.Resource;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.internal.client.renderer.blockentity.state.WirelessNodeRenderState;

import static net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION;

/**
 * @author MapleBadd
 */
public class WirelessNodeModel extends Model<WirelessNodeRenderState> {
    private final ModelPart all;
    private final ModelPart core_li;
    private final ModelPart base2;
    private final ModelPart innner_ef;
    private final KeyframeAnimation quarter;
    private final KeyframeAnimation half;
    private final KeyframeAnimation quarters;
    private final KeyframeAnimation full;

    public WirelessNodeModel(ModelPart root) {
        super(root.getChild("all"), RenderType::entityTranslucent);
        this.all = root.getChild("all");
        this.core_li = this.all.getChild("core_li");
        this.base2 = this.all.getChild("base2");
        this.innner_ef = this.all.getChild("innner_ef");
        quarter = QUARTER.bake(root);
        half = HALF.bake(root);
        quarters = QUARTERS.bake(root);
        full = FULL.bake(root);
    }

    public static final AnimationDefinition EMPTY = AnimationDefinition.Builder.withLength(0.0F).looping()
            .build();

    public static final AnimationDefinition QUARTER = AnimationDefinition.Builder.withLength(4.0F).looping()
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

    public static final AnimationDefinition HALF = AnimationDefinition.Builder.withLength(2.5F).looping()
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

    public static final AnimationDefinition QUARTERS = AnimationDefinition.Builder.withLength(1.5F).looping()
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

    public static final AnimationDefinition FULL = AnimationDefinition.Builder.withLength(1.0F).looping()
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
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var core_li = all.addOrReplaceChild("core_li", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var cube_r1 = core_li.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, -0.4656F, 0.422F, 0.6799F));

        var base2 = all.addOrReplaceChild("base2", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var innner_ef = all.addOrReplaceChild("innner_ef", CubeListBuilder.create().texOffs(2, 60).addBox(-10.0F, -9.0F, 7.9F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
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

        var cube_r2 = innner_ef.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(2, 60).addBox(3.0F, -2.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
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

        var cube_r3 = innner_ef.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(2, 60).addBox(3.0F, -2.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
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
    public void setupAnim(WirelessNodeRenderState renderState) {
        super.setupAnim(renderState);
        if (renderState.connectedUsersCount == 0) {
            resetPose();
        } else {
            int ratio100 = renderState.connectedUsersCount * 100 / renderState.maxConnectedUsers;
            int level = (ratio100 - 1) / 25 + 1;
            int progress = Math.min(level, 4);
            switch (progress) {
                case 1: {
                    quarter.apply(renderState.coreState, renderState.ageInTicks);
                    break;
                }
                case 2: {
                    half.apply(renderState.coreState, renderState.ageInTicks);
                    break;
                }
                case 3: {
                    quarters.apply(renderState.coreState, renderState.ageInTicks);
                    break;
                }
                case 4: {
                    full.apply(renderState.coreState, renderState.ageInTicks);
                    break;
                }
            }
        }
    }

    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, int packedOverlay) {
        submitNodeCollector.submitModelPart(base2, poseStack, renderType(Resource.Textures.WIRELESS_NODE_MODEL), packedLight, packedOverlay, null);
        submitNodeCollector.submitModelPart(core_li, poseStack, renderType(Resource.Textures.WIRELESS_NODE_MODEL), packedLight, packedOverlay, null);

        RenderType type;
        if (!IrisCompat.isShaderPackInUse()) {
            type = RenderType.endGateway();
        } else {
            var blockStateIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();

            if (blockStateIds != null) {
                var intId = blockStateIds.getOrDefault(Blocks.END_PORTAL.defaultBlockState(), -1);
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(intId);
            }

            type = RenderType.entitySolid(END_PORTAL_LOCATION);
        }

        submitNodeCollector.submitModelPart(innner_ef, poseStack, type, packedLight, packedOverlay, null);
    }
}