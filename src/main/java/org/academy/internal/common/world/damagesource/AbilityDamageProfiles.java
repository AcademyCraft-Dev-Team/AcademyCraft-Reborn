package org.academy.internal.common.world.damagesource;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.AbilityDamageProfile;
import org.academy.api.common.damage.DamageSettlement;
import org.academy.api.common.registries.Registries;
import java.util.*;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AbilityDamageProfiles {
    private static volatile Map<ResourceKey<DamageType>, AbilityDamageProfile> profiles = Map.of();

    private AbilityDamageProfiles() {
    }

    public static void freeze() {
        var result = new LinkedHashMap<ResourceKey<DamageType>, AbilityDamageProfile>();
        var owners = new HashMap<ResourceKey<DamageType>, String>();
        for (var id : Registries.DAMAGE_PROFILES.keySet().stream()
                .sorted(Comparator.comparing(Object::toString)).toList()) {
            var profile = Registries.DAMAGE_PROFILES.getValue(id);
            var previous = owners.putIfAbsent(profile.damageType(), id.toString());
            if (previous != null || DamageTypes.isBuiltInType(profile.damageType())) {
                throw new IllegalStateException("Damage profile " + id + " conflicts on " + profile.damageType().identifier()
                        + " with " + (previous == null ? "Academy built-in damage rules" : previous));
            }
            result.put(profile.damageType(), profile);
        }
        profiles = Collections.unmodifiableMap(result);
        for (var category : Registries.ABILITY_CATEGORIES) {
            category.getDefaultDamageProfile().ifPresent(AbilityDamageProfiles::require);
        }
        for (var skill : Registries.SKILLS) skill.getDamageProfile().ifPresent(AbilityDamageProfiles::require);
    }

    public static AbilityDamageProfile require(ResourceKey<AbilityDamageProfile> key) {
        return Registries.DAMAGE_PROFILES.get(key)
                .orElseThrow(() -> new IllegalStateException("Missing damage profile " + key.identifier())).value();
    }

    public static Optional<AbilityDamageProfile> find(ResourceKey<DamageType> type) {
        return Optional.ofNullable(profiles.get(type));
    }

    public static Optional<AbilityDamageProfile> find(DamageSource source) {
        return source == null ? Optional.empty() : source.typeHolder().unwrapKey().flatMap(AbilityDamageProfiles::find);
    }

    public static boolean uses(ResourceKey<DamageType> type, DamageSettlement settlement) {
        return find(type).map(profile -> profile.settlement() == settlement).orElse(false);
    }

    public static boolean uses(DamageSource source, DamageSettlement settlement) {
        return find(source).map(profile -> profile.settlement() == settlement).orElse(false);
    }

    @SubscribeEvent
    public static void validateWorld(ServerAboutToStartEvent event) {
        var types = new LinkedHashSet<>(profiles.keySet());
        Registries.ABILITY_CATEGORIES.forEach(category -> category.getDefaultDamageType().ifPresent(types::add));
        Registries.SKILLS.forEach(skill -> skill.getDamageType().ifPresent(types::add));
        var registry = event.getServer().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var missing = types.stream().filter(key -> registry.get(key).isEmpty())
                .map(key -> key.identifier().toString()).sorted().toList();
        if (!missing.isEmpty()) throw new IllegalStateException("Missing addon DamageType data: " + missing);
    }
}
