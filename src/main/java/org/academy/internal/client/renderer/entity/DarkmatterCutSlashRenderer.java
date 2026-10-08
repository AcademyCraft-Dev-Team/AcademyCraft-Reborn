package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.academy.internal.client.renderer.entity.state.DarkmatterCutSlashRenderState;
import org.academy.internal.common.world.entity.skill.DarkmatterCutSlash;

/** The legacy tracking entity is invisible; its graph is owned by DarkmatterSlashVfx. */
public final class DarkmatterCutSlashRenderer
        extends EntityRenderer<DarkmatterCutSlash, DarkmatterCutSlashRenderState> {
    public DarkmatterCutSlashRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public DarkmatterCutSlashRenderState createRenderState() { return new DarkmatterCutSlashRenderState(); }
    @Override protected boolean affectedByCulling(DarkmatterCutSlash entity) { return false; }
}
