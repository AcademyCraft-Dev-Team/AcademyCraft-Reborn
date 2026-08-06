package org.academy.internal.common.attribute;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.internal.common.world.damagesource.DamageTypes;

import java.util.ArrayDeque;
import java.util.Deque;

/** Applies the logarithmic bonuses derived from AcademyCraft's player attributes. */
@EventBusSubscriber
public final class PlayerAttributeRuntime {
    private static final Identifier MUSCLE_DAMAGE = AcademyCraft.academy("attribute_bonus.muscle_damage");
    private static final Identifier ENDURANCE_HEALTH = AcademyCraft.academy("attribute_bonus.endurance_health");
    private static final Identifier DEXTERITY_SPEED = AcademyCraft.academy("attribute_bonus.dexterity_speed");
    private static final Identifier ENDURANCE_JUMP = AcademyCraft.academy("attribute_bonus.endurance_jump");
    private static final ThreadLocal<Deque<DamageSource>> DAMAGE_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> RESISTANCE_BYPASS_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private PlayerAttributeRuntime() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var muscle = value(player, PlayerAttributes.MUSCLE_STRENGTH);
        var endurance = value(player, PlayerAttributes.ENDURANCE);
        var dexterity = value(player, PlayerAttributes.DEXTERITY);

        syncModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                MUSCLE_DAMAGE,
                muscleDamageBonus(muscle),
                AttributeModifier.Operation.ADD_VALUE
        );
        syncModifier(
                player.getAttribute(Attributes.MAX_HEALTH),
                ENDURANCE_HEALTH,
                enduranceHealthBonus(endurance),
                AttributeModifier.Operation.ADD_VALUE
        );
        syncModifier(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                DEXTERITY_SPEED,
                dexteritySpeedBonus(dexterity),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
        syncModifier(
                player.getAttribute(Attributes.JUMP_STRENGTH),
                ENDURANCE_JUMP,
                enduranceJumpBonus(endurance),
                AttributeModifier.Operation.ADD_VALUE
        );

        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    @SubscribeEvent
    public static void onExperienceGain(PlayerXpEvent.XpChange event) {
        if (event.getAmount() <= 0) return;
        var bonus = perceptionBonus(event.getEntity());
        if (bonus <= 0) return;
        var scaled = (long) event.getAmount() * (1L + bonus);
        event.setAmount((int) Math.min(Integer.MAX_VALUE, scaled));
    }

    @SubscribeEvent
    public static void onEnchantedEntityLoot(EnchantedEntityLootEvent event) {
        if (!event.getEnchantment().is(Enchantments.LOOTING)) return;
        var player = resolvePlayer(event.getDamageSource());
        if (player == null) return;
        event.setEnchantmentLevel(event.getEnchantmentLevel() + perceptionBonus(player));
    }

    public static int perceptionBonus(Player player) {
        return logarithmicLevel(value(player, PlayerAttributes.PERCEPTION));
    }

    public static int neuralIterationMultiplier(Player player) {
        var bonus = logarithmicLevel(value(player, PlayerAttributes.NEURAL_ACTIVITY));
        return 1 + Math.max(0, bonus);
    }

    public static double muscleDamageBonus(double value) {
        return Math.ceil(Math.log1p(nonNegative(value)));
    }

    public static double enduranceHealthBonus(double value) {
        return Math.ceil(Math.log1p(nonNegative(value)) * 2.0);
    }

    public static double dexteritySpeedBonus(double value) {
        return Math.log1p(nonNegative(value)) * 0.2;
    }

    public static double enduranceJumpBonus(double value) {
        return Math.log1p(nonNegative(value));
    }

    public static int logarithmicLevel(double value) {
        return (int) Math.floor(Math.log1p(nonNegative(value)) * 0.5);
    }

    public static double trueResistance(Player player) {
        return Math.clamp(value(player, PlayerAttributes.TRUE_RESISTANCE), 0.0, 8.0);
    }

    public static void syncTrueResistanceModifier(Player player, Identifier id,
                                                  double amount, boolean enabled) {
        syncModifier(
                player.getAttribute(PlayerAttributes.TRUE_RESISTANCE),
                id,
                enabled ? Math.max(0.0, amount) : 0.0,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    public static float reduceDamage(Player player, float damage, double reductionPerPoint) {
        if (!(damage > 0.0f) || !Float.isFinite(damage)) return damage;
        var multiplier = Math.max(0.0, 1.0 - trueResistance(player) * reductionPerPoint);
        return (float) (damage * multiplier);
    }

    /**
     * Reduces a negative health write. This is intentionally the last line of defense so direct
     * calls to setHealth receive the same protection as hurtServer and actuallyHurt.
     */
    public static float modifyHealthWrite(Player player, float requestedHealth) {
        if (RESISTANCE_BYPASS_DEPTH.get() > 0 || !Float.isFinite(requestedHealth)) return requestedHealth;
        var current = player.getHealth();
        if (!(requestedHealth < current)) return requestedHealth;

        var source = DAMAGE_CONTEXT.get().peek();
        var reductionPerPoint = source != null && DamageTypes.usesResistanceBackdoor(source)
                ? 0.08
                : 0.10;
        var reducedLoss = reduceDamage(player, current - requestedHealth, reductionPerPoint);
        return current - reducedLoss;
    }

    public static void pushDamageContext(DamageSource source) {
        if (source != null) DAMAGE_CONTEXT.get().push(source);
    }

    public static void popDamageContext() {
        var stack = DAMAGE_CONTEXT.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) DAMAGE_CONTEXT.remove();
    }

    public static void clearDamageContext() {
        DAMAGE_CONTEXT.remove();
    }

    public static void runWithoutResistance(Runnable action) {
        RESISTANCE_BYPASS_DEPTH.set(RESISTANCE_BYPASS_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            var depth = RESISTANCE_BYPASS_DEPTH.get() - 1;
            if (depth <= 0) RESISTANCE_BYPASS_DEPTH.remove();
            else RESISTANCE_BYPASS_DEPTH.set(depth);
        }
    }

    private static double value(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return Math.max(0.0, player.getAttributeValue(attribute));
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static void syncModifier(AttributeInstance attribute, Identifier id, double amount,
                                     AttributeModifier.Operation operation) {
        if (attribute == null) return;
        var current = attribute.getModifier(id);
        if (current != null
                && current.operation() == operation
                && Math.abs(current.amount() - amount) < 1.0E-9) return;
        if (current != null) attribute.removeModifier(id);
        if (amount != 0.0 && Double.isFinite(amount)) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static Player resolvePlayer(DamageSource source) {
        if (source == null) return null;
        if (source.getEntity() instanceof Player player) return player;
        if (source.getDirectEntity() instanceof Player player) return player;
        Entity direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player) return player;
        return null;
    }
}
