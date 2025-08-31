package org.academy.api.common.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.future.PayloadType;

public final class Registries {
    public static final Registry<AbilityCategory> ABILITY_CATEGORIES = new RegistryBuilder<>(Keys.ABILITY_CATEGORIES).sync(true).create();
    public static final Registry<Skill> SKILLS = new RegistryBuilder<>(Keys.SKILLS).sync(true).create();
    public static final Registry<PacketType<?, ?>> PACKET_TYPES = new RegistryBuilder<>(Keys.PACKET_TYPES).sync(true).create();
    public static final Registry<PayloadType<?, ?>> PAYLOAD_TYPES = new RegistryBuilder<>(Keys.PAYLOAD_TYPES).sync(true).create();

    public static final class Keys {
        public static final ResourceKey<Registry<AbilityCategory>> ABILITY_CATEGORIES = key("ability_category");
        public static final ResourceKey<Registry<Skill>> SKILLS = key("skill");
        public static final ResourceKey<Registry<PacketType<?, ?>>> PACKET_TYPES = key("packet_type");
        public static final ResourceKey<Registry<PayloadType<?, ?>>> PAYLOAD_TYPES = key("payload_types");

        private static <T> ResourceKey<Registry<T>> key(String name) {
            return ResourceKey.createRegistryKey(AcademyCraft.academy(name));
        }

        private Keys() {
        }
    }

    private Registries() {
    }
}