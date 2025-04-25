package org.academy.api.client.renderer;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RendererManager {
    public static final Map<String, CameraRenderer> CAMERA_RENDERER_MAP = new HashMap<>();
    public static final List<EffectRenderer> EFFECT_RENDERER_MAP = new ArrayList<>();
    public static final Map<Item, ItemRenderer> ITEM_RENDERER_MAP = new HashMap<>();
}