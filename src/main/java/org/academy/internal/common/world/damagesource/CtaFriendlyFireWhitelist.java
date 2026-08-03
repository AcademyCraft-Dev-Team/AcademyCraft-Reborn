package org.academy.internal.common.world.damagesource;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;

public final class CtaFriendlyFireWhitelist {
    private static final String CONFIG_KEY = "ctaFriendlyFireWhitelist";
    private static final String TAG_PREFIX = "tag:";
    private static final List<String> DEFAULT = List.of("tamed", "touhou_little_maid:maid");

    private CtaFriendlyFireWhitelist() {
    }

    public static boolean shouldProtect(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return false;
        if (FriendlyFireSetting.shouldPrevent(attacker, target)) return true;
        for (var entry : getList(attacker)) {
            if (!matchesEntry(target, entry)) continue;
            var ownerId = FriendlyFireSetting.getOwnerUuid(target);
            if (ownerId != null && ownerId.equals(attacker.getUUID())) return true;
        }
        return false;
    }

    public static boolean isWhitelisted(Entity entity) {
        if (entity == null) return false;
        for (var entry : getList(entity)) if (matchesEntry(entity, entry)) return true;
        return false;
    }

    private static List<String> getList(Entity entity) {
        try {
            var server = entity.level().getServer();
            if (server == null || server.getAcademyCraftServer() == null) return DEFAULT;
            var configured = server.getAcademyCraftServer().getGenericConfig().stringListMap.get(CONFIG_KEY);
            return configured == null ? DEFAULT : configured;
        } catch (Throwable ignored) {
            return DEFAULT;
        }
    }

    private static boolean matchesEntry(Entity entity, String entry) {
        if (entity == null || entry == null || entry.isBlank()) return false;
        if ("tamed".equalsIgnoreCase(entry)) return FriendlyFireSetting.getOwnerUuid(entity) != null;
        if (entry.regionMatches(true, 0, TAG_PREFIX, 0, TAG_PREFIX.length())) {
            var id = Identifier.tryParse(entry.substring(TAG_PREFIX.length()).strip());
            if (id == null) return false;
            var tag = TagKey.create(BuiltInRegistries.ENTITY_TYPE.key(), id);
            return entity.getType().builtInRegistryHolder().is(tag);
        }
        var id = Identifier.tryParse(entry.strip());
        return id != null && Objects.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), id);
    }
}
