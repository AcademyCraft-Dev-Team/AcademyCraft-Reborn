package org.academy.internal.server.time;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporalFieldResolverTest {
    private static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath("test", "temporal_resolver")
    );
    private static final Set<TemporalChannel> ENTITY =
            Set.of(TemporalChannel.ENTITY);

    @Test
    void applicableScopesMultiplyAndSpatialFieldsNeedPosition() {
        var fields = List.of(
                TemporalField.scale(
                        TemporalScope.dimension(DIMENSION),
                        ENTITY,
                        0.5D
                ),
                TemporalField.scale(
                        TemporalScope.sphere(DIMENSION, Vec3.ZERO, 8.0D),
                        ENTITY,
                        2.0D
                )
        );

        assertEquals(1.0D, TemporalFieldResolver.resolve(
                fields,
                DIMENSION,
                Vec3.ZERO,
                TemporalChannel.ENTITY,
                ignored -> false
        ), 1.0E-12D);
        assertEquals(0.5D, TemporalFieldResolver.resolve(
                fields,
                DIMENSION,
                null,
                TemporalChannel.ENTITY,
                ignored -> false
        ), 1.0E-12D);
    }

    @Test
    void immunitySkipsOnlyMatchingHardPauseSources() {
        var fields = List.of(
                TemporalField.scale(TemporalScope.save(), ENTITY, 0.5D),
                TemporalField.pause(
                        TemporalScope.save(),
                        ENTITY,
                        TemporalPauseSource.EXTERNAL_COMPATIBILITY
                ),
                TemporalField.pause(
                        TemporalScope.save(),
                        ENTITY,
                        TemporalPauseSource.ACADEMY_PAUSE
                )
        );

        assertEquals(0.0D, TemporalFieldResolver.resolve(
                fields,
                DIMENSION,
                Vec3.ZERO,
                TemporalChannel.ENTITY,
                source -> source == TemporalPauseSource.EXTERNAL_COMPATIBILITY
        ));
        assertEquals(0.5D, TemporalFieldResolver.resolve(
                fields,
                DIMENSION,
                Vec3.ZERO,
                TemporalChannel.ENTITY,
                source -> true
        ), 1.0E-12D);
    }

    @Test
    void entityScopeAppliesToEverySelectedSubjectAndNothingElse() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var field = TemporalField.scale(
                TemporalScope.entities(List.of(first, second)),
                ENTITY,
                0.25D
        );

        assertEquals(0.25D, TemporalFieldResolver.resolve(
                List.of(field),
                DIMENSION,
                Vec3.ZERO,
                first,
                TemporalChannel.ENTITY,
                ignored -> false
        ), 1.0E-12D);
        assertEquals(0.25D, TemporalFieldResolver.resolve(
                List.of(field),
                DIMENSION,
                Vec3.ZERO,
                second,
                TemporalChannel.ENTITY,
                ignored -> false
        ), 1.0E-12D);
        assertEquals(1.0D, TemporalFieldResolver.resolve(
                List.of(field),
                DIMENSION,
                Vec3.ZERO,
                UUID.randomUUID(),
                TemporalChannel.ENTITY,
                ignored -> false
        ), 1.0E-12D);
        assertEquals(1.0D, TemporalFieldResolver.resolve(
                List.of(field),
                DIMENSION,
                Vec3.ZERO,
                TemporalChannel.ENTITY,
                ignored -> false
        ), 1.0E-12D);
    }
}
