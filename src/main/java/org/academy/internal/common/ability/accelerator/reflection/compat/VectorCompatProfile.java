package org.academy.internal.common.ability.accelerator.reflection.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;
import java.util.Locale;

public record VectorCompatProfile(
        boolean deny,
        List<String> damageTypes,
        List<String> directEntityTypes,
        Shape shape,
        DirectionMode direction,
        double range,
        double radius,
        boolean piercing,
        boolean continuous,
        boolean safeMotionRedirect,
        VectorVisualStyle visual,
        VectorBlockPolicy blockPolicy,
        int priority
) {
    public static final Codec<VectorCompatProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("deny", false).forGetter(VectorCompatProfile::deny),
            Codec.STRING.listOf().optionalFieldOf("damage_type", List.of()).forGetter(VectorCompatProfile::damageTypes),
            Codec.STRING.listOf().optionalFieldOf("direct_entity", List.of()).forGetter(VectorCompatProfile::directEntityTypes),
            enumCodec(Shape.class).optionalFieldOf("shape", Shape.HITSCAN).forGetter(VectorCompatProfile::shape),
            enumCodec(DirectionMode.class).optionalFieldOf("direction", DirectionMode.AUTO).forGetter(VectorCompatProfile::direction),
            Codec.DOUBLE.optionalFieldOf("range", VectorExecutionPolicy.DEFAULT_MAXIMUM_RANGE).forGetter(VectorCompatProfile::range),
            Codec.DOUBLE.optionalFieldOf("radius", 0.25).forGetter(VectorCompatProfile::radius),
            Codec.BOOL.optionalFieldOf("piercing", false).forGetter(VectorCompatProfile::piercing),
            Codec.BOOL.optionalFieldOf("continuous", false).forGetter(VectorCompatProfile::continuous),
            Codec.BOOL.optionalFieldOf("safe_motion_redirect", false).forGetter(VectorCompatProfile::safeMotionRedirect),
            enumCodec(VectorVisualStyle.class).optionalFieldOf("visual", VectorVisualStyle.ENERGY).forGetter(VectorCompatProfile::visual),
            enumCodec(VectorBlockPolicy.class).optionalFieldOf("block_policy", VectorBlockPolicy.CLIP_NO_BREAK).forGetter(VectorCompatProfile::blockPolicy),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(VectorCompatProfile::priority)
    ).apply(instance, VectorCompatProfile::new));

    public VectorCompatProfile {
        damageTypes = normalize(damageTypes);
        directEntityTypes = normalize(directEntityTypes);
        shape = shape == null ? Shape.HITSCAN : shape;
        direction = direction == null ? DirectionMode.AUTO : direction;
        visual = visual == null ? VectorVisualStyle.ENERGY : visual;
        blockPolicy = blockPolicy == null ? VectorBlockPolicy.CLIP_NO_BREAK : blockPolicy;
        if (!Double.isFinite(range) || range <= 0.0) range = VectorExecutionPolicy.DEFAULT_MAXIMUM_RANGE;
        range = Math.min(range, VectorExecutionPolicy.HARD_MAXIMUM_RANGE);
        if (!Double.isFinite(radius) || radius < 0.0) radius = 0.25;
        radius = Math.min(radius, 8.0);
    }

    public static String damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.identifier().toString().toLowerCase(Locale.ROOT))
                .orElseGet(() -> source.getMsgId().toLowerCase(Locale.ROOT));
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(
                value -> Enum.valueOf(type, value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT)
        );
    }

    public boolean matches(DamageSource source) {
        if (damageTypes.isEmpty() && directEntityTypes.isEmpty()) return false;
        var damageTypeId = damageTypeId(source);
        var damageMatches = damageTypes.isEmpty()
                || damageTypes.contains(damageTypeId)
                || damageTypes.contains(source.getMsgId().toLowerCase(Locale.ROOT));
        if (!damageMatches) return false;
        var direct = source.getDirectEntity();
        if (directEntityTypes.isEmpty()) return true;
        if (direct == null) return false;
        var directId = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString();
        return directEntityTypes.contains(directId.toLowerCase(Locale.ROOT));
    }

    public VectorExecutionPolicy executionPolicy() {
        return new VectorExecutionPolicy(
                piercing,
                continuous,
                safeMotionRedirect,
                blockPolicy,
                visual,
                piercing ? VectorExecutionPolicy.HARD_MAXIMUM_TARGETS : 1,
                range
        );
    }

    public enum Shape {
        HITSCAN
    }

    public enum DirectionMode {
        AUTO,
        SOURCE_POSITION,
        DIRECT_MOTION,
        ATTACKER_LOOK,
        ATTACKER_TO_DEFENDER
    }
}
