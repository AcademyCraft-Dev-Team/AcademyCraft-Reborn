package org.academy.api.common.ability;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.locale.Language;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.academy.api.common.ability.event.AbilitySystemFinalizedEvent;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.vanilla.MinecraftServerContext;

import java.util.*;

public abstract class Skill {
    public static final Codec<Skill> CODEC =
            Codec.INT.xmap(Registries.SKILLS::byIdOrThrow, Registries.SKILLS::getId);
    public static final StreamCodec<ByteBuf, Skill> STREAM_CODEC = ByteBufCodecs.idMapper(Registries.SKILLS);
    public static final StreamCodec<ByteBuf, Set<Skill>> STREAM_CODEC_SET = STREAM_CODEC.apply(
            codec -> ByteBufCodecs.collection(HashSet::new, codec)
    );

    private final AbilityLevel recommendedLevel;
    private final int energyCostToLearn;
    private final AbilityCategory category;
    private Set<Skill> dependencies = new HashSet<>();

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

    public static <T extends Context> Map<Player, T> createContextMap() {
        return new WeakHashMap<>();
    }

    public final Set<Skill> getDependencies() {
        return dependencies;
    }

    public void init() {
    }

    public void initClient() {
    }

    /**
     * 要注意服务器不一定只初始化一次喵
     */
    public void initServer(MinecraftServerContext context) {
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

    public Identifier getKey() {
        var key = Registries.SKILLS.getKey(this);
        if (key == null) {
            throw new IllegalStateException("This skill has not been registered: " + this);
        }
        return key;
    }

    public String getKeyString() {
        return getKey().toString();
    }

    public String getDescriptionId() {
        return Util.makeDescriptionId("skill", getKey());
    }

    public String getTranslatedName() {
        return Language.getInstance().getOrDefault(getDescriptionId());
    }

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