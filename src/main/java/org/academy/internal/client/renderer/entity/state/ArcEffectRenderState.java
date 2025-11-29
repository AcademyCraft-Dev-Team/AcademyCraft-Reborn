package org.academy.internal.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.academy.api.client.renderer.ArcFactory;

import java.util.List;

public class ArcEffectRenderState extends EntityRenderState {
    public List<ArcFactory.ArcRenderData> renderDataList;
}