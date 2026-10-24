package org.academy.internal.client.renderer.entity;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.jspecify.annotations.Nullable;

/**
 * GeckoLib renderer for Misaka Sister. When she is carried (passenger of a player),
 * Movement animations stop and a static hug silhouette is applied to geo bones.
 * Incapacitated sisters use a light slumped pose (not vanilla death flip).
 */
public final class MisakaSisterRenderer extends GeoEntityRenderer<MisakaSisterEntity, LivingEntityRenderState> {
    private static final DataTicket<Boolean> CARRIED = DataTicket.create("academy_misaka_carried", Boolean.class);
    private static final DataTicket<Boolean> INCAPACITATED = DataTicket.create("academy_misaka_incap", Boolean.class);

    /**
     * Whole-pose pitch on root {@code All} while carried (GeckoLib radians).
     * Tips the cradle as one rigid pose (head + body + legs) so feet rise without
     * changing inter-joint fold angles.
     */
    private static final float CARRY_WHOLE_PITCH = 0.45f;

    /**
     * {@code All} pivot Y in geo units (see misaka_sister.geo.json). Rolling around this
     * mid-torso pivot lifts the body unless we also drop the bone onto the ground.
     */
    private static final float ALL_PIVOT_Y = 26.8f;
    /** ~90° onto the side — readable as downed without vanilla death flip. */
    private static final float INCAP_WHOLE_ROLL = (float) (Math.PI * 0.5);
    /**
     * Drop so the side rests on the ground (pivot height minus ~half torso thickness).
     * GeckoLib applies {@code translateY / 16} as blocks.
     */
    private static final float INCAP_GROUND_DROP_Y = -(ALL_PIVOT_Y - 3.5f);

    public MisakaSisterRenderer(EntityRendererProvider.Context context) {
        super(context, EntityTypes.MISAKA_SISTER.get());
        withScale(MisakaSisterEntity.MODEL_RENDER_SCALE);
        this.shadowRadius = 0.4f * MisakaSisterEntity.MODEL_RENDER_SCALE;
    }

    @Override
    public void extractRenderState(MisakaSisterEntity entity, LivingEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // deathTime / orphaned SLEEPING pose apply a 90° Z flip in GeoEntityRenderer.applyRotations.
        // GeckoLib also caches ENTITY_POSE during fillRenderState — patching state.pose alone is not enough.
        if (entity.getHealth() > 0.0f) {
            state.deathTime = 0.0f;
            if (state.pose == Pose.DYING || (state.pose == Pose.SLEEPING && !entity.isSleeping())) {
                clearSleepFlip(state);
            }
        }
        if (entity.isIncapacitated()) {
            state.deathTime = 0.0f;
            if (state.pose == Pose.DYING || state.pose == Pose.SLEEPING) {
                clearSleepFlip(state);
            }
        }
    }

    private static void clearSleepFlip(LivingEntityRenderState state) {
        state.pose = Pose.STANDING;
        state.bedOrientation = null;
        asGeo(state).addGeckolibData(DataTickets.ENTITY_POSE, Pose.STANDING);
    }

    @Override
    public void addRenderData(
            MisakaSisterEntity animatable,
            @Nullable Void relatedObject,
            LivingEntityRenderState renderState,
            float partialTick
    ) {
        boolean carried = animatable.isPassenger() && animatable.getVehicle() instanceof Player;
        asGeo(renderState).addGeckolibData(CARRIED, carried);
        asGeo(renderState).addGeckolibData(INCAPACITATED, !carried && animatable.isIncapacitated());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        GeoRenderState geoState = (GeoRenderState) renderPassInfo.renderState();
        if (Boolean.TRUE.equals(geoState.getOrDefaultGeckolibData(CARRIED, false))) {
            applyCarriedPose(snapshots);
            return;
        }
        if (Boolean.TRUE.equals(geoState.getOrDefaultGeckolibData(INCAPACITATED, false))) {
            applyIncapacitatedPose(snapshots);
            return;
        }
        DefaultAnimations.hardcodedHeadRotation(renderPassInfo, snapshots, "Head");
    }

    /**
     * Hug silhouette: relative limb folds stay fixed; {@code All} applies the whole-pose tip.
     */
    private static void applyCarriedPose(BoneSnapshots snapshots) {
        setRot(snapshots, "All", CARRY_WHOLE_PITCH, 0.0f, 0.0f);
        setRot(snapshots, "TopBody", -0.35f, 0.0f, -0.08f);
        setRot(snapshots, "LeftArm", 0.95f, 0.0f, -0.45f);
        setRot(snapshots, "RightArm", 0.90f, 0.0f, 0.50f);
        setRot(snapshots, "LeftLeg", 0.85f, 0.0f, -0.15f);
        setRot(snapshots, "RightLeg", 0.90f, 0.0f, 0.12f);
        setRot(snapshots, "LeftLowerLeg", 0.45f, 0.0f, 0.0f);
        setRot(snapshots, "RightLowerLeg", 0.48f, 0.0f, 0.0f);
    }

    private static void applyIncapacitatedPose(BoneSnapshots snapshots) {
        // Drop first (matrix order), then roll around the lowered pivot so she lies on the ground.
        snapshots.ifPresent("All", snapshot -> {
            snapshot.setTranslation(0.0f, INCAP_GROUND_DROP_Y, 0.0f);
            snapshot.setRotation(0.08f, 0.0f, INCAP_WHOLE_ROLL);
        });
        setRot(snapshots, "TopBody", 0.20f, 0.0f, 0.05f);
        setRot(snapshots, "Head", 0.40f, 0.20f, 0.0f);
        setRot(snapshots, "LeftArm", 0.70f, 0.0f, -0.45f);
        setRot(snapshots, "RightArm", 0.15f, 0.0f, 0.55f);
        setRot(snapshots, "LeftLeg", 0.25f, 0.0f, -0.12f);
        setRot(snapshots, "RightLeg", 0.55f, 0.0f, 0.18f);
        setRot(snapshots, "LeftLowerLeg", 0.35f, 0.0f, 0.0f);
        setRot(snapshots, "RightLowerLeg", 0.70f, 0.0f, 0.0f);
    }

    private static void setRot(BoneSnapshots snapshots, String bone, float x, float y, float z) {
        snapshots.ifPresent(bone, snapshot -> snapshot.setRotation(x, y, z));
    }

    private static GeoRenderState asGeo(LivingEntityRenderState state) {
        return (GeoRenderState) (Object) state;
    }
}
