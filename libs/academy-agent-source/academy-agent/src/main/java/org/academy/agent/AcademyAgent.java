package org.academy.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AcademyAgent {
    private static final String INSTALLED_PROPERTY = "academy.agent.installed";
    private static final String HANDLER_CLASS = "org.academy.internal.coremod.AcademyAgentEntrypoint";
    private static final AtomicBoolean HANDOFF_DONE = new AtomicBoolean(false);
    private static final AtomicBoolean HANDOFF_THREAD_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean TRANSFORMER_REGISTERED = new AtomicBoolean(false);

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        install(instrumentation, agentArgs, false);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        install(instrumentation, agentArgs, true);
    }

    private static void install(Instrumentation instrumentation, String agentArgs,
                                boolean handlerMayAlreadyBeLoaded) {
        System.setProperty(INSTALLED_PROPERTY, "true");
        var handler = handlerClassName(agentArgs);
        var internalName = handler.replace('.', '/');
        if (handlerMayAlreadyBeLoaded) {
            instrumentation.addTransformer(
                    new HandoffTransformer(instrumentation, agentArgs, internalName), true);
            tryHandoff(instrumentation, agentArgs);
        } else {
            // During premain the JDK is still initializing its class-loader tables. Calling
            // addTransformer() here can race with the JDWP debugger agent's
            // findClass("java/lang/Class") during cbEarlyVMInit and produce
            // AGENT_ERROR_JNI_EXCEPTION(184) on JBR/JDK 25. Defer the registration until the
            // VM has finished early initialization; the handler class is not loadable until
            // NeoForge wires the mods class loader anyway.
            scheduleTransformerRegistration(instrumentation, agentArgs, internalName);
        }
    }

    private static void scheduleTransformerRegistration(Instrumentation instrumentation,
                                                        String agentArgs, String internalName) {
        var thread = new Thread(() -> {
            if (TRANSFORMER_REGISTERED.compareAndSet(false, true)) {
                try {
                    instrumentation.addTransformer(
                            new HandoffTransformer(instrumentation, agentArgs, internalName), true);
                } catch (Throwable error) {
                    TRANSFORMER_REGISTERED.set(false);
                    System.err.println("[AcademyAgent] Transformer registration failed: " + error);
                }
            }
        }, "Academy-Agent-Transformer-Register");
        thread.setDaemon(true);
        thread.start();
    }

    private static String handlerClassName(String agentArgs) {
        if (agentArgs == null || agentArgs.isBlank()) return HANDLER_CLASS;
        var separator = agentArgs.indexOf(';');
        return (separator < 0 ? agentArgs : agentArgs.substring(0, separator)).trim();
    }

    private static void scheduleHandoff(Instrumentation instrumentation, String agentArgs) {
        if (!HANDOFF_THREAD_STARTED.compareAndSet(false, true)) return;
        var thread = new Thread(() -> {
            for (var attempt = 0; attempt < 200 && !HANDOFF_DONE.get(); attempt++) {
                tryHandoff(instrumentation, agentArgs);
                if (HANDOFF_DONE.get()) return;
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            HANDOFF_THREAD_STARTED.set(false);
        }, "Academy-Agent-Handoff");
        thread.setDaemon(true);
        thread.start();
    }

    private static void tryHandoff(Instrumentation instrumentation, String agentArgs) {
        if (HANDOFF_DONE.get()) return;
        var handlerName = handlerClassName(agentArgs);
        Class<?> handlerClass = null;
        for (var loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded != null && handlerName.equals(loaded.getName())) {
                handlerClass = loaded;
                break;
            }
        }
        if (handlerClass == null || !HANDOFF_DONE.compareAndSet(false, true)) return;
        try {
            handlerClass.getMethod("agent", String.class, Instrumentation.class)
                    .invoke(null, agentArgs == null ? "" : agentArgs, instrumentation);
            System.setProperty("academy.agent.handoff", "true");
        } catch (Throwable error) {
            HANDOFF_DONE.set(false);
            System.err.println("[AcademyAgent] Handoff failed: " + error);
        }
    }

    private record HandoffTransformer(
            Instrumentation instrumentation,
            String agentArgs,
            String handlerInternalName
    ) implements ClassFileTransformer {
        @Override
        public byte[] transform(Module module, ClassLoader loader, String className,
                                Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if (Objects.equals(className, handlerInternalName)) {
                scheduleHandoff(instrumentation, agentArgs);
            }
            return null;
        }
    }

    private AcademyAgent() {
    }
}
