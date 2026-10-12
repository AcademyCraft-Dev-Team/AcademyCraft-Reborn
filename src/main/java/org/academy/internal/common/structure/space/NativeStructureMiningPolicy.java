package org.academy.internal.common.structure.space;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/** Shared audited block/tool boundary. No world, actor or server state is retained. */
public final class NativeStructureMiningPolicy {
    private static final Set<Item> BASE_TOOLS = Set.of(
            Items.WOODEN_PICKAXE, Items.COPPER_PICKAXE, Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE,
            Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.WOODEN_SWORD, Items.COPPER_SWORD, Items.STONE_SWORD, Items.GOLDEN_SWORD,
            Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
    private static final Set<Item> AXES = Set.of(Items.WOODEN_AXE, Items.COPPER_AXE, Items.STONE_AXE,
            Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
    private static final Set<Item> SHOVELS = Set.of(Items.WOODEN_SHOVEL, Items.COPPER_SHOVEL, Items.STONE_SHOVEL,
            Items.GOLDEN_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL);
    private static final Set<Item> HOES = Set.of(Items.WOODEN_HOE, Items.COPPER_HOE, Items.STONE_HOE,
            Items.GOLDEN_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
    private static final Set<DataComponentType<?>> ALLOWED_PATCHES = Set.of(
            DataComponents.DAMAGE, DataComponents.ENCHANTMENTS, DataComponents.REPAIR_COST, DataComponents.UNBREAKABLE,
            DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME, DataComponents.LORE, DataComponents.RARITY,
            DataComponents.ITEM_MODEL, DataComponents.CUSTOM_MODEL_DATA, DataComponents.TOOLTIP_DISPLAY,
            DataComponents.TOOLTIP_STYLE, DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

    private NativeStructureMiningPolicy() {}

    /** Client selection uses state only; the authoritative path additionally checks the real BE. */
    public static boolean canMine(ItemStack tool, BlockState state) {
        return canUseTool(tool) && supportedState(state);
    }

    public static boolean canMine(BlockState state, @Nullable BlockEntity entity) {
        if (!supportedState(state)) return false;
        if (state.is(Blocks.CHEST)) {
            return entity != null && entity.getClass() == ChestBlockEntity.class
                    && !entity.isRemoved() && ((ChestBlockEntity) entity).getLootTable() == null;
        }
        return entity == null;
    }

    public static boolean canUseTool(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) return true;
        if (stack.getCount() != 1) return false;
        var item = stack.getItem();
        var type = item.getClass();
        boolean audited = BASE_TOOLS.contains(item) && type == Item.class
                || AXES.contains(item) && type == AxeItem.class
                || SHOVELS.contains(item) && type == ShovelItem.class
                || HOES.contains(item) && type == HoeItem.class
                || item == Items.SHEARS && type == ShearsItem.class;
        if (!audited) return false;
        // The original tool rules, durability limit and use semantics must remain intact.
        // Normal enchanting, repair, wear and presentation changes remain supported.
        for (var entry : stack.getComponentsPatch().entrySet()) {
            if (!ALLOWED_PATCHES.contains(entry.getKey())) return false;
        }
        return true;
    }

    private static boolean supportedState(BlockState state) {
        Objects.requireNonNull(state, "state");
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.CHEST)) {
            return state.getValue(ChestBlock.TYPE) == ChestType.SINGLE && !state.getValue(ChestBlock.WATERLOGGED);
        }
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.OAK_STAIRS) || state.is(Blocks.OAK_SLAB) || state.is(Blocks.LEVER)
                || state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.LAPIS_ORE);
    }
}
