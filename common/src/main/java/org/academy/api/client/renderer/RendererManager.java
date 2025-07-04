package org.academy.api.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RendererManager {
    private static boolean initialized = false;
    private static final List<CameraRenderer> CAMERA_RENDERERS = new ArrayList<>();
    private static final List<EffectRenderer> EFFECT_RENDERERS = new ArrayList<>();
    private static final Map<Item, ItemRenderer> ITEM_RENDERER_MAP = new HashMap<>();

    public static void init() {
        initialized = true;
    }

    public static void registerCameraRenderer(CameraRenderer renderer) {
        if (!initialized) {
            CAMERA_RENDERERS.add(renderer);
        }
    }

    public static void registerEffectRenderer(EffectRenderer renderer) {
        if (!initialized) {
            EFFECT_RENDERERS.add(renderer);
        }
    }

    public static void registerItemRenderer(Item item, ItemRenderer renderer) {
        if (!initialized) {
            ITEM_RENDERER_MAP.put(item, renderer);
        }
    }

    public static boolean handleItemRender(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        var item = itemStack.getItem();
        if (ITEM_RENDERER_MAP.containsKey(item)) {
            ITEM_RENDERER_MAP.get(item).render(itemStack, displayContext, leftHand, poseStack, buffer, combinedLight, combinedOverlay, model);
            return true;
        } else {
            return false;
        }
    }

    public static void renderEffect(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        for (var renderer : EFFECT_RENDERERS) {
            renderer.render(poseStack, buffer, packedLight, livingEntity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        }
    }

    public static void renderCamera(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix) {
        for (var cameraRenderer : CAMERA_RENDERERS) {
            cameraRenderer.render(poseStack, partialTick, finishNanoTime, renderBlockOutline, camera, gameRenderer, lightTexture, projectionMatrix);
        }
    }
}