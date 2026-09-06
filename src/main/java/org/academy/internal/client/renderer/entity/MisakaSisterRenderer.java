package org.academy.internal.client.renderer.entity;

import com.geckolib.constant.DefaultAnimations;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.jspecify.annotations.Nullable;

/**
 * GeckoLib renderer for Misaka Sister. When she is carried (passenger of a player),
 * Movement animations stop and a static hug silhouette is applied to geo bones.
 */
public final class MisakaSisterRenderer extends GeoEntityRenderer<MisakaSisterEntity, LivingEntityRenderState> {
    private static final DataTicket<Boolean> CARRIED = DataTicket.create("academy_misaka_carried", Boolean.class);

    /**
     * Whole-pose pitch on root {@code All} while carried (GeckoLib radians).
     * Tips the cradle as one rigid pose (head + body + legs) so feet rise without
     * changing inter-joint fold angles.
     */
    private static final float CARRY_WHOLE_PITCH = 0.45f;

    public MisakaSisterRenderer(EntityRendererProvider.Context context) {
        super(context, EntityTypes.MISAKA_SISTER.get());
        withScale(MisakaSisterEntity.MODEL_RENDER_SCALE);
        this.shadowRadius = 0.4f * MisakaSisterEntity.MODEL_RENDER_SCALE;
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
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        GeoRenderState geoState = (GeoRenderState) renderPassInfo.renderState();
        if (Boolean.TRUE.equals(geoState.getOrDefaultGeckolibData(CARRIED, false))) {
            applyCarriedPose(snapshots);
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

    private static void setRot(BoneSnapshots snapshots, String bone, float x, float y, float z) {
        snapshots.ifPresent(bone, snapshot -> snapshot.setRotation(x, y, z));
    }

    private static GeoRenderState asGeo(LivingEntityRenderState state) {
        return (GeoRenderState) (Object) state;
    }
}
