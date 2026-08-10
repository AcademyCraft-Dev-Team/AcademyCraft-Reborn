package org.academy.internal.client.ability.mentalout;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;

/**
 * Draws the controlled player's combat HUD without mutating the controller's inventory.
 */
public final class ControlledPlayerHudRenderer {
    private static final Identifier HOTBAR = vanilla("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = vanilla("hud/hotbar_selection");
    private static final Identifier OFFHAND_LEFT = vanilla("hud/hotbar_offhand_left");
    private static final Identifier OFFHAND_RIGHT = vanilla("hud/hotbar_offhand_right");
    private static final Identifier ATTACK_BACKGROUND = vanilla("hud/hotbar_attack_indicator_background");
    private static final Identifier ATTACK_PROGRESS = vanilla("hud/hotbar_attack_indicator_progress");
    private static final Identifier HEART_CONTAINER = vanilla("hud/heart/container");
    private static final Identifier HEART_FULL = vanilla("hud/heart/full");
    private static final Identifier HEART_HALF = vanilla("hud/heart/half");
    private static final Identifier ABSORPTION_FULL = vanilla("hud/heart/absorbing_full");
    private static final Identifier ABSORPTION_HALF = vanilla("hud/heart/absorbing_half");
    private static final Identifier ARMOR_EMPTY = vanilla("hud/armor_empty");
    private static final Identifier ARMOR_HALF = vanilla("hud/armor_half");
    private static final Identifier ARMOR_FULL = vanilla("hud/armor_full");
    private static final Identifier FOOD_EMPTY = vanilla("hud/food_empty");
    private static final Identifier FOOD_HALF = vanilla("hud/food_half");
    private static final Identifier FOOD_FULL = vanilla("hud/food_full");
    private static final Identifier AIR = vanilla("hud/air");
    private static final Identifier AIR_EMPTY = vanilla("hud/air_empty");

    private ControlledPlayerHudRenderer() {
    }

    public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var state = PlayerControlClientState.targetViewState();
        if (state == null) return;
        renderHotbar(graphics, state);
        renderStatus(graphics, state);
    }

    private static void renderHotbar(
            GuiGraphicsExtractor graphics,
            PlayerControlSessionManager.TargetViewState state
    ) {
        var center = graphics.guiWidth() / 2;
        var bottom = graphics.guiHeight();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, center - 91, bottom - 22, 182, 22);
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                HOTBAR_SELECTION,
                center - 92 + state.selectedSlot() * 20,
                bottom - 23,
                24,
                23
        );

        var entity = PlayerControlClientState.controlledViewEntity();
        var living = entity instanceof LivingEntity value ? value : null;
        var mainArm = entity instanceof Player player ? player.getMainArm() : HumanoidArm.RIGHT;
        var offhandArm = mainArm.getOpposite();
        if (!state.offhand().isEmpty()) {
            if (offhandArm == HumanoidArm.LEFT) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND_LEFT, center - 120, bottom - 23, 29, 24);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND_RIGHT, center + 91, bottom - 23, 29, 24);
            }
        }

        var seed = 1;
        for (var slot = 0; slot < 9; slot++) {
            var x = center - 88 + slot * 20;
            var y = bottom - 19;
            renderItem(graphics, living, state.hotbar().get(slot), x, y, seed++);
        }
        if (!state.offhand().isEmpty()) {
            var x = offhandArm == HumanoidArm.LEFT ? center - 117 : center + 101;
            renderItem(graphics, living, state.offhand(), x, bottom - 19, seed);
        }

        if (state.attackStrength() < 1.0f) {
            var x = offhandArm == HumanoidArm.RIGHT ? center - 113 : center + 97;
            var y = bottom - 20;
            var progress = Mth.clamp((int) (state.attackStrength() * 19.0f), 0, 18);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ATTACK_BACKGROUND, x, y, 18, 18);
            if (progress > 0) {
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        ATTACK_PROGRESS,
                        18,
                        18,
                        0,
                        18 - progress,
                        x,
                        y + 18 - progress,
                        18,
                        progress
                );
            }
        }
    }

    private static void renderItem(
            GuiGraphicsExtractor graphics,
            LivingEntity entity,
            ItemStack stack,
            int x,
            int y,
            int seed
    ) {
        if (stack.isEmpty()) return;
        if (entity == null) graphics.item(stack, x, y, seed);
        else graphics.item(entity, stack, x, y, seed);
        graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y);
    }

    private static void renderStatus(
            GuiGraphicsExtractor graphics,
            PlayerControlSessionManager.TargetViewState state
    ) {
        var xLeft = graphics.guiWidth() / 2 - 91;
        var xRight = graphics.guiWidth() / 2 + 91;
        var yBase = graphics.guiHeight() - 39;
        var healthHalves = Mth.ceil(state.health());
        var maxHealthHalves = Math.max(1, Mth.ceil(state.maxHealth()));
        var absorptionHalves = Mth.ceil(state.absorption());
        var healthContainers = Mth.ceil(maxHealthHalves / 2.0f);
        var absorptionContainers = Mth.ceil(absorptionHalves / 2.0f);
        var containers = healthContainers + absorptionContainers;
        var rows = Math.max(1, Mth.ceil(containers / 10.0f));
        var rowHeight = Math.max(10 - (rows - 2), 3);

        for (var index = containers - 1; index >= 0; index--) {
            var x = xLeft + index % 10 * 8;
            var y = yBase - index / 10 * rowHeight;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, x, y, 9, 9);
            var halfIndex = index * 2;
            if (halfIndex < maxHealthHalves) {
                if (halfIndex + 1 < healthHalves) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, x, y, 9, 9);
                } else if (halfIndex < healthHalves) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, x, y, 9, 9);
                }
            } else if (index >= healthContainers) {
                var absorptionIndex = halfIndex - healthContainers * 2;
                if (absorptionIndex + 1 < absorptionHalves) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ABSORPTION_FULL, x, y, 9, 9);
                } else if (absorptionIndex < absorptionHalves) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ABSORPTION_HALF, x, y, 9, 9);
                }
            }
        }

        var armorY = yBase - rows * 10;
        for (var index = 0; index < 10; index++) {
            var value = index * 2 + 1;
            var sprite = value < state.armor() ? ARMOR_FULL
                    : value == state.armor() ? ARMOR_HALF : ARMOR_EMPTY;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, xLeft + index * 8, armorY, 9, 9);
        }

        for (var index = 0; index < 10; index++) {
            var value = index * 2 + 1;
            var sprite = value < state.food() ? FOOD_FULL
                    : value == state.food() ? FOOD_HALF : FOOD_EMPTY;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, xRight - index * 8 - 9, yBase, 9, 9);
        }

        if (state.air() < state.maxAir()) {
            var full = Mth.ceil(state.air() * 10.0f / state.maxAir());
            for (var index = 0; index < 10; index++) {
                var sprite = index < full ? AIR : AIR_EMPTY;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, xRight - index * 8 - 9, yBase - 10, 9, 9);
            }
        }

        var xpY = graphics.guiHeight() - 29;
        graphics.fill(xLeft, xpY, xLeft + 182, xpY + 5, 0xB0000000);
        graphics.fill(xLeft + 1, xpY + 1,
                xLeft + 1 + Math.round(180 * state.experienceProgress()), xpY + 4, 0xFF80C71F);
        if (state.experienceLevel() > 0) {
            graphics.centeredText(
                    Minecraft.getInstance().font,
                    Integer.toString(state.experienceLevel()),
                    graphics.guiWidth() / 2,
                    graphics.guiHeight() - 36,
                    0xFF80FF20
            );
        }
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
