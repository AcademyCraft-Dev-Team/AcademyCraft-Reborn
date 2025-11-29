package org.academy.api.common.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import org.academy.api.common.registries.Registries;

import java.util.*;

/**
 * 名称可以参考 <a href="https://toarumajutsunoindex.fandom.com/wiki/Category:Esper_Abilities">...</a>
 * 也应该遵守于此
 */
public abstract class AbilityCategory {
    /**
     * CODEC 因性能开销并不适合用于网络数据传输喵, 请使用 CODEC 喵
     */
    public static final Codec<AbilityCategory> CODEC =
            Codec.INT.xmap(Registries.ABILITY_CATEGORIES::byId, Registries.ABILITY_CATEGORIES::getId);
    public static final StreamCodec<ByteBuf, AbilityCategory> STREAM_CODEC =
            ByteBufCodecs.idMapper(Registries.ABILITY_CATEGORIES);
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
        skills = Collections.unmodifiableMap(skills);
    }

    public final Collection<Skill> getSkills() {
        return skills.values();
    }

    public final float getProbability() {
        return probability;
    }

    public Identifier getKey() {
        return Objects.requireNonNull(Registries.ABILITY_CATEGORIES.getKey(this), "This ability category has not been registered.");
    }

    public String getKeyString() {
        return getKey().toString();
    }

    public String getDescriptionId() {
        return Util.makeDescriptionId("ability_category", getKey());
    }

    public void initClient() {
    }

    public void initServer(MinecraftServer server) {
    }
}