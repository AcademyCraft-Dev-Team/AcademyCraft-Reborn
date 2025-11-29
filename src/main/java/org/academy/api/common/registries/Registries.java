package org.academy.api.common.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.sync.DataType;
import org.academy.api.common.sync.SyncKey;

public final class Registries {
    public static final Registry<AbilityCategory> ABILITY_CATEGORIES =
            new RegistryBuilder<>(Keys.ABILITY_CATEGORIES).sync(true).create();
    public static final Registry<Skill> SKILLS =
            new RegistryBuilder<>(Keys.SKILLS).sync(true).create();
    public static final Registry<SyncKey> SYNC_KEYS =
            new RegistryBuilder<>(Keys.SYNC_KEYS).sync(true).create();
    public static final Registry<DataType<?>> DATA_TYPES =
            new RegistryBuilder<>(Keys.DATA_TYPES).sync(true).create();

    public static final class Keys {
        public static final ResourceKey<Registry<AbilityCategory>> ABILITY_CATEGORIES = key("ability_category");
        public static final ResourceKey<Registry<Skill>> SKILLS = key("skill");
        public static final ResourceKey<Registry<SyncKey>> SYNC_KEYS = key("sync_key");
        public static final ResourceKey<Registry<DataType<?>>> DATA_TYPES = key("data_type");

        private static <T> ResourceKey<Registry<T>> key(String name) {
            return ResourceKey.createRegistryKey(AcademyCraft.academy(name));
        }

        private Keys() {
        }
    }

    private Registries() {
    }
}