package org.academy.internal.common.world.inventory;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class OmniCraftingMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots;
    private final Player player;

    public OmniCraftingMenu(int containerId, Inventory playerInventory, ContainerLevelAccess levelAccess, Container container) {
        super(MenuTypes.OMNI_CRAFTING_TABLE.get(), containerId);
        access = levelAccess;
        craftSlots = new TransientCraftingContainer(this, 3, 3);
        resultSlots = new ResultContainer();
        player = playerInventory.player;
        addSlot(new Slot(container, 0, 62, 59));
        addSlot(new ResultSlot(playerInventory.player, craftSlots, resultSlots, 0, 134, 29));

        for (var i = 0; i < 3; ++i) {
            for (var j = 0; j < 3; ++j) {
                addSlot(new Slot(craftSlots, j + i * 3, 62 + j * 18, -7 + i * 18));
            }
        }

        for (var k = 0; k < 3; ++k) {
            for (var i1 = 0; i1 < 9; ++i1) {
                addSlot(new Slot(playerInventory, i1 + k * 9 + 9, 8 + i1 * 18, 84 + k * 18));
            }
        }

        for (var l = 0; l < 9; ++l) {
            addSlot(new Slot(playerInventory, l, 8 + l * 18, 142));
        }
    }

    public OmniCraftingMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(1));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        var itemStack = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot.hasItem()) {
            var itemStack1 = slot.getItem();
            itemStack = itemStack1.copy();

            if (index == 1) {
               // this.access.execute((level, pos) -> itemStack1.getItem().onCraftedBy(itemStack1, level, player));
                if (!moveItemStackTo(itemStack1, 11, 47, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemStack1, itemStack);
            } else if (index >= 11 && index < 47) {
                if (!moveItemStackTo(itemStack1, 0, 1, false)) {
                    if (!moveItemStackTo(itemStack1, 2, 11, false)) {
                        if (index < 38) {
                            if (!moveItemStackTo(itemStack1, 38, 47, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else if (!moveItemStackTo(itemStack1, 11, 38, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            } else if (!moveItemStackTo(itemStack1, 11, 47, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemStack1.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemStack1);
            if (index == 1) {
                player.drop(itemStack1, false);
            }
        }
        return itemStack;
    }

    private static void slotChangedCraftingGrid(
            AbstractContainerMenu menu,
            Level level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots
    ) {
        if (!level.isClientSide()) {
            var craftinginput = craftSlots.asCraftInput();
            var serverplayer = (ServerPlayer) player;
            var itemstack = ItemStack.EMPTY;
            var optional = level.getServer()
                    .getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, craftinginput, level, (RecipeHolder<CraftingRecipe>) null);
            if (optional.isPresent()) {
                var recipeholder = optional.get();
                var craftingrecipe = recipeholder.value();
              /*  if (resultSlots.setRecipeUsed(level, serverplayer, recipeholder)) {
                    ItemStack itemstack1 = craftingrecipe.assemble(craftinginput, level.registryAccess());
                    if (itemstack1.isItemEnabled(level.enabledFeatures())) {
                        itemstack = itemstack1;
                    }
                }*/
            }

            resultSlots.setItem(0, itemstack);
            menu.setRemoteSlot(0, itemstack);
            serverplayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, itemstack));
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        access.execute((level, blockPos) -> clearContainer(player, craftSlots));
    }

    @Override
    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void slotsChanged(@NotNull Container inventory) {
        access.execute((p_344363_, p_344364_) -> slotChangedCraftingGrid(this, p_344363_, player, craftSlots, resultSlots));
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, Blocks.OMNI_CRAFTING_TABLE.get());
    }
}