package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.common.arc.ArcPath;
import org.academy.internal.client.renderer.arc.PathProcessor;
import org.academy.internal.client.renderer.entity.state.ArcEffectRenderState;
import org.academy.internal.common.world.entity.skill.ArcEffect;

import java.util.ArrayList;
import java.util.List;

public class ArcEffectRenderer extends EntityRenderer<ArcEffect, ArcEffectRenderState> {

    public ArcEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(ArcEffectRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.renderDataList == null || renderState.renderDataList.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-renderState.x, -renderState.y, -renderState.z);

        for (var renderData : renderState.renderDataList) {
            ArcFactory.render(poseStack, nodeCollector, renderData);
        }

        poseStack.popPose();
    }

    @Override
    public ArcEffectRenderState createRenderState() {
        return new ArcEffectRenderState();
    }

    @Override
    public void extractRenderState(ArcEffect entity, ArcEffectRenderState reusedState, float partialTick) {
        super.extractRenderState(entity, reusedState, partialTick);

        List<ArcPath> paths = entity.getArcPaths();
        if (paths.isEmpty()) {
            reusedState.renderDataList = null;
            return;
        }

        reusedState.renderDataList = new ArrayList<>(paths.size());
        for (ArcPath path : paths) {
            reusedState.renderDataList.add(PathProcessor.process(path, entity.tickCount));
        }
    }

    @Override
    protected boolean affectedByCulling(ArcEffect entity) {
        return false;
    }
}