package org.academy.api.client.render.vfx;

import org.academy.api.common.profiler.AcademyProfiler;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class VfxRegistry {
    private static final Map<Class<?>, Registration<?>> REGISTRATIONS = new HashMap<>();

    private VfxRegistry() {
    }

    public static <T extends VfxRenderData> void register(Class<T> type, VfxPhase phase, VfxRenderer<? super T> renderer) {
        REGISTRATIONS.put(type, new Registration<>(type, phase, renderer));
    }

    @Nullable
    public static Registration<?> find(Class<?> type) {
        return REGISTRATIONS.get(type);
    }

    public static Collection<Registration<?>> entries() {
        return REGISTRATIONS.values();
    }

    public static void forEachRenderer(VfxRendererConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (var registration : REGISTRATIONS.values()) {
            consumer.accept(registration.renderer);
        }
    }

    public static void renderPhase(VfxPhase phase, VfxFrameData frameData, VfxRenderContext ctx) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(frameData, "frameData");
        Objects.requireNonNull(ctx, "ctx");
        var phaseName = phase.name().toLowerCase(Locale.ROOT);
        for (var registration : REGISTRATIONS.values()) {
            if (registration.phase() != phase) continue;
            var rendererName = registration.renderer().getClass().getSimpleName();
            AcademyProfiler.runZone(
                    "academy.vfx." + phaseName + "." + rendererName,
                    () -> registration.render(ctx, frameData)
            );
        }
    }

    @FunctionalInterface
    public interface VfxRendererConsumer {
        void accept(VfxRenderer<?> renderer);
    }

    public record Registration<T extends VfxRenderData>(
            Class<T> dataType,
            VfxPhase phase,
            VfxRenderer<? super T> renderer
    ) {
        public void render(VfxRenderContext ctx, VfxFrameData frameData) {
            var bucket = frameData.get(dataType);
            if (bucket == null || bucket.isEmpty()) return;
            renderer.render(ctx, bucket);
        }
    }
}
