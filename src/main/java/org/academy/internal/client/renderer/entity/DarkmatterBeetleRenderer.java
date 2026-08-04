package org.academy.internal.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.endermite.EndermiteModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.academy.api.client.resources.R;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

public final class DarkmatterBeetleRenderer extends MobRenderer<
        DarkmatterBeetle, LivingEntityRenderState, EndermiteModel> {
    public DarkmatterBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new EndermiteModel(context.bakeLayer(ModelLayers.ENDERMITE)), 0.3f);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return R.textures.darkmatter_beetle;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}
