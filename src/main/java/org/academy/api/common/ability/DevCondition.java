package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.resources.R;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.ability.AbilitySystemServer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public interface DevCondition {
    boolean accepts();

    default boolean accepts(ServerPlayer player, WirelessUser developer) {
        return accepts();
    }

    @Nullable
    Identifier getIcon();

    String getHintText();

    default boolean shouldDisplay() {
        return true;
    }

    record LevelCondition(AbilityLevel requiredLevel) implements DevCondition {
        @Override
        public boolean accepts() {
            return AbilitySystemClient.getLevel().levelCode >= requiredLevel.levelCode;
        }

        @Override
        public boolean accepts(ServerPlayer player, WirelessUser developer) {
            var system = AbilitySystemServer.getSystem(player);
            return system.getPlayerLevel(player.getUUID()) >= requiredLevel.levelCode;
        }

        @Override
        public @Nullable Identifier getIcon() {
            return switch (requiredLevel.levelCode) {
                case 1 -> R.textures.ability.condition.any1;
                case 2 -> R.textures.ability.condition.any2;
                case 3 -> R.textures.ability.condition.any3;
                case 4 -> R.textures.ability.condition.any4;
                case 5 -> R.textures.ability.condition.any5;
                default -> null;
            };
        }

        @Override
        public String getHintText() {
            return "Requires Level " + requiredLevel.levelCode;
        }
    }

    record EnergyCondition(int requiredEnergy) implements DevCondition {
        @Override
        public boolean accepts() {
            return false;
        }

        @Override
        public boolean accepts(ServerPlayer player, WirelessUser developer) {
            return developer.getEnergyStored() >= requiredEnergy;
        }

        @Override
        public @Nullable Identifier getIcon() {
            return null;
        }

        @Override
        public String getHintText() {
            return "Requires " + requiredEnergy + " IM";
        }
    }

    record DependencyCondition(String depName, String depId) implements DevCondition {
        public DependencyCondition(String depName) {
            this(depName, "");
        }

        static boolean isSatisfied(String dependencyId, Predicate<String> isLearned) {
            return dependencyId == null || dependencyId.isEmpty() || isLearned.test(dependencyId);
        }

        @Override
        public boolean accepts() {
            return isSatisfied(depId, dependencyId -> AbilitySystemClient.LEARNED_SKILLS.stream()
                    .anyMatch(skill -> dependencyId.equals(skill.getKeyString())));
        }

        @Override
        public boolean accepts(ServerPlayer player, WirelessUser developer) {
            var system = AbilitySystemServer.getSystem(player);
            return isSatisfied(
                    depId,
                    system.getPlayerData(player.getUUID())::isSkillLearned
            );
        }

        @Override
        public @Nullable Identifier getIcon() {
            return null;
        }

        @Override
        public String getHintText() {
            return "Requires " + depName;
        }

        @Override
        public boolean shouldDisplay() {
            return false;
        }
    }

    record AnySkillOfLevelCondition(int requiredLevel) implements DevCondition {
        @Override
        public boolean accepts() {
            return AbilitySystemClient.LEARNED_SKILLS.stream()
                    .anyMatch(s -> s.getRecommendedLevel().levelCode >= requiredLevel);
        }

        @Override
        public boolean accepts(ServerPlayer player, WirelessUser developer) {
            var system = AbilitySystemServer.getSystem(player);
            var data = system.getPlayerData(player.getUUID());
            return Registries.SKILLS.stream()
                    .anyMatch(skill -> skill.getRecommendedLevel().levelCode >= requiredLevel
                            && data.isSkillLearned(Objects.requireNonNull(
                            Registries.SKILLS.getKey(skill)).toString()));
        }

        @Override
        public @Nullable Identifier getIcon() {
            return switch (requiredLevel) {
                case 1 -> R.textures.ability.condition.any1;
                case 2 -> R.textures.ability.condition.any2;
                case 3 -> R.textures.ability.condition.any3;
                case 4 -> R.textures.ability.condition.any4;
                case 5 -> R.textures.ability.condition.any5;
                default -> null;
            };
        }

        @Override
        public String getHintText() {
            return "Requires any skill of level " + requiredLevel;
        }
    }
}
