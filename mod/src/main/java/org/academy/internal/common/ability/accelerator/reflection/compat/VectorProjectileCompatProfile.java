package org.academy.internal.common.ability.accelerator.reflection.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

public record VectorProjectileCompatProfile(
        List<Identifier> entityTypes,
        boolean suppressOwnerlessPeerCollision
) {
    public static final Codec<VectorProjectileCompatProfile> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.listOf().optionalFieldOf("entity_type", List.of())
                            .forGetter(VectorProjectileCompatProfile::entityTypes),
                    Codec.BOOL.optionalFieldOf("suppress_ownerless_peer_collision", false)
                            .forGetter(VectorProjectileCompatProfile::suppressOwnerlessPeerCollision)
            ).apply(instance, VectorProjectileCompatProfile::new));

    public VectorProjectileCompatProfile {
        entityTypes = entityTypes == null ? List.of() : entityTypes.stream().distinct().toList();
    }
}
