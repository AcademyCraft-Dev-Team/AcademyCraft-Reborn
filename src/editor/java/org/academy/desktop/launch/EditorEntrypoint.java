package org.academy.desktop.launch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.startup.Entrypoint;
import net.neoforged.fml.startup.FatalErrorReporting;

/**
 * FML launch entrypoint for the out-of-game desktop tools.
 * <p>
 * Mirrors {@link net.neoforged.fml.startup.Client} (bootstraps the FML loader,
 * which the NeoForge-patched vanilla classes require) but launches a desktop
 * editor main class instead of the game client. Which main to run is controlled
 * by the {@code academy.desktop.main} system property.
 */
public final class EditorEntrypoint extends Entrypoint {
    private static final String DEFAULT_MAIN =
            System.getProperty("academy.desktop.main", "org.academy.desktop.uieditor.UiEditorMainKt");

    private EditorEntrypoint() {
    }

    static void main(String[] args) {
        try (var startupResult = startup(args, false, Dist.CLIENT, true)) {
            var main = createMainMethodCallable(startupResult, DEFAULT_MAIN);
            main.invokeExact(startupResult.loader().getProgramArgs().getArguments());
        } catch (Throwable t) {
            t.printStackTrace();
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}
