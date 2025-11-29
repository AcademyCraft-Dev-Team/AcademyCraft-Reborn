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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.academy.api.client.Resource;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.internal.client.definitions.OmniCraftingTableAnimation;
import org.academy.internal.client.renderer.blockentity.state.OmniCraftingTableRenderState;

import static net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.END_PORTAL_LOCATION;

/**
 * @author MapleBadd
 */
@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class OmniCraftingTableModel extends Model<OmniCraftingTableRenderState> {
    private final ModelPart all;
    private final ModelPart craftinggrid_end;
    private final ModelPart _fluid;
    private final ModelPart main;
    private final ModelPart display;
    private final ModelPart printer;
    private final ModelPart base;
    private final ModelPart arm1;
    private final ModelPart arm2;
    private final ModelPart arm3;
    private final ModelPart beam_li;
    private final ModelPart holographcrafting;
    private final ModelPart inside;
    private final ModelPart outside;

    private final KeyframeAnimation crafting;
    private final KeyframeAnimation idle;

    public OmniCraftingTableModel(ModelPart root) {
        super(root.getChild("all"), RenderTypes::entityTranslucent);

        all = root.getChild("all");
        craftinggrid_end = all.getChild("craftinggrid_end");
        _fluid = all.getChild("_fluid");
        main = all.getChild("main");
        display = main.getChild("display");
        printer = main.getChild("printer");
        base = printer.getChild("base");
        arm1 = base.getChild("arm1");
        arm2 = arm1.getChild("arm2");
        arm3 = arm2.getChild("arm3");
        beam_li = arm3.getChild("beam_li");
        holographcrafting = main.getChild("holographcrafting");
        inside = holographcrafting.getChild("inside");
        outside = holographcrafting.getChild("outside");

        crafting = OmniCraftingTableAnimation.CRAFTING.bake(root);
        idle = OmniCraftingTableAnimation.IDLE.bake(root);
    }

    public static LayerDefinition createBodyLayer() {
        var meshdefinition = new MeshDefinition();
        var partdefinition = meshdefinition.getRoot();

        var all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        var craftinggrid_end = all.addOrReplaceChild("craftinggrid_end", CubeListBuilder.create().texOffs(3, 74).addBox(-4.0F, -0.01F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -17.0F, 1.0F, -3.1416F, 0.0F, 3.1416F));

        var _fluid = all.addOrReplaceChild("_fluid", CubeListBuilder.create().texOffs(0, 89).addBox(-20.0F, -21.0F, -4.5F, 3.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-15.0F, 8.0F, 1.0F, -3.1416F, 0.0F, 3.1416F));

        var main = all.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 55).addBox(-5.0F, -1.0F, -6.0F, 12.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(52, 51).addBox(-7.0F, -4.0F, -6.0F, 2.0F, 4.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(82, 50).addBox(-6.5F, -12.0F, -6.0F, 1.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(84, 73).addBox(-7.0F, -12.75F, -6.5F, 2.0F, 1.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(49, 77).addBox(-5.0F, -10.0F, 6.0F, 12.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(106, 92).addBox(19.0F, 2.0F, -6.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(124, 92).addBox(19.0F, 4.0F, -6.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(124, 106).addBox(19.0F, 7.0F, -6.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(131, 27).addBox(19.0F, 2.0F, -2.0F, 4.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(124, 99).addBox(19.0F, 10.0F, -6.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(106, 99).addBox(19.0F, 13.0F, -6.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 23).addBox(8.0F, 2.0F, -6.0F, 11.0F, 12.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(98, 29).addBox(-7.0F, 2.0F, 0.0F, 7.0F, 12.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(39, 96).addBox(-7.0F, 2.0F, -5.0F, 7.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(17, 96).addBox(-6.0F, 13.0F, -5.0F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(39, 107).addBox(-6.0F, 3.0F, -1.0F, 5.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 104).addBox(-6.0F, 3.0F, -5.0F, 1.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(12, 104).addBox(-2.0F, 3.0F, -5.0F, 1.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(24, 107).addBox(-6.0F, 3.0F, -5.0F, 5.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-8.0F, 14.0F, -7.0F, 32.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(82, 3).addBox(-8.0F, 0.0F, -7.0F, 32.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(52, 23).addBox(0.0F, 2.0F, -6.0F, 8.0F, 12.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(154, 24).addBox(2.0F, 4.0F, -7.0F, 7.0F, 7.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(34, 122).addBox(18.0F, -7.5F, 0.0F, 4.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(35, 139).addBox(12.0F, -0.5F, -3.5F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -16.0F, 1.0F, -3.1416F, 0.0F, 3.1416F));

        var cube_r1 = main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(117, 57).addBox(-2.5F, -4.0F, 1.6F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(116, 65).addBox(-4.5F, -5.0F, 0.6F, 9.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(116, 74).addBox(-4.0F, -4.5F, 0.59F, 8.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.25F, -5.0F, 4.15F, 0.0F, -1.5708F, -0.3054F));

        var cube_r2 = main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 121).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, -0.25F, -1.5F, 0.1745F, 0.0F, 0.0F));

        var cube_r3 = main.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(11, 122).addBox(-4.25F, -7.0F, 2.0F, 8.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.25F, -2.5F, -5.5F, -0.3491F, 0.0F, 0.0F));

        var display = main.addOrReplaceChild("display", CubeListBuilder.create(), PartPose.offset(0.0F, 13.5F, 1.5F));

        var cube_r4 = display.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 132).addBox(-7.25F, -9.0F, 0.0F, 14.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.25F, -16.0F, -7.0F, -0.3491F, 0.0F, 0.0F));

        var printer = main.addOrReplaceChild("printer", CubeListBuilder.create(), PartPose.offset(-19.0F, 16.0F, 1.5F));

        var base = printer.addOrReplaceChild("base", CubeListBuilder.create().texOffs(69, 103).addBox(-2.0F, -2.0F, -3.0F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(32.0F, -16.0F, 3.0F));

        var arm1 = base.addOrReplaceChild("arm1", CubeListBuilder.create(), PartPose.offset(0.5F, 0.0F, -0.5F));

        var cube_r5 = arm1.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(71, 126).addBox(-1.5F, -8.0F, -1.0F, 1.0F, 8.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(71, 112).addBox(-1.0F, -8.0F, -2.0F, 3.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -1.0F, 0.5F, 0.0F, 0.0F, 0.1745F));

        var arm2 = arm1.addOrReplaceChild("arm2", CubeListBuilder.create(), PartPose.offset(0.9F, -7.3F, 0.0F));

        var cube_r6 = arm2.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(77, 126).addBox(1.25F, -8.0F, -0.5F, 1.0F, 8.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(91, 107).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 0.75F, 0.0F, 0.0F, 0.0F, -0.5236F));

        var arm3 = arm2.addOrReplaceChild("arm3", CubeListBuilder.create(), PartPose.offset(-3.25F, -5.25F, 0.0F));

        var cube_r7 = arm3.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(76, 97).addBox(-2.25F, 1.75F, -1.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(86, 132).addBox(-1.75F, 2.25F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(84, 126).addBox(-2.25F, -1.0F, -1.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(91, 99).addBox(-2.75F, -5.5F, -2.0F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(91, 117).addBox(-2.75F, 0.0F, -2.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 2.75F, 0.5F, 0.0F, 0.0F, 0.6109F));

        var beam_li = arm3.addOrReplaceChild("beam_li", CubeListBuilder.create(), PartPose.offset(-1.25F, 2.75F, 0.5F));

        var cube_r8 = beam_li.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(95, 124).addBox(-1.25F, 4.25F, -1.0F, 0.0F, 8.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(95, 125).addBox(-1.75F, 4.25F, -0.5F, 1.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6109F));

        var holographcrafting = main.addOrReplaceChild("holographcrafting", CubeListBuilder.create(), PartPose.offset(0.0F, 4.0F, 0.0F));

        var inside = holographcrafting.addOrReplaceChild("inside", CubeListBuilder.create().texOffs(37, 70).addBox(-1.0F, -3.1F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -1.0F, -0.5F));

        var outside = holographcrafting.addOrReplaceChild("outside", CubeListBuilder.create().texOffs(37, 59).addBox(-1.0F, -3.1F, -1.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -1.0F, -0.5F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(OmniCraftingTableRenderState renderState) {
        super.setupAnim(renderState);
    }

    public void render(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay) {
        nodeCollector.submitModelPart(main, poseStack, renderType(Resource.Textures.OMNI_CRAFTING_TABLE), packedLight, packedOverlay, null);
        var rt = IrisCompat.isShaderPackInUse() ? RenderTypes.entitySolid(END_PORTAL_LOCATION) : RenderTypes.endGateway();
        nodeCollector.submitModelPart(craftinggrid_end, poseStack, rt, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);

        nodeCollector.submitModelPart(_fluid, poseStack, renderType(Resource.Textures.OMNI_CRAFTING_TABLE), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }
}