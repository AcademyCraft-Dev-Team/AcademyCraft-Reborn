package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.api.client.model.DistanceModelPolicy;
import org.academy.api.client.resources.R;
import org.academy.internal.client.model.DarkmatterCreatureModel;
import org.academy.internal.client.renderer.entity.state.DarkmatterCreatureRenderState;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

/**
 * Compatibility-named renderer for the new composable creature model.
 */
public final class DarkmatterBeetleRenderer extends MobRenderer<
        DarkmatterBeetle, DarkmatterCreatureRenderState, DarkmatterCreatureModel> {
    private final DarkmatterCreatureModel detailedModel;
    private final DarkmatterCreatureModel lowDetailModel;

    public DarkmatterBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new DarkmatterCreatureModel(
                DarkmatterCreatureModel.createBodyLayer().bakeRoot()), 0.45f);
        detailedModel = model;
        lowDetailModel = new DarkmatterCreatureModel(DarkmatterCreatureModel.createLowDetailBodyLayer().bakeRoot());
    }

    @Override
    public void submit(DarkmatterCreatureRenderState state, PoseStack poses,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        model = DarkmatterCreatureModel.DETAIL_POLICY.detail(state.distanceToCameraSq) == DistanceModelPolicy.Detail.COARSE
                ? lowDetailModel : detailedModel;
        try {
            // Deferred submit stores the chosen model and render state together. Each mask replay
            // chooses again from its own state, and setupAnim resets selected joints before drawing.
            super.submit(state, poses, collector, camera);
        } finally {
            model = detailedModel;
        }
    }

    @Override
    public Identifier getTextureLocation(DarkmatterCreatureRenderState state) {
        return R.textures.darkmatter_beetle;
    }

    @Override
    public DarkmatterCreatureRenderState createRenderState() {
        return new DarkmatterCreatureRenderState();
    }

    @Override
    public void extractRenderState(DarkmatterBeetle entity,
                                   DarkmatterCreatureRenderState state,
                                   float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.headModel = entity.headModel();
        state.torsoModel = entity.torsoModel();
        state.limbsModel = entity.limbsModel();
        state.additionalModel = entity.additionalModel();
        state.gammaCatalyzed = entity.gammaCatalyzed();
        state.swimming = entity.isInWater();
        state.aimYaw = Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot);
        state.aimPitch = entity.getXRot();
        state.attackProgress = entity.getAttackAnim(partialTicks);
        state.animationPhaseSeed = entity.getId();
    }
}
