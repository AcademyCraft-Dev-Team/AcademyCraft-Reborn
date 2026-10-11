package org.academy.desktop.grapheditor.preview;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.KeyEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.terminal.TerminalHud;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.electromaster.mine.MineDetectClient;
import org.academy.internal.client.app.settings.ui.SkillSettingsApp;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv3.MineDetect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Opt-in terminal interaction and persisted client range checks in the disposable mine-detect world. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MineDetectSettingsSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static int total, ticks;
    private static int originalRadius;
    private static String originalLanguage;
    private static CompletableFuture<Void> reload;
    private static SeekBarWidget slider;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("academy.mineDetectSettingsSmoke")) return;
        var mc = Minecraft.getInstance();
        if (serverFailure != null) throw new IllegalStateException("Settings fixture failed", serverFailure);
        if (++total > 2400) throw new IllegalStateException("Settings smoke timed out");
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed to main menu")) {
                    button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                    break;
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if (!configured) {
            configured = true;
            originalRadius = MineDetect.Client.CONFIG.getScanRadius();
            originalLanguage = mc.getLanguageManager().getSelected();
            MineDetect.Client.CONFIG.setScanRadius(80);
            AcademyCraftClient.Config.INSTANCE.save();
            language("zh_cn");
            server(p -> {
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                var system = AbilitySystemServer.getSystem(p);
                system.interruptActiveSkills(p);
                system.setPlayerAbilityCategory(p.getUUID(), AbilityCategories.ELECTROMASTER.get());
                system.setPlayerLevel(p.getUUID(), 5);
                system.addPlayerSkill(p, Skills.MINE_DETECT.get().getKeyString());
                system.setPlayerSkillProficiency(p.getUUID(), Skills.MINE_DETECT.get(), 0);
                var data = system.getPlayerData(p.getUUID());
                data.setAcademyMaxCp(1000);
                data.getCpData().setMaxCP(1000);
                data.getCpData().setAvailableCP(1000);
                if (!Skills.MINE_DETECT.get().isEnabled(p)) Skills.MINE_DETECT.get().toggle(p);
                ready = true;
            });
        }
        if (!ready || reload != null && !reload.isDone() || mc.gui.screen() != null) return;
        ticks++;
        if (ticks == 30) {
            require(MineDetectClient.scan().radius() == 64, "Default radius must respect the base proficiency limit");
            openSettings();
        }
        if (ticks == 45 || ticks == 135 || ticks == 185) revealSlider();
        if (ticks == 60) {
            capture("settings-zh-default");
            press(80);
            drag(8);
        }
        if (ticks == 65) {
            require(MineDetect.Client.CONFIG.getScanRadius() == 8, "Dragging must update the selected radius");
            require(MineDetectClient.scan().radius() == 8, "Dragging must update the live scan");
            require(savedRadius() == 80, "Dragging must not write intermediate values to disk");
            capture("settings-zh-minimum");
            drag(24);
        }
        if (ticks == 70) {
            release();
            require(savedRadius() == 24, "Releasing must persist the selected radius");
            require(MineDetectClient.scan().radius() == 24, "Expanding the range must take effect");
            TerminalHud.Companion.getINSTANCE().getRoot().dispatchEvent(
                    new KeyEvent(EventType.KEY_RELEASED, InputConstants.KEY_RIGHT, 0, 0));
            require(MineDetect.Client.CONFIG.getScanRadius() == 25 && savedRadius() == 25,
                    "Keyboard adjustment must move one block and save");
        }
        if (ticks == 80) capture("settings-zh-selected");
        if (ticks == 90) {
            TerminalHud.Companion.getINSTANCE().toggleActive();
            MineDetect.Client.CONFIG.setScanRadius(8);
        }
        if (ticks == 105) {
            require(MineDetectClient.scan().radius() == 8, "Minimum radius is active");
            capture("world-radius-8");
            MineDetect.Client.CONFIG.setScanRadius(25);
        }
        if (ticks == 120) openSettings();
        if (ticks == 150) {
            require(Math.round(slider.getProgress()) == 17, "Reopened settings must retain the selected radius");
            require(savedRadius() == 25, "Fresh config loading must retain the selected radius");
            capture("settings-zh-reopened");
            TerminalHud.Companion.getINSTANCE().toggleActive();
            language("en_us");
        }
        if (ticks == 170) openSettings();
        if (ticks == 200) {
            capture("settings-en-selected");
            press(8);
            drag(80);
            release();
            require(savedRadius() == 80, "Maximum must persist after dragging");
        }
        if (ticks == 210) {
            require(MineDetectClient.scan().radius() == 64, "Client maximum cannot bypass the skill limit");
            server(p -> AbilitySystemServer.getSystem(p).setPlayerSkillProficiency(
                    p.getUUID(), Skills.MINE_DETECT.get(), 2000));
        }
        if (ticks == 240) {
            require(MineDetectClient.scan().radius() == 80, "Upgraded skill must use the configured maximum");
            capture("settings-en-maximum");
            TerminalHud.Companion.getINSTANCE().toggleActive();
            MineDetect.Client.CONFIG.setScanRadius(originalRadius);
            AcademyCraftClient.Config.INSTANCE.save();
            language(originalLanguage);
            Files.writeString(output().resolve("settings-result.txt"),
                    "PASS: actual terminal drag 80 -> 8 -> 24; live scan changes; save only on release; "
                    + "keyboard +1 -> 25; reopen and fresh config load; zh/en layouts; base limit 64 and upgraded limit 80.\n");
            System.out.println("[mine-settings-smoke] PASS");
        }
        if (ticks == 250) mc.stop();
    }

    private static void openSettings() throws Exception {
        var terminal = TerminalHud.Companion.getINSTANCE();
        terminal.toggleActive();
        require(TerminalHud.Companion.isActive(), "Terminal must be active after opening");
        var field = TerminalHud.class.getDeclaredField("context");
        field.setAccessible(true);
        ((TerminalHud.Context) field.get(terminal)).openApp(SkillSettingsApp.INSTANCE);
    }

    private static void revealSlider() {
        var root = TerminalHud.Companion.getINSTANCE().getRoot();
        var label = find(root, widget -> widget instanceof TextWidget text && text.getText().equals(
                Language.getInstance().getOrDefault("app.academy.skill_settings.advanced.mine_detect.scan_radius")));
        require(label != null, "Range row must exist in skill advanced settings");
        slider = (SeekBarWidget) label.getParent().getChildren().get("slider");
        Widget ancestor = label;
        while (!(ancestor instanceof ScrollPanelWidget)) ancestor = ancestor.getParent();
        var panel = (ScrollPanelWidget) ancestor;
        panel.setScrollTarget(panel.getScrollY() + label.getAbsoluteY() - panel.getAbsoluteY() - 110);
    }

    private static Widget find(Widget root, Predicate<Widget> predicate) {
        if (predicate.test(root)) return root;
        if (root instanceof WidgetContainer parent) for (var child : parent.getChildren().values()) {
            var result = find(child, predicate);
            if (result != null) return result;
        }
        return null;
    }

    private static double x(int radius) { return slider.getAbsoluteX() + slider.getWidth() * (radius - 8) / 72.0; }
    private static double y() { return slider.getAbsoluteY() + slider.getHeight() / 2.0; }
    private static void press(int radius) {
        require(TerminalHud.Companion.isActive(), "Terminal must stay active during interaction");
        require(slider.getWidth() > 0 && slider.getHeight() > 0, "Slider must be laid out");
        // Stay inside the hit rectangle at the maximum endpoint.
        TerminalHud.Companion.getINSTANCE().getRoot().dispatchEvent(MouseEvent.Companion.createPressEvent(
                Math.min(x(radius), slider.getAbsoluteX() + slider.getWidth() - 0.01), y(), 0));
    }
    private static void drag(int radius) {
        TerminalHud.Companion.getINSTANCE().getRoot().dispatchEvent(
                MouseEvent.Companion.createDragEvent(x(radius), y(), 0, 0, 0));
    }
    private static void release() {
        TerminalHud.Companion.getINSTANCE().getRoot().dispatchEvent(MouseEvent.Companion.createReleaseEvent(x(24), y(), 0));
    }
    private static int savedRadius() {
        MineDetect.Client.Config config = new AcademyCraftConfig(AcademyCraftClient.Config.CLIENT_CONFIG_FILE)
                .getConfig(Skills.MINE_DETECT.get().getKey());
        return config.getScanRadius();
    }
    private static void language(String code) {
        var mc = Minecraft.getInstance();
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        reload = mc.reloadResourcePacks();
    }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static Path output() { return Path.of(System.getProperty("academy.mineDetectOutput")); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void capture(String name) {
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output());
                image.writeToFile(output().resolve(name + ".png"));
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
