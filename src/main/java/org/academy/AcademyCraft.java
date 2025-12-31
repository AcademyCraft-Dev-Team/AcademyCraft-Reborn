package org.academy;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.academy.internal.client.data.AcademyCraftClientData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(AcademyCraft.MODID)
public final class AcademyCraft {
    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static final String MODID = "academy";
    public static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();
    public static final String MOD_ID = "academy";
    public static final String MOD_NAME = "AcademyCraft";
    public static boolean DEBUG_UI = false;

    public AcademyCraft(IEventBus modEventBus) {
        AcademyCraftRegister.register(modEventBus);
        modEventBus.addListener(AcademyCraftClientData::dataSetup);
    }

    public static Identifier custom(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Identifier vanilla(String name) {
        return Identifier.withDefaultNamespace(name);
    }

    public static Identifier academy(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }

    public static Logger getLogger() {
        return LoggerFactory.getLogger("academy/" + STACK_WALKER.getCallerClass().getSimpleName());
    }
}