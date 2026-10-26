package org.academy.api.common.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import org.academy.api.common.structure.StructureBlockSummary;
import org.academy.api.common.structure.upgrade.StructureProvenance;

import java.util.Arrays;
import java.util.List;

/** Reusable read-only selection predicates; matching never grants control or ownership. */
public final class ProgramStructureFilters {
    private ProgramStructureFilters() {}

    public record Blocks(String selectors, String match) {
        public static final Codec<Blocks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("selectors").forGetter(Blocks::selectors),
                Codec.STRING.optionalFieldOf("match", "any").forGetter(Blocks::match)).apply(instance, Blocks::new));
        public Blocks {
            if (!List.of("any", "all").contains(match)) throw new IllegalArgumentException("Unknown block match mode");
            parse(selectors);
        }
        public boolean matches(StructureBlockSummary summary) {
            var values = parse(selectors);
            return summary.matches(state -> values.stream().anyMatch(selector -> selector.tag
                    ? state.is(TagKey.create(Registries.BLOCK, selector.id))
                    : BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(selector.id)), match.equals("all"));
        }
        private static List<Selector> parse(String selectors) {
            if (selectors == null || selectors.isBlank() || selectors.length() > 512) throw new IllegalArgumentException("Invalid block selectors");
            var tokens = Arrays.stream(selectors.split("[,;\\r\\n]+"))
                    .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
            if (tokens.isEmpty() || tokens.size() > 64) throw new IllegalArgumentException("Invalid selector count");
            return tokens.stream().map(value -> {
                boolean tag = value.startsWith("#");
                var id = Identifier.parse(tag ? value.substring(1) : value);
                if (id.getPath().isEmpty()) throw new IllegalArgumentException("Empty block identifier");
                return new Selector(id, tag);
            }).toList();
        }
    }

    public record Source(String sourceMode, String skill) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("source_mode").forGetter(Source::sourceMode),
                Codec.STRING.optionalFieldOf("skill", "academy:kinetic_throw").forGetter(Source::skill)).apply(instance, Source::new));
        public Source {
            if (!List.of("marked", "unmarked", "owned_by_caster", "skill", "fragment").contains(sourceMode)) {
                throw new IllegalArgumentException("Unknown structure source filter");
            }
            if (skill == null || skill.length() > 256) throw new IllegalArgumentException("Invalid skill identifier");
            if (Identifier.parse(skill).getPath().isEmpty()) throw new IllegalArgumentException("Empty skill identifier");
        }
        public boolean matches(ProgramStructureSource source) {
            return switch (sourceMode) {
                case "marked" -> source.metadata().isPresent();
                case "unmarked" -> source.metadata().isEmpty();
                case "owned_by_caster" -> source.ownedByCaster();
                case "skill" -> source.metadata().filter(value -> value.skillId().equals(Identifier.parse(skill))).isPresent();
                case "fragment" -> source.metadata().filter(value -> value.source() == StructureProvenance.CreationKind.SKILL_FRAGMENT).isPresent();
                default -> throw new IllegalStateException("Invalid source filter");
            };
        }
    }
    private record Selector(Identifier id, boolean tag) {}
}
