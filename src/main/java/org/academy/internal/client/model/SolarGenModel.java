package org.academy.internal.client.model;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.academy.internal.client.definitions.SolarGenAnimation;
import org.academy.internal.client.renderer.blockentity.state.SolarGenRenderState;

public final class SolarGenModel extends Model<SolarGenRenderState> {
    private final ModelPart all;
    private final ModelPart bottom;
    private final ModelPart pole;
    private final ModelPart panel1;
    private final ModelPart panel2;
    private final KeyframeAnimation folding;
    private final KeyframeAnimation unfolding;

    public SolarGenModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        all = root.getChild("all");
        bottom = all.getChild("bottom");
        pole = bottom.getChild("pole");
        panel1 = all.getChild("panel1");
        panel2 = all.getChild("panel2");
        folding = SolarGenAnimation.FOLDING.bake(root);
        unfolding = SolarGenAnimation.UNFOLDING.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var bottom = all.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -8.0F, 12.0F, 6.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var pole = bottom.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(44, 22).addBox(0.0F, -7.0F, -1.0F, 1.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(44, 22).addBox(0.0F, -7.0F, -9.0F, 1.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(44, 22).addBox(-13.0F, -7.0F, -9.0F, 1.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(44, 22).addBox(-13.0F, -7.0F, -1.0F, 1.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 0.0F, 4.0F));

        var panel1 = all.addOrReplaceChild("panel1", CubeListBuilder.create().texOffs(0, 22).addBox(0.0F, -2.0F, -8.0F, 6.0F, 2.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, -6.0F, 0.0F));

        var panel2 = all.addOrReplaceChild("panel2", CubeListBuilder.create().texOffs(0, 40).addBox(-6.0F, -2.0F, -8.0F, 6.0F, 2.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, -6.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(SolarGenRenderState renderState) {
        super.setupAnim(renderState);
        folding.apply(renderState.foldingState, renderState.ageInTicks);
        unfolding.apply(renderState.unfoldingState, renderState.ageInTicks);
    }
}