package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.academy.AcademyCraft;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorCompatProfileRegistry extends SimpleJsonResourceReloadListener<VectorCompatProfile> {
    private static final VectorCompatProfileRegistry INSTANCE = new VectorCompatProfileRegistry();
    private static volatile List<ProfileEntry> profiles = List.of();
    private static volatile VectorCompatibilityMode mode = VectorCompatibilityMode.SAFE;

    private VectorCompatProfileRegistry() {
        super(
                VectorCompatProfile.CODEC,
                FileToIdConverter.json("academy_vector_compat")
        );
    }

    @SubscribeEvent
    public static void registerReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(AcademyCraft.academy("vector_compat_profiles"), INSTANCE);
    }

    public static Optional<ProfileEntry> find(DamageSource source) {
        return profiles.stream()
                .filter(entry -> entry.profile().matches(source))
                .findFirst();
    }

    public static List<ProfileEntry> profiles() {
        return profiles;
    }

    public static VectorCompatibilityMode mode() {
        return mode;
    }

    public static void setMode(VectorCompatibilityMode newMode) {
        mode = newMode == null ? VectorCompatibilityMode.SAFE : newMode;
    }

    @Override
    protected void apply(
            Map<Identifier, VectorCompatProfile> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        profiles = prepared.entrySet().stream()
                .map(entry -> new ProfileEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .<ProfileEntry>comparingInt(entry -> entry.profile().deny() ? 1 : 0)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt((ProfileEntry entry) -> entry.profile().priority())
                                        .reversed()
                        ))
                .toList();
        AcademyCraft.LOGGER.info("Loaded {} vector compatibility profiles", profiles.size());
    }

    public record ProfileEntry(Identifier id, VectorCompatProfile profile) {
    }
}
