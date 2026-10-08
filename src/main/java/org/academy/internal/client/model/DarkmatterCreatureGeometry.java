package org.academy.internal.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Geometry exported from docs/models/darkmatter_creature/darkmatter_creature.bbmodel.
 * Edit the Blockbench project and run its export_geometry.cjs adapter to update this class.
 * Hidden authoring branches are deliberately exported for runtime blueprint selection.
 */
final class DarkmatterCreatureGeometry {
    private DarkmatterCreatureGeometry() {
    }

    static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var part0 = mesh.getRoot().addOrReplaceChild("all", CubeListBuilder.create()
                , PartPose.offset(0.0f, 24.0f, 0.0f));
        var part1 = part0.addOrReplaceChild("head_0", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.6f, -2.3f, -3.5f, 5.2f, 3.8f, 4.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.1f, -3.0f, -2.8f, 4.2f, 1.2f, 3.6f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.3f, -1.2f, 0.4f, 4.6f, 2.7f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(-2.75f, -1.4f, -2.9f, 0.3f, 0.55f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(2.45f, -1.4f, -2.9f, 0.3f, 0.55f, 1.1f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -8.0f, -4.0f));
        var part2 = part1.addOrReplaceChild("antenna_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.16f, -0.2f, -2.2f, 0.32f, 0.32f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.32f, -0.35f, -2.6f, 0.64f, 0.5f, 0.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.15f, -2.4f, -2.8f, 0.0f, -0.314159f, -0.349066f));
        var part3 = part1.addOrReplaceChild("antenna_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.16f, -0.2f, -2.2f, 0.32f, 0.32f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.32f, -0.35f, -2.6f, 0.64f, 0.5f, 0.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.15f, -2.4f, -2.8f, 0.0f, 0.314159f, 0.349066f));
        var part4 = part1.addOrReplaceChild("horn", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.05f, -0.4f, -2.0f, 2.1f, 1.5f, 3.1f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.92f, -1.35f, -3.3f, 1.84f, 1.4f, 2.2f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.75f, -2.65f, -4.05f, 1.5f, 1.6f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.55f, -4.1f, -4.6f, 1.1f, 1.8f, 1.2f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.3f, -5.55f, -4.95f, 0.6f, 1.65f, 0.7f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -2.0f, -2.8f));
        var part5 = part1.addOrReplaceChild("jaw_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.58f, -0.35f, -2.1f, 1.16f, 1.35f, 2.4f, new CubeDeformation(0.0f))
                .texOffs(64, 64).addBox(0.0f, -0.2f, -3.0f, 1.0f, 0.85f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.7f, 0.0f, -3.1f, 0.35f, 0.6f, 0.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.15f, 0.75f, -2.7f, 0.0f, 0.20944f, 0.0f));
        var part6 = part1.addOrReplaceChild("jaw_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.58f, -0.35f, -2.1f, 1.16f, 1.35f, 2.4f, new CubeDeformation(0.0f))
                .texOffs(64, 64).addBox(-1.0f, -0.2f, -3.0f, 1.0f, 0.85f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.05f, 0.0f, -3.1f, 0.35f, 0.6f, 0.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.15f, 0.75f, -2.7f, 0.0f, -0.20944f, 0.0f));
        var part7 = part0.addOrReplaceChild("head_1", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.6f, -2.3f, -3.5f, 5.2f, 3.8f, 4.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.1f, -3.0f, -2.8f, 4.2f, 1.2f, 3.6f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.3f, -1.2f, 0.4f, 4.6f, 2.7f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(-2.75f, -1.4f, -2.9f, 0.3f, 0.55f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(2.45f, -1.4f, -2.9f, 0.3f, 0.55f, 1.1f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -8.0f, -4.0f));
        var part8 = part7.addOrReplaceChild("antenna_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.16f, -0.2f, -2.2f, 0.32f, 0.32f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.32f, -0.35f, -2.6f, 0.64f, 0.5f, 0.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.15f, -2.4f, -2.8f, 0.0f, -0.314159f, -0.349066f));
        var part9 = part7.addOrReplaceChild("antenna_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.16f, -0.2f, -2.2f, 0.32f, 0.32f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.32f, -0.35f, -2.6f, 0.64f, 0.5f, 0.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.15f, -2.4f, -2.8f, 0.0f, 0.314159f, 0.349066f));
        var part10 = part7.addOrReplaceChild("horn", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.8f, -0.5f, -1.8f, 1.6f, 1.2f, 2.3f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.55f, -1.9f, -2.4f, 1.1f, 1.5f, 1.2f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.3f, -3.0f, -2.75f, 0.6f, 1.3f, 0.7f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -2.0f, -2.8f));
        var part11 = part7.addOrReplaceChild("cannon", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.95f, -0.85f, -2.5f, 1.9f, 1.8f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.8f, -0.85f, -4.3f, 1.6f, 0.36f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.8f, 0.49f, -4.3f, 1.6f, 0.36f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.8f, -0.5f, -4.3f, 0.36f, 1.0f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.44f, -0.5f, -4.3f, 0.36f, 1.0f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(-0.44f, -0.49f, -2.58f, 0.88f, 0.98f, 0.08f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -0.65f, -2.6f));
        var part12 = part0.addOrReplaceChild("head_2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.6f, -2.3f, -3.5f, 5.2f, 3.8f, 4.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.1f, -3.0f, -2.8f, 4.2f, 1.2f, 3.6f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.3f, -1.2f, 0.4f, 4.6f, 2.7f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(-2.75f, -1.4f, -2.9f, 0.3f, 0.55f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(2.45f, -1.4f, -2.9f, 0.3f, 0.55f, 1.1f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -8.0f, -4.0f));
        var part13 = part12.addOrReplaceChild("antenna_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.16f, -0.2f, -2.2f, 0.32f, 0.32f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.32f, -0.35f, -2.6f, 0.64f, 0.5f, 0.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.15f, -2.4f, -2.8f, 0.0f, -0.314159f, -0.349066f));
        var part14 = part12.addOrReplaceChild("antenna_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.16f, -0.2f, -2.2f, 0.32f, 0.32f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.32f, -0.35f, -2.6f, 0.64f, 0.5f, 0.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.15f, -2.4f, -2.8f, 0.0f, 0.314159f, 0.349066f));
        var part15 = part12.addOrReplaceChild("horn", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.8f, -0.5f, -1.8f, 1.6f, 1.2f, 2.3f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.55f, -1.9f, -2.4f, 1.1f, 1.5f, 1.2f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.3f, -3.0f, -2.75f, 0.6f, 1.3f, 0.7f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -2.0f, -2.8f));
        var part16 = part12.addOrReplaceChild("cannon", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.3f, -0.85f, -2.5f, 1.9f, 1.8f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.15f, -0.85f, -4.3f, 1.6f, 0.36f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.15f, 0.49f, -4.3f, 1.6f, 0.36f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.15f, -0.5f, -4.3f, 0.36f, 1.0f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.91f, -0.5f, -4.3f, 0.36f, 1.0f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(-1.79f, -0.49f, -2.58f, 0.88f, 0.98f, 0.08f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.4f, -0.85f, -2.5f, 1.9f, 1.8f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.55f, -0.85f, -4.3f, 1.6f, 0.36f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.55f, 0.49f, -4.3f, 1.6f, 0.36f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.55f, -0.5f, -4.3f, 0.36f, 1.0f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(1.79f, -0.5f, -4.3f, 0.36f, 1.0f, 1.9f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(0.91f, -0.49f, -2.58f, 0.88f, 0.98f, 0.08f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -0.65f, -2.6f));
        var part17 = part0.addOrReplaceChild("torso_0", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.35f, -1.9f, -3.6f, 6.7f, 3.9f, 8.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.65f, -1.4f, 4.6f, 5.3f, 3.0f, 1.65f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.5f, -2.0f, -3.8f, 7.0f, 3.4f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.9f, 1.7f, -1.8f, 5.8f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.8f, 1.7f, 0.0f, 5.6f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.7f, 1.7f, 1.8f, 5.4f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -5.2f, 1.1f));
        var part18 = part17.addOrReplaceChild("elytra_left", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2.75f, -0.55f, -2.4f, 2.75f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.0f, 0.12f, -2.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.35f, -1.0f, -0.6f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.6f, 0.12f, -0.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.35f, -1.0f, 1.2f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.6f, 0.12f, 1.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.35f, -0.55f, 3.0f, 2.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.6f, 0.12f, 3.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                , PartPose.offset(-0.4f, -2.4f, -0.9f));
        var part19 = part17.addOrReplaceChild("elytra_right", CubeListBuilder.create()
                .texOffs(0, 32).addBox(0.0f, -0.55f, -2.4f, 2.75f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.2f, 0.12f, -2.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -1.0f, -0.6f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.8f, 0.12f, -0.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -1.0f, 1.2f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.8f, 0.12f, 1.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.55f, 3.0f, 2.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(1.8f, 0.12f, 3.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                , PartPose.offset(0.4f, -2.4f, -0.9f));
        var part20 = part0.addOrReplaceChild("torso_1", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.35f, -1.9f, -3.6f, 6.7f, 3.9f, 8.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.65f, -1.4f, 4.6f, 5.3f, 3.0f, 1.65f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.5f, -2.0f, -3.8f, 7.0f, 3.4f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.9f, 1.7f, -1.8f, 5.8f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.8f, 1.7f, 0.0f, 5.6f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.7f, 1.7f, 1.8f, 5.4f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -5.2f, 1.1f));
        var part21 = part20.addOrReplaceChild("elytra_left", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2.75f, -0.55f, -2.4f, 2.75f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.0f, 0.12f, -2.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.35f, -1.0f, -0.6f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.6f, 0.12f, -0.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.35f, -1.0f, 1.2f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.6f, 0.12f, 1.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.35f, -0.55f, 3.0f, 2.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.6f, 0.12f, 3.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                , PartPose.offset(-0.4f, -2.4f, -0.9f));
        var part22 = part20.addOrReplaceChild("wing_left", CubeListBuilder.create()
                , PartPose.offset(-2.6f, -1.7f, -0.5f));
        var part23 = part22.addOrReplaceChild("wing_blade_0", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-5.7f, -0.09f, -0.2f, 5.7f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-5.7f, -0.13f, -0.12f, 5.7f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, -1.3f, 0.0f, 0.20944f, 0.0f));
        var part24 = part22.addOrReplaceChild("wing_blade_1", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-6.5f, -0.09f, -0.2f, 6.5f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-6.5f, -0.13f, -0.12f, 6.5f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, -0.15f, 0.0f, 0.261799f, 0.0f));
        var part25 = part22.addOrReplaceChild("wing_blade_2", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-7.3f, -0.09f, -0.2f, 7.3f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-7.3f, -0.13f, -0.12f, 7.3f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, 1.0f, 0.0f, 0.314159f, 0.0f));
        var part26 = part22.addOrReplaceChild("wing_blade_3", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-6.5f, -0.09f, -0.2f, 6.5f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-6.5f, -0.13f, -0.12f, 6.5f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, 2.15f, 0.0f, 0.366519f, 0.0f));
        var part27 = part22.addOrReplaceChild("wing_blade_4", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-5.7f, -0.09f, -0.2f, 5.7f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-5.7f, -0.13f, -0.12f, 5.7f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, 3.3f, 0.0f, 0.418879f, 0.0f));
        var part28 = part20.addOrReplaceChild("elytra_right", CubeListBuilder.create()
                .texOffs(0, 32).addBox(0.0f, -0.55f, -2.4f, 2.75f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.2f, 0.12f, -2.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -1.0f, -0.6f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.8f, 0.12f, -0.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -1.0f, 1.2f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.8f, 0.12f, 1.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.55f, 3.0f, 2.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(1.8f, 0.12f, 3.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                , PartPose.offset(0.4f, -2.4f, -0.9f));
        var part29 = part20.addOrReplaceChild("wing_right", CubeListBuilder.create()
                , PartPose.offset(2.6f, -1.7f, -0.5f));
        var part30 = part29.addOrReplaceChild("wing_blade_0", CubeListBuilder.create()
                .texOffs(0, 64).addBox(0.0f, -0.09f, -0.2f, 5.7f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.13f, -0.12f, 5.7f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, -1.3f, 0.0f, -0.20944f, 0.0f));
        var part31 = part29.addOrReplaceChild("wing_blade_1", CubeListBuilder.create()
                .texOffs(0, 64).addBox(0.0f, -0.09f, -0.2f, 6.5f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.13f, -0.12f, 6.5f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, -0.15f, 0.0f, -0.261799f, 0.0f));
        var part32 = part29.addOrReplaceChild("wing_blade_2", CubeListBuilder.create()
                .texOffs(0, 64).addBox(0.0f, -0.09f, -0.2f, 7.3f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.13f, -0.12f, 7.3f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, 1.0f, 0.0f, -0.314159f, 0.0f));
        var part33 = part29.addOrReplaceChild("wing_blade_3", CubeListBuilder.create()
                .texOffs(0, 64).addBox(0.0f, -0.09f, -0.2f, 6.5f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.13f, -0.12f, 6.5f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, 2.15f, 0.0f, -0.366519f, 0.0f));
        var part34 = part29.addOrReplaceChild("wing_blade_4", CubeListBuilder.create()
                .texOffs(0, 64).addBox(0.0f, -0.09f, -0.2f, 5.7f, 0.18f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.13f, -0.12f, 5.7f, 0.08f, 0.12f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 0.0f, 3.3f, 0.0f, -0.418879f, 0.0f));
        var part35 = part0.addOrReplaceChild("torso_2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.35f, -1.9f, -3.6f, 6.7f, 3.9f, 10.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.65f, -1.4f, 4.6f, 5.3f, 3.0f, 1.65f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.5f, -2.0f, -3.8f, 7.0f, 3.4f, 1.6f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.9f, 1.7f, -1.8f, 5.8f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.8f, 1.7f, 0.0f, 5.6f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                .texOffs(64, 0).addBox(-2.7f, 1.7f, 1.8f, 5.4f, 0.42f, 1.1f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -5.2f, 1.1f));
        var part36 = part35.addOrReplaceChild("elytra_left", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2.75f, -0.55f, -2.4f, 2.75f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.0f, 0.12f, -2.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.35f, -1.0f, -0.6f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.6f, 0.12f, -0.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-3.35f, -1.0f, 1.2f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-3.6f, 0.12f, 1.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.35f, -0.55f, 3.0f, 2.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-2.6f, 0.12f, 3.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                , PartPose.offset(-0.4f, -2.4f, -0.9f));
        var part37 = part35.addOrReplaceChild("elytra_right", CubeListBuilder.create()
                .texOffs(0, 32).addBox(0.0f, -0.55f, -2.4f, 2.75f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.2f, 0.12f, -2.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -1.0f, -0.6f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.8f, 0.12f, -0.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -1.0f, 1.2f, 3.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(2.8f, 0.12f, 1.4f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.0f, -0.55f, 3.0f, 2.35f, 1.1f, 2.35f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(1.8f, 0.12f, 3.2f, 0.8f, 0.5f, 2.05f, new CubeDeformation(0.0f))
                , PartPose.offset(0.4f, -2.4f, -0.9f));
        var part38 = part35.addOrReplaceChild("fin_left", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-1.8f, -0.12f, 0.0f, 1.8f, 0.24f, 2.8f, new CubeDeformation(0.0f))
                .texOffs(0, 64).addBox(-3.15f, -0.09f, 1.2f, 1.8f, 0.18f, 2.8f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.5f, 0.0f, 3.5f, 0.0f, 0.436332f, 0.0f));
        var part39 = part35.addOrReplaceChild("fin_right", CubeListBuilder.create()
                .texOffs(0, 64).addBox(0.0f, -0.12f, 0.0f, 1.8f, 0.24f, 2.8f, new CubeDeformation(0.0f))
                .texOffs(0, 64).addBox(1.35f, -0.09f, 1.2f, 1.8f, 0.18f, 2.8f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.5f, 0.0f, 3.5f, 0.0f, -0.436332f, 0.0f));
        var part40 = part35.addOrReplaceChild("swim_tail", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.55f, -0.4f, -0.2f, 1.1f, 0.8f, 2.2f, new CubeDeformation(0.0f))
                .texOffs(0, 64).addBox(-2.35f, -0.12f, 1.7f, 4.7f, 0.24f, 1.8f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, 0.6f, 5.5f));
        var part41 = part0.addOrReplaceChild("limbs_0", CubeListBuilder.create()
                , PartPose.offset(0.0f, 0.0f, 0.0f));
        var part42 = part41.addOrReplaceChild("leg_left_front", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, -2.7f, 0.0f, 0.314159f, -0.261799f));
        var part43 = part42.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.9f, 0.6f, -0.8f, 0.5f, 2.3f, 1.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part44 = part43.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part45 = part41.addOrReplaceChild("leg_left_middle", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, 0.7f, 0.0f, 0.0f, -0.261799f));
        var part46 = part45.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part47 = part46.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part48 = part41.addOrReplaceChild("leg_left_rear", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, 4.1f, 0.0f, -0.314159f, -0.261799f));
        var part49 = part48.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part50 = part49.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part51 = part41.addOrReplaceChild("leg_right_front", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, -2.7f, 0.0f, -0.314159f, 0.261799f));
        var part52 = part51.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.4f, 0.6f, -0.8f, 0.5f, 2.3f, 1.6f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part53 = part52.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part54 = part41.addOrReplaceChild("leg_right_middle", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, 0.7f, 0.0f, 0.0f, 0.261799f));
        var part55 = part54.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part56 = part55.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part57 = part41.addOrReplaceChild("leg_right_rear", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, 4.1f, 0.0f, 0.314159f, 0.261799f));
        var part58 = part57.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part59 = part58.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part60 = part0.addOrReplaceChild("limbs_1", CubeListBuilder.create()
                , PartPose.offset(0.0f, 0.0f, 0.0f));
        var part61 = part60.addOrReplaceChild("leg_left_front", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, -2.7f, 0.0f, 0.314159f, -0.261799f));
        var part62 = part61.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part63 = part62.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-1.3f, -0.2f, -2.35f, 2.6f, 0.5f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.05f, -0.1f, -2.85f, 0.5f, 0.35f, 0.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.25f, -0.1f, -2.85f, 0.5f, 0.35f, 0.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.55f, -0.1f, -2.85f, 0.5f, 0.35f, 0.8f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part64 = part60.addOrReplaceChild("leg_left_middle", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, 0.7f, 0.0f, 0.0f, -0.261799f));
        var part65 = part64.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part66 = part65.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part67 = part60.addOrReplaceChild("leg_left_rear", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, 4.1f, 0.0f, -0.314159f, -0.261799f));
        var part68 = part67.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part69 = part68.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part70 = part60.addOrReplaceChild("leg_right_front", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, -2.7f, 0.0f, -0.314159f, 0.261799f));
        var part71 = part70.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part72 = part71.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-1.3f, -0.2f, -2.35f, 2.6f, 0.5f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.05f, -0.1f, -2.85f, 0.5f, 0.35f, 0.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.25f, -0.1f, -2.85f, 0.5f, 0.35f, 0.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.55f, -0.1f, -2.85f, 0.5f, 0.35f, 0.8f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part73 = part60.addOrReplaceChild("leg_right_middle", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, 0.7f, 0.0f, 0.0f, 0.261799f));
        var part74 = part73.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part75 = part74.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part76 = part60.addOrReplaceChild("leg_right_rear", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, 4.1f, 0.0f, 0.314159f, 0.261799f));
        var part77 = part76.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part78 = part77.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part79 = part0.addOrReplaceChild("limbs_2", CubeListBuilder.create()
                , PartPose.offset(0.0f, 0.0f, 0.0f));
        var part80 = part79.addOrReplaceChild("leg_left_front", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, -2.7f, 0.0f, 0.314159f, -0.261799f));
        var part81 = part80.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part82 = part81.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part83 = part79.addOrReplaceChild("leg_left_middle", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, 0.7f, 0.0f, 0.0f, -0.261799f));
        var part84 = part83.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part85 = part84.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part86 = part79.addOrReplaceChild("leg_left_rear", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-2.9f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.65f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-2.7f, -1.35f, -1.2f, 2.3f, 0.6f, 2.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-3.1f, -4.7f, 4.1f, 0.0f, -0.314159f, -0.261799f));
        var part87 = part86.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-2.6f, 0.45f, 0.0f, 0.0f, 0.0f, 0.436332f));
        var part88 = part87.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, -0.174533f));
        var part89 = part79.addOrReplaceChild("leg_right_front", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, -2.7f, 0.0f, -0.314159f, 0.261799f));
        var part90 = part89.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part91 = part90.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part92 = part79.addOrReplaceChild("leg_right_middle", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, 0.7f, 0.0f, 0.0f, 0.261799f));
        var part93 = part92.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part94 = part93.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part95 = part79.addOrReplaceChild("leg_right_rear", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-0.2f, -0.6f, -0.65f, 3.1f, 1.2f, 1.3f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.35f, -0.88f, -0.85f, 2.3f, 0.65f, 1.7f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(0.4f, -1.35f, -1.2f, 2.3f, 0.6f, 2.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(3.1f, -4.7f, 4.1f, 0.0f, 0.314159f, 0.261799f));
        var part96 = part95.addOrReplaceChild("tibia", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -0.2f, -0.5f, 1.0f, 3.9f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.65f, -0.3f, -0.7f, 1.3f, 2.6f, 1.4f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(2.6f, 0.45f, 0.0f, 0.0f, 0.0f, -0.436332f));
        var part97 = part96.addOrReplaceChild("tarsus", CubeListBuilder.create()
                .texOffs(64, 64).addBox(-0.6f, -0.25f, -0.95f, 1.2f, 0.65f, 1.8f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.5f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.16f, 0.06f, -1.55f, 0.34f, 0.35f, 1.0f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, 3.4f, 0.0f, 0.0f, 0.0f, 0.174533f));
        var part98 = part0.addOrReplaceChild("additional_0", CubeListBuilder.create()
                , PartPose.offset(0.0f, 0.0f, 0.0f));
        var part99 = part0.addOrReplaceChild("additional_1", CubeListBuilder.create()
                , PartPose.offset(0.0f, -8.7f, 1.1f));
        var part100 = part99.addOrReplaceChild("armor_-1_0", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-1.5f, -0.12f, -1.6f, 0.0f, 0.0f, -0.20944f));
        var part101 = part99.addOrReplaceChild("armor_-1_1", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-1.5f, -0.12f, 0.25f, 0.0f, 0.0f, -0.20944f));
        var part102 = part99.addOrReplaceChild("armor_-1_2", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-1.5f, -0.12f, 2.1f, 0.0f, 0.0f, -0.20944f));
        var part103 = part99.addOrReplaceChild("armor_-1_3", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-1.5f, -0.12f, 3.95f, 0.0f, 0.0f, -0.20944f));
        var part104 = part99.addOrReplaceChild("armor_1_0", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(1.5f, -0.12f, -1.6f, 0.0f, 0.0f, 0.20944f));
        var part105 = part99.addOrReplaceChild("armor_1_1", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(1.5f, -0.12f, 0.25f, 0.0f, 0.0f, 0.20944f));
        var part106 = part99.addOrReplaceChild("armor_1_2", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(1.5f, -0.12f, 2.1f, 0.0f, 0.0f, 0.20944f));
        var part107 = part99.addOrReplaceChild("armor_1_3", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.55f, -0.35f, -0.6f, 3.1f, 0.65f, 2.7f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-1.3f, -0.15f, 1.5f, 2.6f, 0.36f, 1.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(1.5f, -0.12f, 3.95f, 0.0f, 0.0f, 0.20944f));
        var part108 = part0.addOrReplaceChild("additional_2", CubeListBuilder.create()
                , PartPose.offset(0.0f, -8.4f, 0.0f));
        var part109 = part108.addOrReplaceChild("sensor_left", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.18f, -3.4f, -0.18f, 0.36f, 3.5f, 0.36f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.75f, -3.65f, -0.36f, 1.5f, 0.4f, 0.72f, new CubeDeformation(0.0f))
                .texOffs(0, 64).addBox(-0.48f, -3.7f, -0.38f, 0.96f, 0.13f, 0.76f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(-1.5f, 0.0f, 0.0f, 0.20944f, 0.0f, -0.279253f));
        var part110 = part108.addOrReplaceChild("sensor_right", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.18f, -3.4f, -0.18f, 0.36f, 3.5f, 0.36f, new CubeDeformation(0.0f))
                .texOffs(0, 32).addBox(-0.75f, -3.65f, -0.36f, 1.5f, 0.4f, 0.72f, new CubeDeformation(0.0f))
                .texOffs(0, 64).addBox(-0.48f, -3.7f, -0.38f, 0.96f, 0.13f, 0.76f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(1.5f, 0.0f, 0.0f, 0.20944f, 0.0f, 0.279253f));
        var part111 = part0.addOrReplaceChild("additional_3", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-1.6f, -0.3f, -1.25f, 3.2f, 1.7f, 2.5f, new CubeDeformation(0.0f))
                , PartPose.offset(0.0f, -9.3f, 3.2f));
        var part112 = part111.addOrReplaceChild("dorsal_cannon", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.9f, -0.8f, -4.1f, 1.8f, 0.35f, 4.1f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.9f, 0.45f, -4.1f, 1.8f, 0.35f, 4.1f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(-0.9f, -0.46f, -4.1f, 0.35f, 0.92f, 4.1f, new CubeDeformation(0.0f))
                .texOffs(0, 0).addBox(0.55f, -0.46f, -4.1f, 0.35f, 0.92f, 4.1f, new CubeDeformation(0.0f))
                .texOffs(64, 32).addBox(-0.55f, -0.45f, -2.0f, 1.1f, 0.9f, 0.15f, new CubeDeformation(0.0f))
                , PartPose.offsetAndRotation(0.0f, -0.55f, -0.45f, -0.139626f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
