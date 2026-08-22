package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.internal.client.model.DarkmatterCreatureModel;
import org.academy.internal.client.renderer.entity.state.DarkmatterCreatureRenderState;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

/** Compatibility-named renderer for the new composable creature model. */
public final class DarkmatterBeetleRenderer extends MobRenderer<
        DarkmatterBeetle, DarkmatterCreatureRenderState, DarkmatterCreatureModel> {
    public DarkmatterBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new DarkmatterCreatureModel(
                DarkmatterCreatureModel.createBodyLayer().bakeRoot()), 0.45f);
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
    }
}
