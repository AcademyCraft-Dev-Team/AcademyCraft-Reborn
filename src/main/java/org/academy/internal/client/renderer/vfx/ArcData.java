package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;
import org.academy.api.client.renderer.ArcFactory;

public interface ArcData extends VfxRenderData {
    ArcFactory.ArcRenderData renderData();
}
