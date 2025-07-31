package org.academy.api.common.ability;

import com.google.common.collect.ImmutableSet;
import net.minecraft.Util;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.academy.api.common.ability.event.AbilitySystemFinalizedEvent;
import org.academy.api.common.registries.Registries;
import org.academy.internal.server.world.level.storage.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class Skill {
    private final AbilityLevel recommendedLevel;
    private final int energyCostToLearn;
    private final AbilityCategory category;
    private Set<Skill> dependencies;

    protected Skill(Builder builder) {
        recommendedLevel = builder.recommendedLevel;
        energyCostToLearn = builder.energyCostToLearn;
        category = builder.category;
        category.addSkill(this);

        if (builder.dependencyHolders.isEmpty()) {
            dependencies = ImmutableSet.of();
        } else {
            var dependencyResolver = new DependencyResolver(this, builder.dependencyHolders);
            NeoForge.EVENT_BUS.register(dependencyResolver);
        }
    }

    public final Set<Skill> getDependencies() {
        return dependencies;
    }

    public void init() {
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }

    public AbilityLevel getRecommendedLevel() {
        return recommendedLevel;
    }

    public AbilityCategory getCategory() {
        return category;
    }

    public int getEnergyCostToLearn() {
        return energyCostToLearn;
    }

    public Player.SkillData getDefaultSkillData() {
        return new Player.SkillData() {
        };
    }

    @NotNull
    public ResourceLocation getKey() {
        ResourceLocation key = Registries.SKILLS.getKey(this);
        if (key == null) {
            throw new IllegalStateException("This skill has not been registered: " + this);
        }
        return key;
    }

    @NotNull
    public String getKeyString() {
        return getKey().toString();
    }

    @NotNull
    public String getDescriptionId() {
        return Util.makeDescriptionId("skill", getKey());
    }

    @NotNull
    public String getTranslatedName() {
        return Language.getInstance().getOrDefault(getDescriptionId());
    }

    @NotNull
    public String getKeyBindingKeyName(String name) {
        var key = getKey();
        var skillName = Util.makeDescriptionId("key", key);
        return skillName + "." + name;
    }

    private record DependencyResolver(Skill target, Set<DeferredHolder<Skill, ? extends Skill>> holders) {
        private DependencyResolver(Skill target, Set<DeferredHolder<Skill, ? extends Skill>> holders) {
            this.target = target;
            this.holders = Set.copyOf(holders);
        }

        @SubscribeEvent
        public void onFinalize(AbilitySystemFinalizedEvent event) {
            target.dependencies = holders.stream()
                    .map(DeferredHolder::get)
                    .collect(ImmutableSet.toImmutableSet());
            NeoForge.EVENT_BUS.unregister(this);
        }
    }

    public static final class Builder {
        private final AbilityCategory category;
        private AbilityLevel recommendedLevel = AbilityLevel.LEVEL0;
        private int energyCostToLearn = 5000;
        private final Set<DeferredHolder<Skill, ? extends Skill>> dependencyHolders = new HashSet<>();

        private Builder(AbilityCategory category) {
            this.category = category;
        }

        public Builder level(AbilityLevel level) {
            recommendedLevel = level;
            return this;
        }

        public Builder energyCost(int cost) {
            energyCostToLearn = cost;
            return this;
        }

        @SafeVarargs
        public final Builder dependsOn(DeferredHolder<Skill, ? extends Skill>... dependencies) {
            Collections.addAll(dependencyHolders, dependencies);
            return this;
        }

        public static Builder of(AbilityCategory category) {
            return new Builder(category);
        }
    }
}