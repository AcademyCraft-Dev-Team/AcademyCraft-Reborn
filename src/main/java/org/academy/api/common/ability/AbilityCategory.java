package org.academy.api.common.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.academy.api.common.registries.Registries;

import java.util.*;

/**
 * 名称可以参考 <a href="https://toarumajutsunoindex.fandom.com/wiki/Category:Esper_Abilities">...</a>
 * 也应该遵守于此
 */
public abstract class AbilityCategory {
    /**
     * CODEC 因为性能开销不适合用在网络数据传输中, 请使用 STREAM_CODEC
     */
    public static final Codec<AbilityCategory> CODEC =
            Codec.INT.xmap(Registries.ABILITY_CATEGORIES::byId, Registries.ABILITY_CATEGORIES::getId);
    public static final StreamCodec<ByteBuf, AbilityCategory> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityCategory decode(ByteBuf buffer) {
            return Registries.ABILITY_CATEGORIES.byIdOrThrow(VarInt.read(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, AbilityCategory value) {
            VarInt.write(buffer, Registries.ABILITY_CATEGORIES.getId(value));
        }
    };
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

    public ResourceLocation getKey() {
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