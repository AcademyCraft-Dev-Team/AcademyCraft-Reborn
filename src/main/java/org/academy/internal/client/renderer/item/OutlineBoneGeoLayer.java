package org.academy.internal.client.renderer.item;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Hides {@code *_outline} bones from the main (typically double-sided) pass and
 * re-submits them with backface culling — Blockbench "单面" for inset glass shells.
 */
public final class OutlineBoneGeoLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends GeoRenderLayer<T, O, R> {
    private static final String OUTLINE_SUFFIX = "_outline";

    public OutlineBoneGeoLayer(GeoRenderer<T, O, R> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        if (!renderPassInfo.willRender()) {
            return;
        }
        List<CuboidGeoBone> outlineBones = outlineBones(renderPassInfo);
        if (outlineBones.isEmpty()) {
            return;
        }
        renderPassInfo.addBoneUpdater((_, snapshots) -> {
            for (CuboidGeoBone bone : outlineBones) {
                snapshots.get(bone.name()).ifPresent(snapshot -> {
                    boolean skipChildren = snapshot.areChildrenHidden();
                    snapshot.skipRender(true);
                    snapshot.skipChildrenRender(skipChildren);
                });
            }
        });
    }

    @Override
    public void addPerBoneRender(
            RenderPassInfo<R> renderPassInfo,
            BiConsumer<GeoBone, PerBoneRender<R>> consumer
    ) {
        if (!renderPassInfo.willRender()) {
            return;
        }
        for (CuboidGeoBone bone : outlineBones(renderPassInfo)) {
            consumer.accept(bone, this::renderBone);
        }
    }

    private void renderBone(RenderPassInfo<R> renderPassInfo, GeoBone bone, SubmitNodeCollector renderTasks) {
        if (!(bone instanceof CuboidGeoBone cuboid)) {
            return;
        }
        R renderState = renderPassInfo.renderState();
        Identifier texture = getTextureResource(renderState);
        RenderType renderType = getOutlineRenderType(renderState, texture);
        if (renderType == null) {
            return;
        }

        int packedLight = renderPassInfo.packedLight();
        int packedOverlay = renderPassInfo.packedOverlay();
        int renderColor = renderPassInfo.renderColor();

        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, buffer) -> {
            PoseStack poseStack = renderPassInfo.poseStack();
            poseStack.pushPose();
            poseStack.last().set(pose);
            bone.translateAwayFromPivotPoint(poseStack);
            for (GeoCube cube : cuboid.cubes) {
                poseStack.pushPose();
                cube.render(poseStack, buffer, packedLight, packedOverlay, renderColor);
                poseStack.popPose();
            }
            poseStack.popPose();
        });
    }

    private @Nullable RenderType getOutlineRenderType(R renderState, Identifier texture) {
        return MisakaTowerRenderTypes.outline(texture);
    }

    private static List<CuboidGeoBone> outlineBones(RenderPassInfo<?> renderPassInfo) {
        List<CuboidGeoBone> bones = new ArrayList<>();
        for (GeoBone bone : renderPassInfo.model().boneLookup().get().values()) {
            if (bone.name().endsWith(OUTLINE_SUFFIX) && bone instanceof CuboidGeoBone cuboid) {
                bones.add(cuboid);
            }
        }
        return bones;
    }
}
