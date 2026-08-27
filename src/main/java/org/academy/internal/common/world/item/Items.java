package org.academy.internal.common.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.internal.common.world.level.block.Blocks;

import static org.academy.AcademyCraft.MODID;

public final class Items {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredHolder<Item, Item> ICON =
            ITEMS.registerItem("icon", Item::new);
    public static final DeferredHolder<Item, Item> DARKMATTER =
            ITEMS.registerItem("darkmatter", Item::new);
    public static final DeferredHolder<Item, DarkmatterCoatingItem> DARKMATTER_COATING =
            ITEMS.registerItem("darkmatter_coating", DarkmatterCoatingItem::new);
    public static final DeferredHolder<Item, DarkmatterBlockItem> DARKMATTER_BLOCK =
            ITEMS.registerItem("darkmatter_block", properties -> new DarkmatterBlockItem(
                    Blocks.DARKMATTER_BLOCK.get(), properties));
    public static final DeferredHolder<Item, DarkmatterToolItem> DARKMATTER_TOOL =
            ITEMS.registerItem("darkmatter_tool", properties -> new DarkmatterToolItem(
                    properties.tool(
                            ToolMaterial.NETHERITE,
                            DarkmatterToolItem.EFFECTIVE_BLOCKS,
                            8.0f,
                            -2.4f,
                            0.0f
                    )
            ));
    public static final DeferredHolder<Item, DarkmatterSwordItem> DARKMATTER_SWORD =
            ITEMS.registerItem("darkmatter_sword", properties -> new DarkmatterSwordItem(
                    properties.sword(ToolMaterial.NETHERITE, 5.0f, -2.4f)));
    public static final DeferredHolder<Item, DarkmatterSpearItem> DARKMATTER_SPEAR =
            ITEMS.registerItem("darkmatter_spear", properties -> new DarkmatterSpearItem(
                    properties.spear(
                            ToolMaterial.NETHERITE,
                            0.65f,
                            0.7f,
                            0.75f,
                            5.0f,
                            14.0f,
                            10.0f,
                            5.1f,
                            15.0f,
                            4.6f
                    )));
    public static final DeferredHolder<Item, DarkmatterTridentItem> DARKMATTER_TRIDENT =
            ITEMS.registerItem("darkmatter_trident", properties -> new DarkmatterTridentItem(
                    properties.attributes(TridentItem.createAttributes())
                            .component(DataComponents.TOOL, TridentItem.createToolProperties())));
    public static final DeferredHolder<Item, DarkmatterBowItem> DARKMATTER_BOW =
            ITEMS.registerItem("darkmatter_bow", DarkmatterBowItem::new);
    public static final DeferredHolder<Item, DarkmatterCrossbowItem> DARKMATTER_CROSSBOW =
            ITEMS.registerItem("darkmatter_crossbow", DarkmatterCrossbowItem::new);
    public static final DeferredHolder<Item, DarkmatterMaceItem> DARKMATTER_MACE =
            ITEMS.registerItem("darkmatter_mace", properties -> new DarkmatterMaceItem(
                    properties.attributes(MaceItem.createAttributes())
                            .component(DataComponents.TOOL, MaceItem.createToolProperties())));
    public static final DeferredHolder<Item, DarkmatterArrowItem> DARKMATTER_ARROW =
            ITEMS.registerItem("darkmatter_arrow", DarkmatterArrowItem::new);
    public static final DeferredHolder<Item, Item> DARKMATTER_FEATHER =
            ITEMS.registerItem("darkmatter_feather", Item::new);
    public static final DeferredHolder<Item, Item> IMAG_PHASE_INGOT =
            ITEMS.registerItem("imag_phase_ingot", Item::new);
    public static final DeferredHolder<Item, Item> IMAG_PHASE_CRYSTAL =
            ITEMS.registerItem("imag_phase_crystal", Item::new);
    public static final DeferredHolder<Item, Item> IMAG_PHASE_POLYMER =
            ITEMS.registerItem("imag_phase_polymer", Item::new);
    public static final DeferredHolder<Item, Item> IMAG_PHASE_PLATE =
            ITEMS.registerItem("imag_phase_plate", Item::new);
    public static final DeferredHolder<Item, Item> IMAG_PHASE_CIRCUIT =
            ITEMS.registerItem("imag_phase_circuit", Item::new);
    public static final DeferredHolder<Item, Item> NEEDLE =
            ITEMS.registerItem("needle", Item::new);
    public static final DeferredHolder<Item, Item> WIND_GEN_BASE_SCREEN =
            ITEMS.registerItem("wind_gen_base_screen", Item::new);
    public static final DeferredHolder<Item, ImagPhaseDowsingRodItem> IMAG_PHASE_DOWSING_ROD =
            ITEMS.registerItem("imag_phase_dowsing_rod", ImagPhaseDowsingRodItem::new);
    public static final DeferredHolder<Item, DarkmatterEquipmentItem> DARK_MATTER_HELMET =
            ITEMS.registerItem("dark_matter_helmet", properties -> new DarkmatterEquipmentItem(
                    properties.humanoidArmor(DarkmatterArmorMaterial.INSTANCE, ArmorType.HELMET)));
    public static final DeferredHolder<Item, DarkmatterEquipmentItem> DARK_MATTER_CHESTPLATE =
            ITEMS.registerItem("dark_matter_chestplate", properties -> new DarkmatterEquipmentItem(
                    properties.humanoidArmor(DarkmatterArmorMaterial.INSTANCE, ArmorType.CHESTPLATE)));
    public static final DeferredHolder<Item, DarkmatterEquipmentItem> DARK_MATTER_LEGGINGS =
            ITEMS.registerItem("dark_matter_leggings", properties -> new DarkmatterEquipmentItem(
                    properties.humanoidArmor(DarkmatterArmorMaterial.INSTANCE, ArmorType.LEGGINGS)));
    public static final DeferredHolder<Item, DarkmatterEquipmentItem> DARK_MATTER_BOOTS =
            ITEMS.registerItem("dark_matter_boots", properties -> new DarkmatterEquipmentItem(
                    properties.humanoidArmor(DarkmatterArmorMaterial.INSTANCE, ArmorType.BOOTS)));
    public static final DeferredHolder<Item, DataTerminalItem> DATA_TERMINAL =
            ITEMS.registerItem("data_terminal", DataTerminalItem::new);
    public static final DeferredHolder<Item, TutorialItem> TUTORIAL =
            ITEMS.registerItem("tutorial", TutorialItem::new);
    public static final DeferredHolder<Item, AbilityControlTabletItem> ABILITY_CONTROL_TABLET =
            ITEMS.registerItem("ability_control_tablet", AbilityControlTabletItem::new);
    public static final DeferredHolder<Item, CoinItem> COIN =
            ITEMS.registerItem("coin", CoinItem::new);
    public static final DeferredHolder<Item, PaperAirplaneItem> PAPER_AIRPLANE =
            ITEMS.registerItem("paper_airplane", properties -> new PaperAirplaneItem(properties.stacksTo(64)));
    public static final DeferredHolder<Item, BlockItem> WIRELESS_NODE =
            ITEMS.registerSimpleBlockItem("wireless_node", Blocks.WIRELESS_NODE);
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_BASE =
            ITEMS.registerSimpleBlockItem("wind_gen_base", Blocks.WIND_GEN_BASE);
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_TOP =
            ITEMS.registerSimpleBlockItem("wind_gen_top", Blocks.WIND_GEN_TOP);
    public static final DeferredHolder<Item, BlockItem> WIND_GEN_PILLAR =
            ITEMS.registerSimpleBlockItem("wind_gen_pillar", Blocks.WIND_GEN_PILLAR);
    public static final DeferredHolder<Item, MultiBlockItem> ABILITY_DEVELOPER =
            ITEMS.registerItem("ability_developer",
                    properties -> new MultiBlockItem(Blocks.ABILITY_DEVELOPER.get(), properties)
            );
    public static final DeferredHolder<Item, Item> WIND_GEN_FAN_ITEM =
            ITEMS.registerItem("wind_gen_fan",
                    properties -> new Item(properties.stacksTo(16))
            );
    public static final DeferredHolder<Item, MultiBlockItem> OMNI_CRAFTING_TABLE =
            ITEMS.registerItem("omni_crafting_table",
                    properties -> new MultiBlockItem(Blocks.OMNI_CRAFTING_TABLE.get(), properties)
            );
    public static final DeferredHolder<Item, BlockItem> CAT_ENGINE =
            ITEMS.registerSimpleBlockItem("cat_engine", Blocks.CAT_ENGINE);
    public static final DeferredHolder<Item, BlockItem> SOLAR_GEN =
            ITEMS.registerSimpleBlockItem("solar_gen", Blocks.SOLAR_GEN);
    public static final DeferredHolder<Item, BlockItem> IMAG_PHASE_VEGETATION =
            ITEMS.registerSimpleBlockItem("imag_phase_vegetation", Blocks.IMAG_PHASE_VEGETATION);
    public static final DeferredHolder<Item, BlockItem> IMAG_PHASE_LEAVES =
            ITEMS.registerSimpleBlockItem("imag_phase_leaves", Blocks.IMAG_PHASE_LEAVES);
    public static final DeferredHolder<Item, BlockItem> IMAG_PHASE_LOG =
            ITEMS.registerSimpleBlockItem("imag_phase_log", Blocks.IMAG_PHASE_LOG);
    public static final DeferredHolder<Item, BlockItem> IMAG_PHASE_LICHEN =
            ITEMS.registerSimpleBlockItem("imag_phase_lichen", Blocks.IMAG_PHASE_LICHEN);
    public static final DeferredHolder<Item, EmptyUnitItem> EMPTY_UNIT =
            ITEMS.registerItem("empty_unit", EmptyUnitItem::new);
    public static final DeferredHolder<Item, ImagPhaseUnitItem> IMAG_PHASE_UNIT =
            ITEMS.registerItem("imag_phase_unit", ImagPhaseUnitItem::new);

    private Items() {
    }
}
