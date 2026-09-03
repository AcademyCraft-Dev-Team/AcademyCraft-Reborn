package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.academy.AcademyCraft;

import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorProjectileCompatRegistry extends
        SimpleJsonResourceReloadListener<VectorProjectileCompatProfile> {
    private static final VectorProjectileCompatRegistry INSTANCE =
            new VectorProjectileCompatRegistry();
    private static volatile Set<Identifier> ownerlessPeerCollisionSuppressedTypes = Set.of();

    private VectorProjectileCompatRegistry() {
        super(
                VectorProjectileCompatProfile.CODEC,
                FileToIdConverter.json("academy_vector_projectile_compat")
        );
    }

    @SubscribeEvent
    public static void registerReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(AcademyCraft.academy("vector_projectile_compat_profiles"), INSTANCE);
    }

    public static boolean suppressesOwnerlessPeerCollision(EntityType<?> type) {
        return type != null && ownerlessPeerCollisionSuppressedTypes.contains(
                BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    @Override
    protected void apply(
            Map<Identifier, VectorProjectileCompatProfile> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        ownerlessPeerCollisionSuppressedTypes = prepared.values().stream()
                .filter(VectorProjectileCompatProfile::suppressOwnerlessPeerCollision)
                .flatMap(profile -> profile.entityTypes().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        AcademyCraft.LOGGER.info(
                "Loaded {} vector projectile compatibility collision guards",
                ownerlessPeerCollisionSuppressedTypes.size());
    }
}
