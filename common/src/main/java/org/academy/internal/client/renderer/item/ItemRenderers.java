package org.academy.internal.client.renderer.item;

import net.minecraft.world.item.Item;
import org.academy.api.client.renderer.ItemRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.internal.common.world.item.Items;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class ItemRenderers {
    public static final Map<Item, ItemRenderer> ITEM_RENDERER_MAP = new HashMap<>();

    public static final ItemRenderer WIRELESS_NODE =
            register(Items.WIRELESS_NODE_BLOCK_ITEM, new WirelessNodeBlockItemRenderer());

    public static ItemRenderer register(Item item, ItemRenderer itemRenderer) {
        ITEM_RENDERER_MAP.put(item, itemRenderer);
        return itemRenderer;
    }

    public static void init() {
        for (Item item : ITEM_RENDERER_MAP.keySet()) {
            RendererManager.registerItemRenderer(item, ITEM_RENDERER_MAP.get(item));
        }
    }

    private ItemRenderers() {
    }
}