package org.academy.internal.coremod.transform;

import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Bytecode-only processors loaded before the game module layer is created. */
public final class AcademyCoremodProvider implements ClassProcessorProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcademyCoremodProvider.class);

    @Override
    public void createProcessors(Context context, Collector collector) {
        collector.add(new TemporalBoundaryProcessor());
        collector.add(new WorldWeaverConfigProcessor());
        collector.add(new HealthReadProcessor());
    }

    private abstract static class AfterMixinProcessor extends SimpleClassProcessor {
        @Override
        public Set<ProcessorName> runsAfter() {
            return Set.of(ClassProcessorIds.SIMPLE_PROCESSORS_GROUP,
                    ClassProcessorIds.COMPUTING_FRAMES, ClassProcessorIds.MIXIN);
        }
    }

    private static final class TemporalBoundaryProcessor extends AfterMixinProcessor {
        private static final Set<Target> TARGETS = Set.of(
                new Target("net.minecraft.server.level.ServerLevel"),
                new Target("net.minecraft.client.multiplayer.ClientLevel"),
                new Target("net.minecraft.client.KeyboardHandler"),
                new Target("net.minecraft.client.MouseHandler"),
                new Target("org.academy.api.client.input.InputSystem"));

        @Override
        public ProcessorName name() {
            return new ProcessorName("academy", "temporal_boundary");
        }

        @Override
        public Set<Target> targets() {
            return TARGETS;
        }

        @Override
        public void transform(ClassNode input, SimpleTransformationContext context) {
            TemporalBoundaryTransformer.apply(input);
            LOGGER.debug("Applied temporal boundary processor to {}", input.name);
        }
    }

    private static final class WorldWeaverConfigProcessor extends AfterMixinProcessor {
        private static final Set<Target> TARGETS = Set.of(
                new Target("net.minecraft.world.level.levelgen.feature.EndSpikeFeature"),
                new Target("net.minecraft.world.level.levelgen.feature.EndSpikeFeature$EndSpike"),
                new Target("net.minecraft.world.level.levelgen.feature.EndPodiumFeature"));

        @Override
        public ProcessorName name() {
            return new ProcessorName("academy", "world_weaver_config");
        }

        @Override
        public Set<Target> targets() {
            return TARGETS;
        }

        @Override
        public void transform(ClassNode input, SimpleTransformationContext context) {
            var changed = WorldWeaverConfigTransformer.apply(input);
            LOGGER.debug("Applied WorldWeaver config processor to {} ({} calls)", input.name, changed);
        }
    }

    private static final class HealthReadProcessor extends AfterMixinProcessor {
        private static final Set<Target> TARGETS = Set.of(
                new Target("net.minecraft.world.entity.LivingEntity"));

        @Override
        public ProcessorName name() {
            return new ProcessorName("academy", "health_read_inliner");
        }

        @Override
        public Set<Target> targets() {
            return TARGETS;
        }

        @Override
        public void transform(ClassNode input, SimpleTransformationContext context) {
            HealthReadInliner.apply(input);
            LOGGER.debug("Applied health read processor to {}", input.name);
        }
    }
}
