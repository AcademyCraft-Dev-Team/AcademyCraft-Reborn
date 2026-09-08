package org.academy.api.common.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.academy.api.common.damage.AbilityDamageProfile;
import org.academy.api.common.ability.program.ProgramProfile;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * 名称可以参考 <a href="https://toarumajutsunoindex.fandom.com/wiki/Category:Esper_Abilities">这里</a> 喵
 */
public abstract class AbilityCategory {
    /**
     * CODEC 因性能开销并不适合用于网络数据传输喵, 请使用 STREAM_CODEC 喵
     */
    public static final Codec<AbilityCategory> CODEC =
            Codec.INT.xmap(Registries.ABILITY_CATEGORIES::byIdOrThrow, Registries.ABILITY_CATEGORIES::getId);
    public static final StreamCodec<ByteBuf, AbilityCategory> STREAM_CODEC =
            ByteBufCodecs.idMapper(Registries.ABILITY_CATEGORIES);
    /**
     * 成为此能力的概率喵
     */
    private final float probability;
    private final @Nullable AbilityFactorProfile developmentProfile;

    private Map<Identifier, Skill> skills = new LinkedHashMap<>();
    private boolean sealed;

    /** Stable namespaced codec for persistence; CODEC retains the legacy numeric representation. */
    public static final Codec<AbilityCategory> ID_CODEC = Identifier.CODEC.flatXmap(
            id -> Registries.ABILITY_CATEGORIES.get(id)
                    .map(holder -> com.mojang.serialization.DataResult.success(holder.value()))
                    .orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "Unknown ability category " + id)),
            category -> com.mojang.serialization.DataResult.success(category.getKey()));

    public static Builder builder() {
        return new Builder();
    }

    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.empty();
    }

    public Optional<ResourceKey<AbilityDamageProfile>> getDefaultDamageProfile() {
        return Optional.empty();
    }

    public Optional<ProgramProfile> getProgramProfile() {
        return Optional.empty();
    }

    protected AbilityCategory(float probability) {
        this(probability, null);
    }

    protected AbilityCategory(float probability, @Nullable AbilityFactorProfile developmentProfile) {
        this.probability = probability;
        this.developmentProfile = developmentProfile;
    }

    public final void addSkill(Skill skill) {
        if (sealed) throw new IllegalStateException("Ability category is frozen: " + getKey());
        Objects.requireNonNull(skill, "skill");
        if (skill.getScope() != SkillScope.CATEGORY || skill.getCategory() != this) {
            throw new IllegalArgumentException("Skill belongs to another category: " + skill.getKey());
        }
        var previous = skills.putIfAbsent(skill.getKey(), skill);
        if (previous != null && previous != skill) {
            throw new IllegalStateException("Duplicate skill ID " + skill.getKey());
        }
    }

    public final void seal() {
        if (sealed) return;
        var sorted = new LinkedHashMap<Identifier, Skill>();
        skills.values().stream().sorted(Comparator.comparingInt(Skill::getDisplayOrder)
                .thenComparing(skill -> skill.getKey().toString()))
                .forEach(skill -> sorted.put(skill.getKey(), skill));
        skills = Collections.unmodifiableMap(sorted);
        sealed = true;
    }

    public final Collection<Skill> getSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    public final float getProbability() {
        return probability;
    }

    /**
     * Optional P.R.O.P.S profile used for server-authoritative initial ability prediction.
     */
    public final Optional<AbilityFactorProfile> getDevelopmentProfile() {
        return Optional.ofNullable(developmentProfile);
    }

    /**
     * Whether this category exposes skills registered with {@link SkillScope#COMMON}.
     */
    public boolean supportsCommonSkills() {
        return true;
    }

    /**
     * Optional resource exposed by this category. Categories without one keep the resource HUD hidden.
     */
    public Optional<AbilityResourceSpec> getResourceSpec() {
        return Optional.empty();
    }

    public Identifier getKey() {
        return Objects.requireNonNull(Registries.ABILITY_CATEGORIES.getKey(this), "This ability category has not been registered.");
    }

    public float getProgIncrRate() {
        return 1.0f;
    }

    /**
     * 能力在开发机左侧面板中显示的图标喵
     */
    public abstract Identifier getDeveloperIcon();

    /**
     * 能力在开发机左侧面板中显示的名称喵
     */
    public abstract String getDisplayName();

    public String getDescriptionId() {
        return net.minecraft.util.Util.makeDescriptionId("ability_category", getKey());
    }

    public static final class Builder {
        private String translationKey;
        private Identifier icon;
        private float probability;
        private AbilityFactorProfile development;
        private AbilityResourceSpec resource;
        private boolean commonSkills = true;
        private ResourceKey<DamageType> damageType;
        private ResourceKey<AbilityDamageProfile> damageProfile;
        private ProgramProfile program;

        public Builder translationKey(String value) {
            translationKey = Objects.requireNonNull(value);
            return this;
        }

        public Builder icon(Identifier value) {
            icon = Objects.requireNonNull(value);
            return this;
        }

        /** Explicitly participates in initial development with the supplied profile and weight. */
        public Builder development(float weight, AbilityFactorProfile profile) {
            if (!Float.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Invalid development weight");
            probability = weight;
            development = Objects.requireNonNull(profile);
            return this;
        }

        public Builder developmentProfile(AbilityFactorProfile profile) {
            return development(1.0f, profile);
        }

        public Builder resource(AbilityResourceSpec value) {
            resource = Objects.requireNonNull(value);
            return this;
        }

        public Builder commonSkills(boolean value) {
            commonSkills = value;
            return this;
        }

        public Builder defaultDamageType(ResourceKey<DamageType> value) {
            if (damageProfile != null) throw new IllegalStateException("Damage type and profile are mutually exclusive");
            damageType = Objects.requireNonNull(value);
            return this;
        }

        public Builder defaultDamageProfile(ResourceKey<AbilityDamageProfile> value) {
            if (damageType != null) throw new IllegalStateException("Damage type and profile are mutually exclusive");
            damageProfile = Objects.requireNonNull(value);
            return this;
        }

        public Builder program(ProgramProfile value) {
            program = Objects.requireNonNull(value);
            return this;
        }

        public AbilityCategory build() {
            if (translationKey == null || translationKey.isBlank() || icon == null) {
                throw new IllegalStateException("Category translation key and icon are required");
            }
            return new DeclaredCategory(this);
        }
    }

    private static final class DeclaredCategory extends AbilityCategory {
        private final String name;
        private final Identifier icon;
        private final boolean commonSkills;
        private final Optional<AbilityResourceSpec> resource;
        private final Optional<ResourceKey<DamageType>> damageType;
        private final Optional<ResourceKey<AbilityDamageProfile>> damageProfile;
        private final Optional<ProgramProfile> program;

        private DeclaredCategory(Builder builder) {
            super(builder.probability, builder.development);
            name = builder.translationKey;
            icon = builder.icon;
            commonSkills = builder.commonSkills;
            resource = Optional.ofNullable(builder.resource);
            damageType = Optional.ofNullable(builder.damageType);
            damageProfile = Optional.ofNullable(builder.damageProfile);
            program = Optional.ofNullable(builder.program);
        }

        @Override public String getDisplayName() { return org.academy.api.common.util.L10nUtil.get(name); }
        @Override public String getDescriptionId() { return name; }
        @Override public Identifier getDeveloperIcon() { return icon; }
        @Override public boolean supportsCommonSkills() { return commonSkills; }
        @Override public Optional<AbilityResourceSpec> getResourceSpec() { return resource; }
        @Override public Optional<ResourceKey<DamageType>> getDefaultDamageType() { return damageType; }
        @Override public Optional<ResourceKey<AbilityDamageProfile>> getDefaultDamageProfile() { return damageProfile; }
        @Override public Optional<ProgramProfile> getProgramProfile() { return program; }
    }

    public void initClient() {
    }

    /**
     * 要注意服务器不一定只初始化一次喵
     */
    public void initServer(MinecraftServerContext context) {
    }
}
