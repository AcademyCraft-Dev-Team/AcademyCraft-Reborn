package org.academy.internal.client.validation;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.academy.AcademyCraft;

import java.util.Locale;

/** Confirms NeoForge's mod-loading warning screen for opt-in client smoke runs. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeWarningConfirmer {
    private static final boolean ENABLED = System.getProperties().stringPropertyNames().stream()
            .anyMatch(name -> name.startsWith("academy.") && name.toLowerCase(Locale.ROOT).contains("smoke")
                    && Boolean.getBoolean(name));

    private ClientSmokeWarningConfirmer() {
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED) return;
        confirm(event.getScreen());
    }

    public static boolean confirm(Screen screen) {
        if (screen == null) return false;
        for (var child : screen.children()) {
            if (!(child instanceof Button button)) continue;
            var text = button.getMessage().getString().toLowerCase(Locale.ROOT);
            if (text.contains("proceed") || text.contains("continue") || text.equals("yes")
                    || text.equals("ok") || text.contains("i understand")
                    || text.contains("继续") || text.contains("确定") || text.contains("接受")) {
                AcademyCraft.LOGGER.info("CLIENT_SMOKE_CONFIRM_WARNING screen={} button={}",
                        screen.getClass().getSimpleName(), text);
                button.onPress(new InputWithModifiers() {
                    @Override public int input() { return 257; }
                    @Override public int modifiers() { return 0; }
                });
                return true;
            }
        }
        return false;
    }
}
