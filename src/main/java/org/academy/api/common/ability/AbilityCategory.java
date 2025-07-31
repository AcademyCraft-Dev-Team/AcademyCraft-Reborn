package org.academy.api.common.ability;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.academy.api.common.registries.Registries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 名称可以参考 <a href="https://toarumajutsunoindex.fandom.com/wiki/Category:Esper_Abilities">...</a>
 * 也应该遵守于此
 */
public abstract class AbilityCategory {
    private final float probability;

    private Map<Class<? extends Skill>, Skill> skills = new HashMap<>();

    protected AbilityCategory(float probability) {
        this.probability = probability;
    }

    public final void addSkill(Skill skill) {
        if (skills.containsKey(skill.getClass())) {
            throw new IllegalStateException("Skill already exists! " + skill.getClass());
        } else {
            skills.put(skill.getClass(), skill);
        }
    }

    public final void seal() {
        skills = Collections.unmodifiableMap(this.skills);
    }

    public final Collection<Skill> getSkills() {
        return skills.values();
    }

    public final float getProbability() {
        return probability;
    }

    @NotNull
    public ResourceLocation getKey() {
        return Objects.requireNonNull(Registries.ABILITY_CATEGORIES.getKey(this), "This ability category has not been registered.");
    }

    @NotNull
    public String getKeyString() {
        return getKey().toString();
    }

    @NotNull
    public String getDescriptionId() {
        return Util.makeDescriptionId("ability_category", getKey());
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }
}