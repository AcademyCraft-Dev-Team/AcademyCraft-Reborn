package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.client.model.MisakaSisterModel;
import org.academy.internal.client.renderer.entity.state.MisakaSisterRenderState;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

public final class MisakaSisterRenderer extends MobRenderer<
        MisakaSisterEntity, MisakaSisterRenderState, MisakaSisterModel> {
    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    public MisakaSisterRenderer(EntityRendererProvider.Context context) {
        super(context, new MisakaSisterModel(MisakaSisterModel.createBodyLayer().bakeRoot()), 0.4f);
    }

    @Override
    public Identifier getTextureLocation(MisakaSisterRenderState state) {
        return TEXTURE;
    }

    @Override
    public MisakaSisterRenderState createRenderState() {
        return new MisakaSisterRenderState();
    }

    @Override
    public void extractRenderState(MisakaSisterEntity entity, MisakaSisterRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.carried = entity.isPassenger() && entity.getVehicle() instanceof Player;
    }
}
