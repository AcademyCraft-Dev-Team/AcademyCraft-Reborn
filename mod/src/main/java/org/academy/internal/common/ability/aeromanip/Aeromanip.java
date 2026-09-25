package org.academy.internal.common.ability.aeromanip;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AbilityResourceSpec;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.academy.internal.common.world.damagesource.DamageTypes;

import java.util.*;

public final class Aeromanip extends AbilityCategory {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private final Map<UUID, WearState> wear = new HashMap<>();

    public static int equipmentWear(float fixedDamage) {
        return fixedDamage > 0.0f && Float.isFinite(fixedDamage)
                ? Math.min(20, (int) Math.ceil(4.0f + fixedDamage * 0.5f)) : 0;
    }

    public void onDamageCompleted(LivingEntity target, DamageContainer hit, float healthDamage) {
        if (!(hit.getSource() instanceof SkillDamageSource source) || !(healthDamage > 0.0f)) return;
        var fixed = Math.max(0.0f, hit.getOriginalDamage()
                - DamageComposition.maximumHealthPart(target, source));
        damageEquipment(target, equipmentWear(fixed), source.is(DamageTypes.LAMINAR_CUT));
    }

    public void damageEquipment(LivingEntity target, int amount, boolean includeHands) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || amount <= 0) return;
        var now = target.level().getServer().getTickCount();
        var state = wear.computeIfAbsent(target.getUUID(), ignored -> new WearState());
        if (now >= state.expires) {
            state.used.clear();
            state.expires = now + 10L;
        }
        for (var slot : ARMOR) wearSlot(target, slot, amount, state);
        if (includeHands) {
            wearSlot(target, EquipmentSlot.MAINHAND, amount, state);
            wearSlot(target, EquipmentSlot.OFFHAND, amount, state);
        }
    }

    private static void wearSlot(LivingEntity target, EquipmentSlot slot, int amount, WearState state) {
        var stack = target.getItemBySlot(slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        var cap = amount > 20 ? 80 : 20;
        var used = state.used.getOrDefault(slot, 0);
        var accepted = Math.clamp(cap - used, 0, amount);
        if (accepted <= 0) return;
        state.used.put(slot, used + accepted);
        stack.hurtAndBreak(accepted, target, slot);
    }

    public void tick(long gameTime) {
        wear.values().removeIf(state -> gameTime >= state.expires);
    }

    public void leave(LivingEntity target) {
        wear.remove(target.getUUID());
    }

    public void stop() {
        wear.clear();
    }

    private static final class WearState {
        final Map<EquipmentSlot, Integer> used = new EnumMap<>(EquipmentSlot.class);
        long expires;
    }

    public static final int DEFAULT_COMPRESSED_AIR_CAPACITY = 128;
    public static final float DEFAULT_COMPRESSED_AIR_RECOVERY_PER_TICK = 4.0f;
    public static final AbilityResourceSpec COMPRESSED_AIR_RESOURCE =
            AbilityResourceSpec.fixed(DEFAULT_COMPRESSED_AIR_CAPACITY);

    public Aeromanip() {
        super(0.2f, AbilityDevelopmentProfiles.AEROMANIP);
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.AERO_DAMAGE);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ICON_AEROMANIP;
    }

    @Override
    public String getDisplayName() {
        return "Aeromanipulation";
    }

    @Override
    public Optional<AbilityResourceSpec> getResourceSpec() {
        return Optional.of(COMPRESSED_AIR_RESOURCE);
    }
}
