package org.academy.internal.coremod.transform;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class AcademyCoremodProviderTest {
    @Test
    void serviceRegistersThreeTargetedProcessorsAfterMixin() {
        var provider = ServiceLoader.load(ClassProcessorProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(AcademyCoremodProvider.class::isInstance)
                .findFirst().orElseThrow();
        var processors = new ArrayList<ClassProcessor>();
        provider.createProcessors(new ClassProcessorProvider.Context(), processors::add);

        assertEquals(3, processors.size());
        for (var processor : processors) {
            assertEquals("academy", processor.name().namespace());
            assertTrue(processor.runsAfter().contains(ClassProcessorIds.MIXIN));
            assertFalse(processor.handlesClass(new ClassProcessor.SelectionContext(
                    Type.getObjectType("example/Unrelated"), false)));
        }
        assertTrue(processors.stream().anyMatch(processor -> processor.handlesClass(
                new ClassProcessor.SelectionContext(Type.getObjectType(
                        "net/minecraft/world/entity/LivingEntity"), false))));
    }
}
