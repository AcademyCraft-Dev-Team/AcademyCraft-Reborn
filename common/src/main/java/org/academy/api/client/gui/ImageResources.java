package org.academy.api.client.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;

import java.util.function.BiFunction;

import static org.academy.api.client.resource.TextureResources.*;

public final class ImageResources {
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
        public static final RenderType RENDER_TYPE_WIND_GEN_UI = RenderUtil.getPositionColorTexRenderTypeFull(
                "windgen_ui", TEXTURE_WIND_GEN_UI, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_BASE = RenderUtil.getPositionColorTexRenderTypeFull(
                "icon_wind_gen_base", TEXTURE_ICON_WIND_GEN_BASE, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_PILLAR = RenderUtil.getPositionColorTexRenderTypeFull(
                "icon_wind_gen_pillar", TEXTURE_ICON_WIND_GEN_PILLAR, false
        );
        public static final RenderType RENDER_TYPE_ICON_WIND_GEN_TOP = RenderUtil.getPositionColorTexRenderTypeFull(
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
        public static final RenderType RENDER_TYPE_HISTOGRAM = RenderUtil.getPositionColorTexRenderType(
                "histogram", TEXTURE_HISTOGRAM, false
        );
        public static final RenderType RENDER_TYPE_BLEND_QUAD = RenderUtil.getPositionColorTexRenderTypeFull(
                "blend_quad", TEXTURE_BLEND_QUAD, true
        );
    }

    private ImageResources() {
    }
}