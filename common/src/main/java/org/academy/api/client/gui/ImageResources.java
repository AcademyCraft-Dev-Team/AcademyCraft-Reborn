package org.academy.api.client.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;

import java.util.function.BiFunction;

import static org.academy.api.client.gui.ImageResources.Textures.*;

public final class ImageResources {
    public static final class Textures {
        /**
         * Ability Developer
         */
        public static final ResourceLocation PANEL_LEFT_BACK_TOP_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerleft.png");
        public static final ResourceLocation TEXTURE_PANEL_LEFT_BACK_MIDDLE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developermachine.png");
        public static final ResourceLocation TEXTURE_PANEL_RIGHT_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developerright.png");
        public static final ResourceLocation TEXTURE_PANEL_RIGHT_INFO = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerright.png");
        public static final ResourceLocation TEXTURE_PANEL_RIGHT_SKILL_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_panel_back.png");
        public static final ResourceLocation TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_back.png");
        /**
         * Wind Gen
         */
        public static final ResourceLocation TEXTURE_WIND_GEN_UI = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/windgen/ui_windgen.png");
        public static final ResourceLocation TEXTURE_ICON_WIND_GEN_BASE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wind_base.png");
        public static final ResourceLocation TEXTURE_ICON_WIND_GEN_PILLAR = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wind_pillar.png");
        public static final ResourceLocation TEXTURE_ICON_WIND_GEN_TOP = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wind_top.png");
        /**
         * Wireless Node
         */
        public static final ResourceLocation TEXTURE_WIRELESS_NODE_UI = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/node/ui_node.png");
        public static final ResourceLocation TEXTURE_WIRELESS_NODE_STATE = new ResourceLocation(AcademyCraft.MOD_ID,"textures/gui/node/state_node.png");
        /**
         * Common
         */
        public static final ResourceLocation TEXTURE_INVENTORY = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/ui_inventory.png");
        public static final ResourceLocation TEXTURE_ELEMENT_LINE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/line.png");
        public static final ResourceLocation TEXTURE_ELEMENT_BACK_DARK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_dark.png");
        public static final ResourceLocation TEXTURE_ELEMENT_BACK_LIGHT = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_light.png");
        public static final ResourceLocation TEXTURE_WIRELESS_PANEL_VIEW_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_tonode.png");
        public static final ResourceLocation TEXTURE_ICON_NODE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_node.png");
        public static final ResourceLocation TEXTURE_ICON_CONNECTED = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_connected.png");
        public static final ResourceLocation TEXTURE_ICON_UNCONNECTED = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_unconnected.png");
        public static final ResourceLocation TEXTURE_ICON_INV = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_inv.png");
        public static final ResourceLocation TEXTURE_ICON_WIRELESS = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_wireless.png");
    }

    public static final class RenderTypes {
        /**
         * Ability Developer
         */
        public static final BiFunction<String, ResourceLocation, RenderType> RENDER_TYPE_SKILL_ICON =
                (string, resourceLocation) ->
                        RenderUtil.getPositionColorTexRenderType(string, resourceLocation, false);
        public static final RenderType DEBUG = new RenderType.CompositeRenderType(
                "debug_aaa",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                64,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateUtil.DEBUG)
                        .setCullState(RenderStateUtil.NO_CULL)
                        .setWriteMaskState(RenderStateUtil.COLOR_WRITE)
                        .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_TOP = RenderUtil.getPositionColorTexRenderType(
                "panel_left_back_top", PANEL_LEFT_BACK_TOP_TEXTURE, false);
        public static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_MIDDLE = RenderUtil.getPositionColorTexRenderType(
                "panel_left_back_middle", TEXTURE_PANEL_LEFT_BACK_MIDDLE, false);
        public static final RenderType RENDER_TYPE_SKILL_PANEL_BACK = RenderUtil.getPositionTexRenderType(
                "skill_panel_back", TEXTURE_PANEL_RIGHT_SKILL_BACK, true);
        public static final RenderType RENDER_TYPE_SKILL_PANEL_INFO = RenderUtil.getPositionColorTexRenderType(
                "skill_panel_info", TEXTURE_PANEL_RIGHT_INFO, false);
        public static final RenderType RENDER_TYPE_PANEL_RIGHT_BACK = RenderUtil.getPositionColorTexRenderType(
                "panel_right_back", TEXTURE_PANEL_RIGHT_BACK, false);
        public static final RenderType RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK = RenderUtil.getPositionColorTexRenderType(
                "panel_right_skill_icon_back", TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK, false);
        /**
         * WindGen
         */
        public static final RenderType RENDER_TYPE_WIND_GEN_UI = RenderUtil.getPositionColorTexRenderType(
                "windgen_ui", TEXTURE_WIND_GEN_UI, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_BASE = RenderUtil.getPositionColorTexRenderType(
                "icon_wind_gen_base", TEXTURE_ICON_WIND_GEN_BASE, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_PILLAR = RenderUtil.getPositionColorTexRenderType(
                "icon_wind_gen_pillar", TEXTURE_ICON_WIND_GEN_PILLAR, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_TOP = RenderUtil.getPositionColorTexRenderType(
                "icon_wind_gen_top", TEXTURE_ICON_WIND_GEN_TOP, false
        );
        /**
         * Wireless Node
         */
        public static final RenderType RENDER_TYPE_WIRELESS_NODE_UI = RenderUtil.getPositionColorTexRenderType(
                "wireless_node_ui", TEXTURE_WIRELESS_NODE_UI, false
        );
        public static final RenderType RENDER_TYPE_WIRELESS_NODE_STATE = RenderUtil.getPositionTexRenderType(
                "wireless_node_state", TEXTURE_WIRELESS_NODE_STATE, false
        );
        /**
         * Common
         */
        public static final RenderType RENDER_TYPE_INVENTORY = RenderUtil.getPositionColorTexRenderType(
                "inventory", TEXTURE_INVENTORY, false
        );
        public static final RenderType RENDER_TYPE_ELEMENT_LINE = RenderUtil.getPositionColorTexRenderType(
                "element_line", TEXTURE_ELEMENT_LINE, true);
        public static final RenderType RENDER_TYPE_ELEMENT_BACK_DARK = RenderUtil.getPositionColorTexRenderType(
                "element_back_dark", TEXTURE_ELEMENT_BACK_DARK, false);
        public static final RenderType RENDER_TYPE_ELEMENT_BACK_LIGHT = RenderUtil.getPositionColorTexRenderType(
                "element_back_light", TEXTURE_ELEMENT_BACK_LIGHT, false);
        public static final RenderType RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON = RenderUtil.getPositionColorTexRenderType(
                "wireless_panel_view_icon", TEXTURE_WIRELESS_PANEL_VIEW_ICON, false);
        public static final RenderType RENDER_TYPE_ICON_NODE = RenderUtil.getPositionColorTexRenderType(
                "icon_node", TEXTURE_ICON_NODE, false);
        public static final RenderType RENDER_TYPE_ICON_CONNECTED = RenderUtil.getPositionColorTexRenderType(
                "icon_connected", TEXTURE_ICON_CONNECTED, false);
        public static final RenderType RENDER_TYPE_ICON_UNCONNECTED = RenderUtil.getPositionColorTexRenderType(
                "icon_unconnected", TEXTURE_ICON_UNCONNECTED, false);
        public static final RenderType RENDER_TYPE_ICON_INV = RenderUtil.getPositionColorTexRenderType(
                "icon_inv", TEXTURE_ICON_INV, false);
        public static final RenderType RENDER_TYPE_ICON_WIRELESS = RenderUtil.getPositionColorTexRenderType(
                "icon_wireless", TEXTURE_ICON_WIRELESS, false);
    }

    private ImageResources() {
    }
}