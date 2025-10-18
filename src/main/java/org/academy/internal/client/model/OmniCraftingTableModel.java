package org.academy.internal.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.Resource;
import org.academy.internal.client.definitions.OmniCraftingTableAnimation;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.internal.client.renderer.blockentity.state.OmniCraftingTableRenderState;

import static net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION;

/**
 * @author MapleBadd
 */
@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class OmniCraftingTableModel extends Model<OmniCraftingTableRenderState> {
    private final ModelPart all;
    private final ModelPart main;
    private final ModelPart tabletop;
    private final ModelPart corners;
    private final ModelPart nwcorner;
    private final ModelPart necorner;
    private final ModelPart secorner;
    private final ModelPart swcorner;
    private final ModelPart roboarms;
    private final ModelPart arm1;
    private final ModelPart arm12;
    private final ModelPart arm13;
    private final ModelPart beam1_li;
    private final ModelPart arm2;
    private final ModelPart arm22;
    private final ModelPart arm23;
    private final ModelPart beam2_li;
    private final ModelPart arm3;
    private final ModelPart arm32;
    private final ModelPart arm33;
    private final ModelPart beam3_li;
    private final ModelPart arm4;
    private final ModelPart arm42;
    private final ModelPart arm43;
    private final ModelPart beam4_li;
    private final ModelPart shell;
    private final ModelPart fshell;
    private final ModelPart bshell;
    private final ModelPart lshell;
    private final ModelPart rshell;
    private final ModelPart effect;
    private final ModelPart inner_ef;
    private final ModelPart tebletop_ef;
    private final ModelPart production_li;
    private final ModelPart inner_li;
    private final KeyframeAnimation unfolding;
    private final KeyframeAnimation working;

    public OmniCraftingTableModel(ModelPart root) {
        super(root.getChild("all"), RenderType::entityCutoutNoCull);
        all = root.getChild("all");
        main = all.getChild("main");
        tabletop = main.getChild("tabletop");
        corners = main.getChild("corners");
        nwcorner = corners.getChild("nwcorner");
        necorner = corners.getChild("necorner");
        secorner = corners.getChild("secorner");
        swcorner = corners.getChild("swcorner");
        roboarms = main.getChild("roboarms");
        arm1 = roboarms.getChild("arm1");
        arm12 = arm1.getChild("arm12");
        arm13 = arm12.getChild("arm13");
        beam1_li = arm13.getChild("beam1_li");
        arm2 = roboarms.getChild("arm2");
        arm22 = arm2.getChild("arm22");
        arm23 = arm22.getChild("arm23");
        beam2_li = arm23.getChild("beam2_li");
        arm3 = roboarms.getChild("arm3");
        arm32 = arm3.getChild("arm32");
        arm33 = arm32.getChild("arm33");
        beam3_li = arm33.getChild("beam3_li");
        arm4 = roboarms.getChild("arm4");
        arm42 = arm4.getChild("arm42");
        arm43 = arm42.getChild("arm43");
        beam4_li = arm43.getChild("beam4_li");
        shell = main.getChild("shell");
        fshell = shell.getChild("fshell");
        bshell = shell.getChild("bshell");
        lshell = shell.getChild("lshell");
        rshell = shell.getChild("rshell");
        effect = all.getChild("effect");
        inner_ef = effect.getChild("inner_ef");
        tebletop_ef = effect.getChild("tebletop_ef");
        production_li = effect.getChild("production_li");
        inner_li = production_li.getChild("inner_li");
        unfolding = OmniCraftingTableAnimation.UNFOLDING.bake(root);
        working = OmniCraftingTableAnimation.WORKING.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(40, 46).addBox(-3.0F, -16.0F, -9.0F, 6.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(40, 57).addBox(-3.0F, -16.0F, 5.0F, 6.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(40, 68).addBox(-9.0F, -16.0F, -3.0F, 4.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(40, 81).addBox(5.0F, -16.0F, -3.0F, 4.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-8.0F, -3.0F, -8.0F, 16.0F, 3.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(17, 4).addBox(-9.0F, -4.0F, 5.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(17, 4).addBox(-9.0F, -4.0F, -9.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(18, 4).addBox(5.0F, -4.0F, -9.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(18, 4).addBox(5.0F, -4.0F, 5.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 54).addBox(-3.0F, -11.0F, -8.0F, 6.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(31, 35).addBox(3.0F, -11.0F, -3.0F, 5.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(31, 23).addBox(-8.0F, -11.0F, -3.0F, 5.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(-3.0F, -15.0F, -3.0F, 6.0F, 12.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var tabletop = main.addOrReplaceChild("tabletop", CubeListBuilder.create().texOffs(0, 22).addBox(-5.0F, -10.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, 0.0F));

        var corners = main.addOrReplaceChild("corners", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var nwcorner = corners.addOrReplaceChild("nwcorner", CubeListBuilder.create().texOffs(66, 33).addBox(2.0F, -15.0F, 7.0F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -1.0F, -15.0F));

        var necorner = corners.addOrReplaceChild("necorner", CubeListBuilder.create().texOffs(66, 0).addBox(-8.0F, -15.0F, -8.0F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, 0.0F));

        var secorner = corners.addOrReplaceChild("secorner", CubeListBuilder.create().texOffs(66, 22).addBox(-9.0F, -15.0F, 3.0F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -1.0F, 0.0F));

        var swcorner = corners.addOrReplaceChild("swcorner", CubeListBuilder.create().texOffs(66, 11).addBox(-12.0F, -15.0F, 3.0F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(15.0F, -1.0F, 0.0F));

        var roboarms = main.addOrReplaceChild("roboarms", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var arm1 = roboarms.addOrReplaceChild("arm1", CubeListBuilder.create().texOffs(20, 85).addBox(0.0F, 0.0F, -2.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -10.0F, -3.0F));

        var arm12 = arm1.addOrReplaceChild("arm12", CubeListBuilder.create().texOffs(20, 73).addBox(-2.0F, -7.0F, -2.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, -2.0F));

        var arm13 = arm12.addOrReplaceChild("arm13", CubeListBuilder.create().texOffs(20, 97).addBox(-2.0F, -2.0F, 0.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(32, 59).addBox(-1.5F, 2.5F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(32, 61).addBox(-1.5F, 3.5F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(31, 61).addBox(-2.0F, 3.5F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.0F, 0.0F));

        var beam1_li = arm13.addOrReplaceChild("beam1_li", CubeListBuilder.create().texOffs(121, 121).addBox(-1.0F, -7.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(120, 121).addBox(-1.5F, -7.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 5.5F, 1.0F));

        var arm2 = roboarms.addOrReplaceChild("arm2", CubeListBuilder.create().texOffs(0, 73).addBox(-2.0F, 0.0F, -2.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -10.0F, -3.0F));

        var arm22 = arm2.addOrReplaceChild("arm22", CubeListBuilder.create().texOffs(0, 85).addBox(0.0F, -7.0F, -2.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, -2.0F));

        var arm23 = arm22.addOrReplaceChild("arm23", CubeListBuilder.create().texOffs(32, 59).addBox(-0.5F, 2.5F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(32, 61).addBox(-0.5F, 3.5F, 1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(31, 61).addBox(-1.0F, 3.5F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 97).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -5.0F, 0.0F));

        var beam2_li = arm23.addOrReplaceChild("beam2_li", CubeListBuilder.create().texOffs(120, 121).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(121, 121).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 1.5F));

        var arm3 = roboarms.addOrReplaceChild("arm3", CubeListBuilder.create().texOffs(10, 73).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -10.0F, 3.0F));

        var arm32 = arm3.addOrReplaceChild("arm32", CubeListBuilder.create().texOffs(10, 85).addBox(0.0F, -7.0F, 0.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, 2.0F));

        var arm33 = arm32.addOrReplaceChild("arm33", CubeListBuilder.create().texOffs(10, 97).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(32, 59).addBox(-0.5F, 2.5F, -1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(32, 61).addBox(-0.5F, 3.5F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(31, 61).addBox(-1.0F, 3.5F, -1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -5.0F, 0.0F));

        var beam3_li = arm33.addOrReplaceChild("beam3_li", CubeListBuilder.create().texOffs(121, 121).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(120, 121).addBox(-1.5F, -1.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -0.5F, -1.0F));

        var arm4 = roboarms.addOrReplaceChild("arm4", CubeListBuilder.create().texOffs(30, 73).addBox(0.0F, 0.0F, 0.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -10.0F, 3.0F));

        var arm42 = arm4.addOrReplaceChild("arm42", CubeListBuilder.create().texOffs(30, 85).addBox(-2.0F, -7.0F, 0.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, 2.0F));

        var arm43 = arm42.addOrReplaceChild("arm43", CubeListBuilder.create().texOffs(30, 97).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(32, 59).addBox(-0.5F, 2.5F, -1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(32, 61).addBox(-0.5F, 3.5F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(31, 61).addBox(-1.0F, 3.5F, -1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.0F, -5.0F, 0.0F));

        var beam4_li = arm43.addOrReplaceChild("beam4_li", CubeListBuilder.create().texOffs(120, 121).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(121, 121).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, -0.5F));

        var shell = main.addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var fshell = shell.addOrReplaceChild("fshell", CubeListBuilder.create().texOffs(78, 63).addBox(-3.0F, -5.0F, -5.0F, 6.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.0F, -2.0F));

        var bshell = shell.addOrReplaceChild("bshell", CubeListBuilder.create().texOffs(62, 63).addBox(-3.0F, -10.0F, 6.0F, 6.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var lshell = shell.addOrReplaceChild("lshell", CubeListBuilder.create().texOffs(77, 47).addBox(6.0F, -10.0F, -3.0F, 1.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var rshell = shell.addOrReplaceChild("rshell", CubeListBuilder.create().texOffs(63, 47).addBox(-7.0F, -10.0F, -3.0F, 1.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        var effect = all.addOrReplaceChild("effect", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        var inner_ef = effect.addOrReplaceChild("inner_ef", CubeListBuilder.create().texOffs(3, 53).addBox(8.01F, -14.0F, -0.9F, 0.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(3, 57).addBox(3.0F, -14.0F, -1.91F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(3, 57).addBox(3.0F, -14.0F, 4.11F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(3, 53).addBox(1.99F, -14.0F, -0.9F, 0.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 4.0F, -1.1F));

        var tebletop_ef = effect.addOrReplaceChild("tebletop_ef", CubeListBuilder.create().texOffs(64, 83).addBox(-5.0F, -14.0F, 4.0F, 10.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(64, 83).addBox(-5.0F, -14.0F, 1.0F, 10.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(64, 83).addBox(-5.0F, -14.0F, -2.0F, 10.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(64, 83).addBox(-5.0F, -14.0F, -5.0F, 10.0F, 0.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(57, 76).addBox(-5.0F, -14.0F, -4.0F, 1.0F, 0.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(57, 76).addBox(-2.0F, -14.0F, -4.0F, 1.0F, 0.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(57, 76).addBox(1.0F, -14.0F, -4.0F, 1.0F, 0.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(57, 76).addBox(4.0F, -14.0F, -4.0F, 1.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.9F, 0.0F));

        var production_li = effect.addOrReplaceChild("production_li", CubeListBuilder.create().texOffs(0, 122).addBox(-1.5F, -23.0F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.0F, 0.0F));

        var inner_li = production_li.addOrReplaceChild("inner_li", CubeListBuilder.create().texOffs(14, 122).addBox(-2.0F, 3.0F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -23.0F, -0.5F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(OmniCraftingTableRenderState renderState) {
        super.setupAnim(renderState);
        unfolding.apply(renderState.unfoldingState, renderState.ageInTicks);
    }

    public void render(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay) {
        nodeCollector.submitModelPart(main, poseStack, renderType(Resource.Textures.OMNI_CRAFTING_TABLE), packedLight, packedOverlay, null);
        var rt = IrisCompat.isShaderPackInUse() ? RenderType.entitySolid(END_PORTAL_LOCATION) : RenderType.endGateway();
        nodeCollector.submitModelPart(effect, poseStack, rt, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }
}