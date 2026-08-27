package org.academy.internal.common.world.inventory;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.BedBlock;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;

public final class OmniCraftingMenu extends AbstractContainerMenu {
    public static final int SPECIAL_RECIPE_ENERGY = 4_000;
    public static final int SPECIAL_RECIPE_FLUID = 4_000;
    private static final int MACHINE_INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 1;
    private static final int CRAFT_START = 2;
    private static final int PLAYER_START = 11;
    private static final int PLAYER_END = 47;

    private final ContainerLevelAccess access;
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots;
    private final Player owner;
    private final OmniCraftingTableBlockEntity machine;

    public OmniCraftingMenu(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess levelAccess,
            Container container
    ) {
        super(MenuTypes.OMNI_CRAFTING_TABLE.get(), containerId);
        access = levelAccess;
        owner = playerInventory.player;
        machine = container instanceof OmniCraftingTableBlockEntity omni ? omni : null;
        craftSlots = new TransientCraftingContainer(this, 3, 3);
        resultSlots = new ResultContainer();

        addSlot(new Slot(container, 0, 62, 59) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.IMAG_PHASE_UNIT.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new OmniResultSlot(playerInventory.player, 134, 29));

        for (var row = 0; row < 3; ++row) {
            for (var column = 0; column < 3; ++column) {
                addSlot(new Slot(craftSlots, column + row * 3,
                        62 + column * 18, -7 + row * 18));
            }
        }

        for (var row = 0; row < 3; ++row) {
            for (var column = 0; column < 9; ++column) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }

        for (var column = 0; column < 9; ++column) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    public OmniCraftingMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(1));
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, _) -> {
            if (level instanceof ServerLevel serverLevel && owner instanceof ServerPlayer player) {
                updateCraftingResult(serverLevel, player);
            }
        });
    }

    private void updateCraftingResult(ServerLevel level, ServerPlayer player) {
        var result = ItemStack.EMPTY;
        resultSlots.setRecipeUsed(null);
        if (matchesAbilityDeveloperRecipe()) {
            result = new ItemStack(Items.ABILITY_DEVELOPER.get());
        } else {
            var input = craftSlots.asCraftInput();
            var recipe = level.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isPresent() && resultSlots.setRecipeUsed(player, recipe.get())) {
                var assembled = recipe.get().value().assemble(input);
                if (assembled.isItemEnabled(level.enabledFeatures())) result = assembled;
            }
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        player.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, incrementStateId(), RESULT_SLOT, result));
    }

    private boolean matchesAbilityDeveloperRecipe() {
        return is(0, net.minecraft.world.item.Items.STAINED_GLASS_PANE.blue())
                && is(1, net.minecraft.world.item.Items.STAINED_GLASS_PANE.blue())
                && is(2, Items.ABILITY_CONTROL_TABLET.get())
                && is(3, Items.WIND_GEN_BASE_SCREEN.get())
                && isBed(craftSlots.getItem(4))
                && is(5, Items.IMAG_PHASE_CIRCUIT.get())
                && is(6, Items.IMAG_PHASE_INGOT.get())
                && is(7, Items.IMAG_PHASE_INGOT.get())
                && is(8, Items.IMAG_PHASE_INGOT.get());
    }

    private boolean is(int slot, Item item) {
        return craftSlots.getItem(slot).is(item);
    }

    private static boolean isBed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BedBlock;
    }

    private boolean hasSpecialRecipeResources() {
        return machine != null && machine.hasCraftingResources(
                SPECIAL_RECIPE_ENERGY, SPECIAL_RECIPE_FLUID);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;

        var sourceStack = slot.getItem();
        var copiedStack = sourceStack.copy();

        if (!tryMoveStack(sourceStack, copiedStack, slot, index)) return ItemStack.EMPTY;

        if (sourceStack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (sourceStack.getCount() == copiedStack.getCount()) return ItemStack.EMPTY;

        slot.onTake(player, sourceStack);
        if (index == RESULT_SLOT) player.drop(sourceStack, false);
        return copiedStack;
    }

    private boolean tryMoveStack(ItemStack sourceStack, ItemStack copiedStack, Slot slot, int index) {
        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(sourceStack, PLAYER_START, PLAYER_END, true)) return false;
            slot.onQuickCraft(sourceStack, copiedStack);
            return true;
        }
        if (index >= PLAYER_START && index < PLAYER_END) {
            if (!moveItemStackTo(sourceStack, MACHINE_INPUT_SLOT, MACHINE_INPUT_SLOT + 1, false)
                    && !moveItemStackTo(sourceStack, CRAFT_START, PLAYER_START, false)) {
                if (index < 38) return moveItemStackTo(sourceStack, 38, PLAYER_END, false);
                return moveItemStackTo(sourceStack, PLAYER_START, 38, false);
            }
            return true;
        }
        return moveItemStackTo(sourceStack, PLAYER_START, PLAYER_END, false);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((_, _) -> clearContainer(player, craftSlots));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, Blocks.OMNI_CRAFTING_TABLE.get());
    }

    private final class OmniResultSlot extends ResultSlot {
        private OmniResultSlot(Player player, int x, int y) {
            super(player, craftSlots, resultSlots, 0, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (!matchesAbilityDeveloperRecipe()) return super.mayPickup(player);
            // Client menus do not own the block entity; the server repeats this check authoritatively.
            return machine == null || hasSpecialRecipeResources();
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            if (!matchesAbilityDeveloperRecipe()) {
                super.onTake(player, carried);
                return;
            }
            if (machine != null && !machine.consumeCraftingResources(
                    SPECIAL_RECIPE_ENERGY, SPECIAL_RECIPE_FLUID)) return;
            checkTakeAchievements(carried);
            for (var slot = 0; slot < craftSlots.getContainerSize(); slot++) {
                craftSlots.removeItem(slot, 1);
            }
            slotsChanged(craftSlots);
        }
    }
}
