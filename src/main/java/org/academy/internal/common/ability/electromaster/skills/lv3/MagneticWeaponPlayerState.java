package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.academy.mixin.common.LivingEntityAttackStateAccessor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Temporarily exposes a hotbar weapon as the player's main hand without allowing
 * the synthetic attack to consume the player's real attack or sprint state.
 */
final class MagneticWeaponPlayerState implements AutoCloseable {
    private final ServerPlayer player;
    private final LivingEntityAttackStateAccessor attackState;
    private final int originalSelectedSlot;
    private final int originalAttackStrengthTicker;
    private final int originalItemSwapTicker;
    private final boolean originalSprinting;
    private final double originalFallDistance;
    private final float syntheticFallDistance;
    private final Vec3 originalMovement;
    private final Vec3 originalImpulseImpactPosition;
    private final Map<ModifierKey, ModifierSnapshot> originalModifiers;
    private final Map<ModifierKey, AttributeModifier> weaponModifiers;
    private final Set<ModifierKey> affectedModifiers;
    private boolean closed;

    private MagneticWeaponPlayerState(ServerPlayer player, int weaponSlot, float syntheticFallDistance) {
        if (!Inventory.isHotbarSlot(weaponSlot)) {
            throw new IllegalArgumentException("Magnetic weapon must be in the hotbar: " + weaponSlot);
        }

        this.player = player;
        attackState = (LivingEntityAttackStateAccessor) player;
        originalSelectedSlot = player.getInventory().getSelectedSlot();
        originalAttackStrengthTicker = attackState.academy$getAttackStrengthTicker();
        originalItemSwapTicker = attackState.academy$getItemSwapTicker();
        originalSprinting = player.isSprinting();
        originalFallDistance = player.fallDistance;
        this.syntheticFallDistance = syntheticFallDistance;
        originalMovement = player.getDeltaMovement();
        originalImpulseImpactPosition = player.currentImpulseImpactPos;

        var originalMainHand = player.getMainHandItem();
        var weapon = player.getInventory().getItem(weaponSlot);
        var originalItemModifiers = collectModifiers(originalMainHand, true);
        weaponModifiers = collectModifiers(weapon, !weapon.isBroken());
        affectedModifiers = new LinkedHashSet<>(originalItemModifiers.keySet());
        affectedModifiers.addAll(weaponModifiers.keySet());
        originalModifiers = snapshotModifiers(player, affectedModifiers);

    }

    static MagneticWeaponPlayerState open(ServerPlayer player, int weaponSlot, float syntheticFallDistance) {
        var state = new MagneticWeaponPlayerState(player, weaponSlot, syntheticFallDistance);
        try {
            state.activate(weaponSlot);
            return state;
        } catch (RuntimeException | Error error) {
            state.close();
            throw error;
        }
    }

    private void activate(int weaponSlot) {
        if (originalSprinting) player.setSprinting(false);
        if (syntheticFallDistance >= 0.0f) player.fallDistance = syntheticFallDistance;
        player.getInventory().setSelectedSlot(weaponSlot);
        applyWeaponModifiers();

        var fullChargeTicks = Math.max(1, Mth.ceil(player.getCurrentItemAttackStrengthDelay()) + 1);
        attackState.academy$setAttackStrengthTicker(fullChargeTicks);
        attackState.academy$setItemSwapTicker(fullChargeTicks);
    }

    private void applyWeaponModifiers() {
        for (var key : affectedModifiers) {
            var instance = player.getAttribute(key.attribute());
            if (instance != null) instance.removeModifier(key.id());
        }
        weaponModifiers.forEach((key, modifier) -> {
            var instance = player.getAttribute(key.attribute());
            if (instance != null) instance.addTransientModifier(modifier);
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        try {
            for (var key : affectedModifiers) {
                var instance = player.getAttribute(key.attribute());
                if (instance != null) instance.removeModifier(key.id());
            }
            originalModifiers.forEach((key, snapshot) -> {
                if (snapshot.modifier() == null) return;
                var instance = player.getAttribute(key.attribute());
                if (instance == null) return;
                if (snapshot.permanent()) instance.addPermanentModifier(snapshot.modifier());
                else instance.addTransientModifier(snapshot.modifier());
            });
        } finally {
            player.getInventory().setSelectedSlot(originalSelectedSlot);
            attackState.academy$setAttackStrengthTicker(originalAttackStrengthTicker);
            attackState.academy$setItemSwapTicker(originalItemSwapTicker);
            player.fallDistance = originalFallDistance;
            if (syntheticFallDistance >= 0.0f) {
                player.setDeltaMovement(originalMovement);
                if (originalImpulseImpactPosition == null) player.resetCurrentImpulseContext();
                else player.setIgnoreFallDamageFromCurrentImpulse(true, originalImpulseImpactPosition);
                player.setSpawnExtraParticlesOnFall(false);
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            if (player.isSprinting() != originalSprinting) player.setSprinting(originalSprinting);
        }
    }

    private static Map<ModifierKey, AttributeModifier> collectModifiers(ItemStack stack, boolean include) {
        var modifiers = new LinkedHashMap<ModifierKey, AttributeModifier>();
        if (!include || stack.isEmpty()) return modifiers;
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) ->
                modifiers.put(new ModifierKey(attribute, modifier.id()), modifier));
        return modifiers;
    }

    private static Map<ModifierKey, ModifierSnapshot> snapshotModifiers(
            ServerPlayer player,
            Set<ModifierKey> keys
    ) {
        var snapshots = new LinkedHashMap<ModifierKey, ModifierSnapshot>();
        for (var key : keys) {
            var instance = player.getAttribute(key.attribute());
            if (instance == null) continue;
            var modifier = instance.getModifier(key.id());
            snapshots.put(key, new ModifierSnapshot(
                    modifier,
                    modifier != null && instance.getPermanentModifiers().contains(modifier)
            ));
        }
        return snapshots;
    }

    private record ModifierKey(Holder<Attribute> attribute, Identifier id) {
    }

    private record ModifierSnapshot(AttributeModifier modifier, boolean permanent) {
    }
}
