package org.academy.internal.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.academy.internal.client.renderer.entity.state.DarkmatterCreatureRenderState;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DarkmatterCreatureModelTest {
    private static final Path SOURCE = Path.of("docs/models/darkmatter_creature/darkmatter_creature.bbmodel");

    @Test
    void blockbenchSourceKeepsEveryBlueprintBranchExportableAndTextureCoordinatesInBounds() throws Exception {
        var project = JsonParser.parseString(Files.readString(SOURCE)).getAsJsonObject();
        assertEquals("modded_entity", project.getAsJsonObject("meta").get("model_format").getAsString());
        var all = project.getAsJsonArray("outliner").get(0).getAsJsonObject();
        for (var slot : new String[]{"head", "torso", "limbs", "additional"}) {
            var count = slot.equals("additional") ? 4 : 3;
            for (var i = 0; i < count; i++) {
                var branch = child(all, slot + "_" + i);
                assertTrue(branch.get("export").getAsBoolean(), "Hidden blueprint branches must still export");
            }
        }
        var width = project.getAsJsonObject("resolution").get("width").getAsInt();
        var height = project.getAsJsonObject("resolution").get("height").getAsInt();
        for (var element : project.getAsJsonArray("elements")) {
            var cube = element.getAsJsonObject();
            assertEquals("cube", cube.get("type").getAsString());
            for (var face : cube.getAsJsonObject("faces").entrySet()) {
                var coordinates = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                for (var axis = 0; axis < 4; axis++) {
                    var value = coordinates.get(axis).getAsFloat();
                    assertTrue(value >= 0 && value <= (axis % 2 == 0 ? width : height), cube.get("name") + " has an atlas overflow");
                }
            }
        }
    }

    @Test
    void everyBlueprintCombinationBakesAndSelectsExactlyOneBranchPerSlot() {
        var root = DarkmatterCreatureModel.createBodyLayer().bakeRoot();
        var model = new DarkmatterCreatureModel(root);
        var all = root.getChild("all");
        var state = new DarkmatterCreatureRenderState();
        state.ageInTicks = 17.5f;
        state.walkAnimationSpeed = .6f;
        state.walkAnimationPos = 4.0f;
        state.swimming = true;
        for (var head = 0; head < 3; head++) for (var torso = 0; torso < 3; torso++) {
            for (var limbs = 0; limbs < 3; limbs++) for (var additional = 0; additional < 4; additional++) {
                state.headModel = head;
                state.torsoModel = torso;
                state.limbsModel = limbs;
                state.additionalModel = additional;
                assertDoesNotThrow(() -> model.setupAnim(state));
                for (var i = 0; i < 3; i++) {
                    assertEquals(i == head, all.getChild("head_" + i).visible);
                    assertEquals(i == torso, all.getChild("torso_" + i).visible);
                    assertEquals(i == limbs, all.getChild("limbs_" + i).visible);
                }
                for (var i = 0; i < 4; i++) assertEquals(i == additional, all.getChild("additional_" + i).visible);
            }
        }
    }

    @Test
    void frameResetPreventsWingAndLegPoseFromAccumulating() {
        var root = DarkmatterCreatureModel.createBodyLayer().bakeRoot();
        var model = new DarkmatterCreatureModel(root);
        var state = new DarkmatterCreatureRenderState();
        state.torsoModel = 1;
        state.ageInTicks = 3.0f;
        var wing = root.getChild("all").getChild("torso_1").getChild("wing_left");
        model.setupAnim(state);
        var first = wing.zRot;
        model.setupAnim(state);
        assertEquals(first, wing.zRot, 0.00001f);
        assertNotEquals(0.0f, first);
    }

    @Test
    void sharedRigAndMaskReplayReturnToExactPoseAcrossAllBlueprintsAndDistanceTiers() {
        var root = DarkmatterCreatureModel.createBodyLayer().bakeRoot();
        var shared = new DarkmatterCreatureModel(root);
        var referenceRoot = DarkmatterCreatureModel.createBodyLayer().bakeRoot();
        var reference = new DarkmatterCreatureModel(referenceRoot);
        var lowRoot = DarkmatterCreatureModel.createLowDetailBodyLayer().bakeRoot();
        var low = new DarkmatterCreatureModel(lowRoot);
        for (var distance : new int[]{8, 20, 28}) for (var head = 0; head < 3; head++) for (var torso = 0; torso < 3; torso++) {
            for (var limbs = 0; limbs < 3; limbs++) for (var additional = 0; additional < 4; additional++) {
                var a = pose(head, torso, limbs, additional, distance);
                var b = pose((head + 1) % 3, (torso + 1) % 3, (limbs + 1) % 3, (additional + 1) % 4, distance == 8 ? 28 : 8);
                reference.resetPose(); reference.setupAnim(a);
                var expected = selectedPose(referenceRoot, a);
                shared.setupAnim(a); shared.setupAnim(b); shared.setupAnim(a);
                assertEquals(expected, selectedPose(root, a), "A -> B -> A with shared detailed rig");
                low.setupAnim(b); low.setupAnim(a);
                assertEquals(expected, selectedPose(lowRoot, a), "Low-detail geometry keeps identical joints and pose");
                if (distance == 8) {
                    reference.resetPose();
                    reference.applyLocomotion(a.torsoModel, a.walkAnimationPos, a.walkAnimationSpeed, a.ageInTicks, a.swimming);
                    reference.applyHeadPose(a.headModel, a.aimYaw, a.aimPitch, a.attackProgress, a.ageInTicks);
                    reference.applyModulePose(a.additionalModel, a.aimYaw, a.attackProgress, a.ageInTicks, a.gammaCatalyzed);
                    assertEquals(selectedPose(referenceRoot, a), selectedPose(root, a), "Near detail preserves the original public animation result");
                }
            }
        }
    }

    @Test
    void lowDetailHasEditableBlockbenchSourceAndKeepsEveryJointWithFewerCubes() throws Exception {
        var source = JsonParser.parseString(Files.readString(Path.of("docs/models/darkmatter_creature/darkmatter_creature_low_detail.bbmodel"))).getAsJsonObject();
        assertEquals("modded_entity", source.getAsJsonObject("meta").get("model_format").getAsString());
        assertEquals(95, source.getAsJsonArray("elements").size());
        var full = DarkmatterCreatureModel.createBodyLayer().bakeRoot();
        var low = DarkmatterCreatureModel.createLowDetailBodyLayer().bakeRoot();
        assertEquals(full.getAllParts().size(), low.getAllParts().size());
        var count = new int[1];
        low.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> count[0]++);
        assertEquals(95, count[0]);
    }

    private static DarkmatterCreatureRenderState pose(int head, int torso, int limbs, int additional, int distance) {
        var state = new DarkmatterCreatureRenderState();
        state.headModel = head; state.torsoModel = torso; state.limbsModel = limbs; state.additionalModel = additional;
        state.ageInTicks = 17.5f; state.walkAnimationPos = 4; state.walkAnimationSpeed = .6f;
        state.swimming = true; state.aimYaw = 28; state.aimPitch = -12; state.attackProgress = .45f;
        state.gammaCatalyzed = true; state.animationPhaseSeed = 13; state.distanceToCameraSq = distance * distance;
        return state;
    }

    private static List<PartPose> selectedPose(ModelPart root, DarkmatterCreatureRenderState state) {
        var result = new ArrayList<PartPose>(); var all = root.getChild("all");
        result.add(root.storePose()); result.add(all.storePose());
        for (var branch : new ModelPart[]{all.getChild("head_" + state.headModel), all.getChild("torso_" + state.torsoModel),
                all.getChild("limbs_" + state.limbsModel), all.getChild("additional_" + state.additionalModel)}) {
            for (var part : branch.getAllParts()) result.add(part.storePose());
        }
        return result;
    }

    private static JsonObject child(JsonObject group, String name) {
        for (var entry : group.getAsJsonArray("children")) {
            if (entry.isJsonObject() && name.equals(entry.getAsJsonObject().get("name").getAsString())) {
                return entry.getAsJsonObject();
            }
        }
        fail("Missing Blockbench branch: " + name);
        throw new AssertionError();
    }
}
