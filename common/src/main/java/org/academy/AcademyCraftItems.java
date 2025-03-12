package org.academy;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.academy.internal.common.world.item.*;

import java.util.HashMap;
import java.util.Map;

public class AcademyCraftItems {
    public static final Map<ResourceLocation, Item> ITEMS = new HashMap<>();
    public static final AcademyCraftIconItem ACADEMY_CRAFT_ICON_ITEM = new AcademyCraftIconItem();
    public static final PortableDeveloperItem DEVELOPER_PORTABLE_ITEM = new PortableDeveloperItem();
    public static final DataTerminalItem DATA_TERMINAL_ITEM = new DataTerminalItem();
    public static final CoinItem COIN_ITEM = new CoinItem();
    public static final AbilityDeveloperComputationalChipItem ABILITY_DEVELOPER_COMPUTATIONAL_CHIP_ITEM = new AbilityDeveloperComputationalChipItem(new Item.Properties().stacksTo(1));

    static {
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "icon"), ACADEMY_CRAFT_ICON_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "portable_developer"), DEVELOPER_PORTABLE_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "data_terminal"), DATA_TERMINAL_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "coin"), COIN_ITEM);
        ITEMS.put(new ResourceLocation(AcademyCraft.MOD_ID, "ability_developer_computing_chip_item"), ABILITY_DEVELOPER_COMPUTATIONAL_CHIP_ITEM);
        init(ITEMS);
    }

    @ExpectPlatform
    public static void init(Map<ResourceLocation, Item> itemMap) {
        throw new AssertionError();
    }

    private AcademyCraftItems() {
    }
}
