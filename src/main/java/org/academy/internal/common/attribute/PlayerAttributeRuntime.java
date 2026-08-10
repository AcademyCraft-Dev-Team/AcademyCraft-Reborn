package org.academy.internal.common.attribute;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantedBlockLootEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.internal.common.world.damagesource.DamageTypes;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.Mth;

/**
 * Applies the linear bonuses derived from effective P.R.O.P.S player attributes.
 */
@EventBusSubscriber
public final class PlayerAttributeRuntime {
    private static final Identifier MUSCLE_DAMAGE = AcademyCraft.academy("attribute_bonus.muscle_damage");
    private static final Identifier ENDURANCE_HEALTH = AcademyCraft.academy("attribute_bonus.endurance_health");
    private static final Identifier DEXTERITY_SPEED = AcademyCraft.academy("attribute_bonus.dexterity_speed");
    private static final Identifier LEGACY_ENDURANCE_JUMP = AcademyCraft.academy("attribute_bonus.endurance_jump");
    private static final Identifier DEXTERITY_JUMP = AcademyCraft.academy("attribute_bonus.dexterity_jump");
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
        var healthBeforeEnduranceSync = player.getHealth();

        syncModifier(
                player.getAttribute(Attributes.ATTACK_DAMAGE),
                MUSCLE_DAMAGE,
                muscleDamageBonus(muscle),
                AttributeModifier.Operation.ADD_VALUE,
                true
        );
        syncModifier(
                player.getAttribute(Attributes.MAX_HEALTH),
                ENDURANCE_HEALTH,
                enduranceHealthBonus(endurance),
                AttributeModifier.Operation.ADD_VALUE,
                true
        );
        var healthAfterEnduranceSync = healthAfterMaxHealthChange(
                healthBeforeEnduranceSync, player.getMaxHealth()
        );
        if (healthAfterEnduranceSync < healthBeforeEnduranceSync) {
            player.setHealth(healthAfterEnduranceSync);
        }
        syncModifier(
                player.getAttribute(Attributes.MOVEMENT_SPEED),
                DEXTERITY_SPEED,
                dexteritySpeedBonus(dexterity),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                true
        );
        syncModifier(
                player.getAttribute(Attributes.JUMP_STRENGTH),
                LEGACY_ENDURANCE_JUMP,
                0.0,
                AttributeModifier.Operation.ADD_VALUE,
                false
        );
        syncModifier(
                player.getAttribute(Attributes.JUMP_STRENGTH),
                DEXTERITY_JUMP,
                dexterityJumpStrengthBonus(dexterity),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                true
        );

    }

    @SubscribeEvent
    public static void onExperienceGain(PlayerXpEvent.XpChange event) {
        if (event.getAmount() <= 0) return;
        var multiplier = perceptionExperienceMultiplier(event.getEntity());
        if (multiplier <= 1.0) return;
        var scaled = Mth.lfloor(event.getAmount() * multiplier);
        event.setAmount((int) Math.min(Integer.MAX_VALUE, scaled));
    }

    @SubscribeEvent
    public static void onEnchantedEntityLoot(EnchantedEntityLootEvent event) {
        if (!event.getEnchantment().is(Enchantments.LOOTING)) return;
        var player = resolvePlayer(event.getDamageSource());
        if (player == null) return;
        event.setEnchantmentLevel(event.getEnchantmentLevel() + perceptionBonus(player));
    }

    @SubscribeEvent
    public static void onEnchantedBlockLoot(EnchantedBlockLootEvent event) {
        if (!event.getEnchantment().is(Enchantments.FORTUNE)) return;
        var player = BlockLootPlayerContext.current();
        if (player == null) return;
        event.setEnchantmentLevel(event.getEnchantmentLevel() + perceptionBonus(player));
    }

    public static int perceptionBonus(Player player) {
        return logarithmicLevel(value(player, PlayerAttributes.PERCEPTION));
    }

    public static double perceptionExperienceMultiplier(Player player) {
        return PropsMath.perceptionExperienceMultiplier(value(player, PlayerAttributes.PERCEPTION));
    }

    public static double neuralIterationMultiplier(Player player) {
        return PropsMath.neuralIterationMultiplier(value(player, PlayerAttributes.NEURAL_ACTIVITY));
    }

    public static double muscleDamageBonus(double value) {
        return PropsMath.muscleDamageBonus(value);
    }

    public static double enduranceHealthBonus(double value) {
        return PropsMath.enduranceHealthBonus(value);
    }

    public static double dexteritySpeedBonus(double value) {
        return PropsMath.dexteritySpeedBonus(value);
    }

    public static double dexterityJumpStrengthBonus(double value) {
        return PropsMath.dexterityJumpStrengthBonus(value);
    }

    public static int logarithmicLevel(double value) {
        return PropsMath.perceptionEnchantmentBonus(value);
    }

    public static double trueResistance(Player player) {
        return Mth.clamp(value(player, PlayerAttributes.TRUE_RESISTANCE), 0.0, 8.0);
    }

    public static void syncTrueResistanceModifier(Player player, Identifier id,
                                                  double amount, boolean enabled) {
        syncModifier(
                player.getAttribute(PlayerAttributes.TRUE_RESISTANCE),
                id,
                enabled ? Math.max(0.0, amount) : 0.0,
                AttributeModifier.Operation.ADD_VALUE,
                false
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

    static float healthAfterMaxHealthChange(float health, float newMaxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(newMaxHealth)) return health;
        // ENDURANCE grows when damage is taken. Raising current health together with its maximum
        // therefore turns a lethal hit into a positive-health dead player on the following tick.
        // Attribute synchronization may clamp health after a maximum decrease, but must never heal.
        return Math.min(health, Math.max(0.0f, newMaxHealth));
    }

    private static double value(Player player, Holder<Attribute> attribute) {
        return Math.max(0.0, player.getAttributeValue(attribute));
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static void syncModifier(AttributeInstance attribute, Identifier id, double amount,
                                     AttributeModifier.Operation operation, boolean permanent) {
        if (attribute == null) return;
        var current = attribute.getModifier(id);
        var currentIsPermanent = current != null && attribute.getPermanentModifiers().stream()
                .anyMatch(modifier -> modifier.id().equals(id));
        if (current != null
                && current.operation() == operation
                && Math.abs(current.amount() - amount) < 1.0E-9
                && currentIsPermanent == permanent) return;
        if (amount == 0.0 || !Double.isFinite(amount)) {
            if (current != null) attribute.removeModifier(id);
            return;
        }

        var replacement = new AttributeModifier(id, amount, operation);
        if (permanent) {
            // Stable P.R.O.P.S bonuses must be effective as soon as attributes are loaded. This is
            // essential for MAX_HEALTH: otherwise saved health is clamped to the vanilla limit
            // before the first player tick restores the endurance bonus.
            attribute.addOrReplacePermanentModifier(replacement);
        } else if (current == null) {
            attribute.addTransientModifier(replacement);
        } else if (!currentIsPermanent && current.operation() == operation) {
            attribute.addOrUpdateTransientModifier(replacement);
        } else {
            attribute.removeModifier(id);
            attribute.addTransientModifier(replacement);
        }
    }

    private static Player resolvePlayer(DamageSource source) {
        if (source == null) return null;
        if (source.getEntity() instanceof Player player) return player;
        if (source.getDirectEntity() instanceof Player player) return player;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof Player player) return player;
        return null;
    }
}
