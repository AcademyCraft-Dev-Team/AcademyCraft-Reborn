package org.academy.api.common.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.vanilla.MinecraftServerContext;

import java.util.*;

/**
 * 名称可以参考 <a href="https://toarumajutsunoindex.fandom.com/wiki/Category:Esper_Abilities">...</a> 喵
 */
public abstract class AbilityCategory {
    /**
     * CODEC 因性能开销并不适合用于网络数据传输喵, 请使用 STREAM_CODEC 喵
     */
    public static final Codec<AbilityCategory> CODEC =
            Codec.INT.xmap(Registries.ABILITY_CATEGORIES::byId, Registries.ABILITY_CATEGORIES::getId);
    public static final StreamCodec<ByteBuf, AbilityCategory> STREAM_CODEC =
            ByteBufCodecs.idMapper(Registries.ABILITY_CATEGORIES);
    /**
     * 成为此能力的概率喵
     */
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

    public void initClient() {
    }

    /**
     * 要注意服务器不一定只初始化一次喵
     */
    public void initServer(MinecraftServerContext context) {
    }
}