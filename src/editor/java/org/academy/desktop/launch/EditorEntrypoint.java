package org.academy.desktop.launch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.startup.Entrypoint;
import net.neoforged.fml.startup.FatalErrorReporting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EditorEntrypoint extends Entrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorEntrypoint.class);
    private static final String DEFAULT_MAIN =
            System.getProperty("academy.desktop.main", "org.academy.desktop.grapheditor.GraphEditorMainKt");

    private EditorEntrypoint() {
    }

    static void main(String[] args) {
        try (var startupResult = startup(args, false, Dist.CLIENT, true)) {
            var main = createMainMethodCallable(startupResult, DEFAULT_MAIN);
            main.invokeExact(startupResult.loader().getProgramArgs().getArguments());
        } catch (Throwable t) {
            LOGGER.error("Fatal error launching desktop editor", t);
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}
