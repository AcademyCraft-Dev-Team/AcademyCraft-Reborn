package org.academy.internal.common.world.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.AcademyCraft;
import org.academy.api.client.renderer.ItemStackRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.internal.client.models.WindGenBaseModel;
import org.academy.internal.client.renderer.blockentity.WindGenBaseBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.client.models.AbilityDeveloperBlockEntityModel;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class Items {
    public static final Map<ResourceLocation, Item> ITEMS = new HashMap<>();
    public static final Item ACADEMY_CRAFT_ICON_ITEM = new AcademyCraftIconItem();
    public static final Item DEVELOPER_PORTABLE_ITEM = new PortableDeveloperItem();
    public static final Item DATA_TERMINAL_ITEM = new DataTerminalItem();
    public static final Item COIN_ITEM = new CoinItem();
    public static final Item ADVANCED_WIRELESS_NODE_BLOCK_ITEM = new AdvancedWirelessNodeBlockItem();
    public static final Item WIND_GEN_BASE_BLOCK_ITEM = new WindGenBaseBlockItem();

    static {
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "icon"), ACADEMY_CRAFT_ICON_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "portable_developer"), DEVELOPER_PORTABLE_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "data_terminal"), DATA_TERMINAL_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "coin"), COIN_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "advanced_wireless_node_block"), ADVANCED_WIRELESS_NODE_BLOCK_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "wind_gen_base_block"), WIND_GEN_BASE_BLOCK_ITEM);
        RendererManager.ITEM_STACK_RENDERER_MAP.put(WIND_GEN_BASE_BLOCK_ITEM, new ItemStackRenderer() {
            @Override
            public void render(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
                poseStack.pushPose();
                poseStack.last().normal().rotateX((float) Math.toRadians(180));
                Matrix4f matrix4f = new Matrix4f();
                matrix4f.scale(0.5f);
                matrix4f.rotateX((float) Math.toRadians(180));
                matrix4f.rotateY((float) Math.toRadians(180));
                matrix4f.translate(0, -0.75f, 0);
                if (displayContext.firstPerson()){
                    matrix4f.scale(0.5f);
                    matrix4f.translate(0.5f, -0.5f, 0);
                }
                poseStack.mulPoseMatrix(matrix4f);
                VertexConsumer vertexConsumer = buffer.getBuffer(WindGenBaseBlockEntityRenderer.MODEL.renderType(WindGenBaseModel.TEXTURE));
                WindGenBaseBlockEntityRenderer.MODEL.renderToBuffer(poseStack, vertexConsumer, combinedLight, combinedOverlay, 1, 1, 1, 1);
                poseStack.popPose();
            }
        });
    }

    private Items() {
    }
}