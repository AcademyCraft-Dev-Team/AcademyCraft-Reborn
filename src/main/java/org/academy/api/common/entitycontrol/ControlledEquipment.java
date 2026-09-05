package org.academy.api.common.entitycontrol;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Virtual iron equipment for controlled non-player entities; never inserts or drops items. */
public final class ControlledEquipment {
    private static final Identifier ARMOR = Identifier.fromNamespaceAndPath("academy", "control_default_armor");
    private static final Identifier WEAPON = Identifier.fromNamespaceAndPath("academy", "control_default_weapon");

    private ControlledEquipment() {}

    /** Returns the actual held stack when present; callers must not consume a virtual stack. */
    public static ItemStack tool(LivingEntity subject, Item fallback) {
        var held = subject.getMainHandItem();
        return held.isEmpty() ? new ItemStack(fallback) : held;
    }

    /** Selects the fastest suitable iron tool, retaining the iron harvesting tier. */
    public static ItemStack miningTool(LivingEntity subject, BlockState state) {
        if (!subject.getMainHandItem().isEmpty()) return subject.getMainHandItem();
        var best = new ItemStack(Items.IRON_PICKAXE);
        for (var item : new Item[]{Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE}) {
            var candidate = new ItemStack(item);
            if (candidate.getDestroySpeed(state) > best.getDestroySpeed(state)
                    && (!state.requiresCorrectToolForDrops() || candidate.isCorrectToolForDrops(state))) best = candidate;
        }
        return best;
    }

    public static void damageRealTool(LivingEntity subject, ItemStack effectiveTool, int damage) {
        if (!effectiveTool.isEmpty() && effectiveTool == subject.getMainHandItem()) {
            effectiveTool.hurtAndBreak(damage, subject, EquipmentSlot.MAINHAND);
        }
    }

    /** Refresh on the server while AI takeover is active; existing equipment takes precedence per slot. */
    public static void refresh(Mob subject) {
        int armor = 0;
        if (subject.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) armor += 2;
        if (subject.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) armor += 6;
        if (subject.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) armor += 5;
        if (subject.getItemBySlot(EquipmentSlot.FEET).isEmpty()) armor += 2;
        update(subject, Attributes.ARMOR, ARMOR, armor);
        update(subject, Attributes.ATTACK_DAMAGE, WEAPON, subject.getMainHandItem().isEmpty() ? 5 : 0);
    }

    public static void clear(Mob subject) {
        update(subject, Attributes.ARMOR, ARMOR, 0);
        update(subject, Attributes.ATTACK_DAMAGE, WEAPON, 0);
    }

    /** Melee fallback for passive mobs without the vanilla attack-damage attribute. */
    public static boolean attackWithoutAttribute(Mob subject, ServerLevel level, LivingEntity target) {
        var hit = target.hurtServer(level, subject.damageSources().mobAttack(subject), 6.0F);
        if (hit) subject.setLastHurtMob(target);
        return hit;
    }

    private static void update(Mob subject, Holder<Attribute> attribute, Identifier id, double amount) {
        var instance = subject.getAttribute(attribute);
        if (instance == null) return;
        var previous = instance.getModifier(id);
        if (previous != null && previous.amount() == amount) return;
        if (previous != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
