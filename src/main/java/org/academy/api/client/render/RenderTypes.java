package org.academy.api.client.render;

import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.Resource;

public final class RenderTypes {
    private RenderTypes() {}

    public static final RenderType ABILITY_DEVELOPER = RenderType.entityTranslucent(Resource.Textures.MODEL_ABILITY_DEVELOPER);
    public static final RenderType CAT_ENGINE = RenderType.entityTranslucent(Resource.Textures.CAT_ENGINE);
    public static final RenderType CLEANING_ROBOT = RenderType.entitySolid(Resource.Textures.CLEANING_ROBOT);
}