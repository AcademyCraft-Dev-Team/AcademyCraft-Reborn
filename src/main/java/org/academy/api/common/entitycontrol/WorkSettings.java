package org.academy.api.common.entitycontrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/** Immutable, validated settings shared by RTS orders, adapters and precision programs. */
public record WorkSettings(Mode mode, boolean repeat, boolean harvest, boolean replant,
                           boolean denyList, List<String> filters,
                           Optional<BlockPos> input, Optional<BlockPos> output) {
    public enum Mode {
        MINING, FARMING, LOGGING, SUGAR_CANE, CLEARING, SHEARING, MILKING, FEEDING, COLLECT;
        public boolean animals() { return this == SHEARING || this == MILKING || this == FEEDING; }
    }

    public static final Codec<WorkSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(Mode::valueOf, Mode::name).fieldOf("mode").forGetter(WorkSettings::mode),
            Codec.BOOL.fieldOf("repeat").forGetter(WorkSettings::repeat),
            Codec.BOOL.fieldOf("harvest").forGetter(WorkSettings::harvest),
            Codec.BOOL.fieldOf("replant").forGetter(WorkSettings::replant),
            Codec.BOOL.fieldOf("deny_list").forGetter(WorkSettings::denyList),
            Codec.STRING.listOf().fieldOf("filters").forGetter(WorkSettings::filters),
            BlockPos.CODEC.optionalFieldOf("input").forGetter(WorkSettings::input),
            BlockPos.CODEC.optionalFieldOf("output").forGetter(WorkSettings::output)
    ).apply(instance, WorkSettings::new));

    public WorkSettings {
        java.util.Objects.requireNonNull(mode);
        filters = List.copyOf(filters);
        if (filters.size() > 32) throw new IllegalArgumentException("Too many filters");
        for (var filter : filters) {
            var id = filter.startsWith("#") ? filter.substring(1) : filter;
            if (filter.length() > 128 || Identifier.tryParse(id) == null) {
                throw new IllegalArgumentException("Invalid block filter: " + filter);
            }
        }
        input = input.map(BlockPos::immutable);
        output = output.map(BlockPos::immutable);
    }

    public static WorkSettings defaults() {
        return new WorkSettings(Mode.FARMING, true, true, true, false, List.of(),
                Optional.empty(), Optional.empty());
    }

    public boolean matches(net.minecraft.world.item.ItemStack stack) {
        if (filters.isEmpty()) return true;
        var matched = filters.stream().anyMatch(filter -> filter.startsWith("#")
                ? stack.is(TagKey.create(Registries.ITEM, Identifier.parse(filter.substring(1))))
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(Identifier.parse(filter)));
        return denyList != matched;
    }

    public boolean matches(net.minecraft.world.entity.LivingEntity entity) {
        if (filters.isEmpty()) return true;
        var matched = filters.stream().anyMatch(filter -> filter.startsWith("#")
                ? entity.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(filter.substring(1))))
                : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(Identifier.parse(filter)));
        return denyList != matched;
    }

    public boolean matches(BlockState state) {
        if (filters.isEmpty()) return true;
        var matched = filters.stream().anyMatch(filter -> filter.startsWith("#")
                ? state.is(TagKey.create(Registries.BLOCK, Identifier.parse(filter.substring(1))))
                : BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(Identifier.parse(filter)));
        return denyList != matched;
    }
}
