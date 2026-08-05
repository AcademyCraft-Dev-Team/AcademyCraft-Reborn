package org.academy.internal.coremod;

import java.lang.instrument.Instrumentation;

public final class AcademyAgentEntrypoint {
    public static void agent(String agentArgs, Instrumentation instrumentation) {
        VectorReflectionInstrumentation.install(instrumentation);
    }

    private AcademyAgentEntrypoint() {
    }
}
