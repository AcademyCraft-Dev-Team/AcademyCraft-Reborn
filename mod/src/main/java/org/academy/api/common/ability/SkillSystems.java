package org.academy.api.common.ability;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class SkillSystems {
    @FunctionalInterface
    public interface Provider {
        SkillSystem of(ServerPlayer player);
    }

    private static volatile @Nullable Provider provider;

    private SkillSystems() {
    }

    public static void register(Provider provider) {
        SkillSystems.provider = Objects.requireNonNull(provider);
    }

    public static SkillSystem of(ServerPlayer player) {
        var current = provider;
        if (current == null) throw new IllegalStateException("Skill system backend is not registered");
        return Objects.requireNonNull(current.of(player), "skill system");
    }
}
