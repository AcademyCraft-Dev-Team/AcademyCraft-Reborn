package org.academy.internal.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import org.academy.api.client.model.DistanceModelPolicy;
import org.academy.internal.client.renderer.entity.state.DarkmatterCreatureRenderState;

/**
 * Blockbench-authored white beetle with independently selectable blueprint branches.
 * Pose entry points take entity-neutral values so other render states can reuse the same rig.
 */
public final class DarkmatterCreatureModel extends EntityModel<DarkmatterCreatureRenderState> {
    public static final DistanceModelPolicy DETAIL_POLICY = new DistanceModelPolicy(16, 24, 2, 4);
    private static final String[] LEG_NAMES = {"front", "middle", "rear"};
    private final ModelPart all;
    private final ModelPart[] heads = new ModelPart[3];
    private final ModelPart[] torsos = new ModelPart[3];
    private final ModelPart[] limbs = new ModelPart[3];
    private final ModelPart[] additional = new ModelPart[4];
    private final ModelPart[][] legs = new ModelPart[3][6];
    private final ModelPart[][] tibiae = new ModelPart[3][6];
    private final ModelPart[] elytra = new ModelPart[2];
    private final ModelPart[] wings = new ModelPart[2];
    private final ModelPart[] fins = new ModelPart[2];
    private final ModelPart swimTail;
    private final ModelPart[] jaws = new ModelPart[2];
    private final ModelPart[] cannons = new ModelPart[2];
    private final ModelPart[] sensors = new ModelPart[2];
    private final ModelPart dorsalCannon;

    public DarkmatterCreatureModel(ModelPart root) {
        super(root);
        all = root.getChild("all");
        for (var i = 0; i < heads.length; i++) heads[i] = all.getChild("head_" + i);
        for (var i = 0; i < torsos.length; i++) torsos[i] = all.getChild("torso_" + i);
        for (var i = 0; i < limbs.length; i++) {
            limbs[i] = all.getChild("limbs_" + i);
            for (var side = 0; side < 2; side++) for (var leg = 0; leg < 3; leg++) {
                var part = limbs[i].getChild("leg_" + (side == 0 ? "left_" : "right_") + LEG_NAMES[leg]);
                legs[i][side * 3 + leg] = part;
                tibiae[i][side * 3 + leg] = part.getChild("tibia");
            }
        }
        for (var i = 0; i < additional.length; i++) additional[i] = all.getChild("additional_" + i);
        for (var side = 0; side < 2; side++) {
            var name = side == 0 ? "left" : "right";
            elytra[side] = torsos[1].getChild("elytra_" + name);
            wings[side] = torsos[1].getChild("wing_" + name);
            fins[side] = torsos[2].getChild("fin_" + name);
            jaws[side] = heads[0].getChild("jaw_" + name);
            sensors[side] = additional[2].getChild("sensor_" + name);
        }
        swimTail = torsos[2].getChild("swim_tail");
        cannons[0] = heads[1].getChild("cannon");
        cannons[1] = heads[2].getChild("cannon");
        dorsalCannon = additional[3].getChild("dorsal_cannon");
    }

    public static LayerDefinition createBodyLayer() {
        return DarkmatterCreatureGeometry.createBodyLayer();
    }

    public static LayerDefinition createLowDetailBodyLayer() {
        return DarkmatterCreatureLowDetailGeometry.createBodyLayer();
    }

    @Override
    public void setupAnim(DarkmatterCreatureRenderState state) {
        // The renderer shares one rig across entities. Always reset every selected movable part,
        // even when the preceding entity selected a different blueprint or distance tier.
        resetSelectedPose(state);
        select(heads, state.headModel);
        select(torsos, state.torsoModel);
        select(limbs, state.limbsModel);
        select(additional, state.additionalModel);
        var detail = DETAIL_POLICY.detail(state.distanceToCameraSq);
        var age = DETAIL_POLICY.sampleAge(state.ageInTicks, state.animationPhaseSeed, detail);
        var secondaryMotion = detail == DistanceModelPolicy.Detail.FULL;
        applyLocomotion(state.torsoModel, state.limbsModel, state.walkAnimationPos, state.walkAnimationSpeed,
                age, state.swimming, secondaryMotion);
        applyHeadPose(state.headModel, state.aimYaw, state.aimPitch,
                state.attackProgress, age, secondaryMotion);
        applyModulePose(state.additionalModel, state.aimYaw, state.attackProgress,
                age, state.gammaCatalyzed, secondaryMotion);
    }

    private void resetSelectedPose(DarkmatterCreatureRenderState state) {
        root.resetPose(); all.resetPose();
        if (valid(heads, state.headModel)) {
            heads[state.headModel].resetPose();
            if (state.headModel == 0) for (var jaw : jaws) jaw.resetPose();
            else cannons[state.headModel - 1].resetPose();
        }
        if (valid(torsos, state.torsoModel)) {
            torsos[state.torsoModel].resetPose();
            if (state.torsoModel == 1) for (var i = 0; i < 2; i++) {
                elytra[i].resetPose(); wings[i].resetPose();
            }
            if (state.torsoModel == 2) {
                swimTail.resetPose(); for (var fin : fins) fin.resetPose();
            }
        }
        if (valid(limbs, state.limbsModel)) {
            limbs[state.limbsModel].resetPose();
            for (var i = 0; i < 6; i++) {
                legs[state.limbsModel][i].resetPose(); tibiae[state.limbsModel][i].resetPose();
            }
        }
        if (valid(additional, state.additionalModel)) additional[state.additionalModel].resetPose();
        if (state.additionalModel == 2) for (var sensor : sensors) sensor.resetPose();
        if (state.additionalModel == 3) dorsalCannon.resetPose();
    }

    private static boolean valid(ModelPart[] parts, int selected) { return selected >= 0 && selected < parts.length; }

    /** Applies six-foot tripod gait, wing hinges or swimming strokes after the rig has been reset. */
    public void applyLocomotion(int torsoModel, float distance, float speed, float age, boolean swimming) {
        for (var branch = 0; branch < legs.length; branch++) {
            applyLegPose(torsoModel, branch, distance, speed, age, swimming, true);
        }
        applyTorsoPose(torsoModel, speed, age, swimming, true);
    }

    /** Selected-limb form avoids animation work on mutually hidden blueprint branches. */
    public void applyLocomotion(int torsoModel, int limbsModel, float distance, float speed, float age,
                                boolean swimming, boolean secondaryMotion) {
        if (valid(limbs, limbsModel)) applyLegPose(torsoModel, limbsModel, distance, speed, age, swimming, secondaryMotion);
        applyTorsoPose(torsoModel, speed, age, swimming, secondaryMotion);
    }

    private void applyLegPose(int torsoModel, int branch, float distance, float speed, float age,
                              boolean swimming, boolean secondaryMotion) {
        var amount = Math.clamp(speed, 0.0f, 1.0f);
        var airborne = torsoModel == 1;
        var inWater = torsoModel == 2 && swimming;
        for (var i = 0; i < 6; i++) {
            var side = i < 3 ? -1.0f : 1.0f;
            if (airborne) {
                legs[branch][i].zRot += side * .42f;
                legs[branch][i].xRot += .18f + (secondaryMotion ? Mth.sin(age * .2f + i) * .03f : 0);
                tibiae[branch][i].zRot -= side * .28f;
            } else if (inWater) {
                legs[branch][i].xRot += Mth.sin(age * .35f + i * .8f) * (.12f + amount * .3f);
                legs[branch][i].zRot += side * .12f;
            } else {
                var phase = distance * 1.15f + ((i % 3 + i / 3) % 2 == 0 ? 0.0f : Mth.PI);
                var stride = Mth.sin(phase) * amount;
                var lift = Math.max(0.0f, Mth.cos(phase)) * amount;
                legs[branch][i].yRot += stride * .28f;
                legs[branch][i].zRot -= side * lift * .18f;
                tibiae[branch][i].zRot += side * lift * .26f;
            }
        }
    }

    private void applyTorsoPose(int torsoModel, float speed, float age, boolean swimming, boolean secondaryMotion) {
        var amount = Math.clamp(speed, 0.0f, 1.0f);
        var airborne = torsoModel == 1;
        var inWater = torsoModel == 2 && swimming;
        if (airborne) {
            if (secondaryMotion) torsos[1].xRot += Mth.sin(age * .18f) * .025f;
            for (var i = 0; i < 2; i++) {
                var side = i == 0 ? -1.0f : 1.0f;
                elytra[i].zRot -= side * .52f;
                elytra[i].yRot += side * .12f;
                wings[i].zRot -= side * (.20f + Mth.sin(age * 1.8f) * .46f);
            }
        }
        if (inWater) {
            var stroke = Mth.sin(age * .35f) * (.10f + amount * .2f);
            swimTail.yRot += stroke;
            if (secondaryMotion) torsos[2].xRot += Mth.sin(age * .18f) * .035f;
            for (var i = 0; i < 2; i++) fins[i].xRot += (i == 0 ? stroke : -stroke);
        }
    }

    /** Aiming and one attack impulse; callers provide yaw/pitch in degrees and progress in [0,1]. */
    public void applyHeadPose(int headModel, float yaw, float pitch, float attackProgress, float age) {
        applyHeadPose(headModel, yaw, pitch, attackProgress, age, true);
    }

    private void applyHeadPose(int headModel, float yaw, float pitch, float attackProgress, float age, boolean secondaryMotion) {
        if (headModel < 0 || headModel >= heads.length) return;
        heads[headModel].yRot += Math.clamp(yaw, -55.0f, 55.0f) * Mth.DEG_TO_RAD * .65f;
        heads[headModel].xRot += Math.clamp(pitch, -35.0f, 35.0f) * Mth.DEG_TO_RAD * .55f;
        var impulse = Mth.sin(Math.clamp(attackProgress, 0.0f, 1.0f) * Mth.PI);
        if (headModel == 0) {
            var spread = .025f + (secondaryMotion ? Mth.sin(age * .13f) * .015f : 0) + impulse * .4f;
            jaws[0].yRot -= spread;
            jaws[1].yRot += spread;
            heads[0].xRot += impulse * .10f;
        } else {
            cannons[headModel - 1].z += impulse * .45f;
        }
    }

    /** Organic sensor movement and dorsal cannon aim, using only the blueprint's selected module. */
    public void applyModulePose(int module, float yaw, float attackProgress, float age, boolean gamma) {
        applyModulePose(module, yaw, attackProgress, age, gamma, true);
    }

    private void applyModulePose(int module, float yaw, float attackProgress, float age, boolean gamma, boolean secondaryMotion) {
        if (module == 2 && secondaryMotion) for (var i = 0; i < sensors.length; i++) {
            sensors[i].xRot += Mth.sin(age * .16f + i * Mth.PI) * (gamma ? .10f : .06f);
            sensors[i].zRot += Mth.sin(age * .10f + i) * .035f;
        }
        if (module == 3) {
            dorsalCannon.yRot += Math.clamp(yaw, -40.0f, 40.0f) * Mth.DEG_TO_RAD * .5f;
            dorsalCannon.z += Mth.sin(Math.clamp(attackProgress, 0.0f, 1.0f) * Mth.PI) * .3f;
        }
    }

    private static void select(ModelPart[] parts, int selected) {
        for (var i = 0; i < parts.length; i++) parts[i].visible = i == selected;
    }
}
