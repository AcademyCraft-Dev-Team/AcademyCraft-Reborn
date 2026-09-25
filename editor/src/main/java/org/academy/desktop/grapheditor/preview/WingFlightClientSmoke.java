package org.academy.desktop.grapheditor.preview;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.LoadingErrorScreen;
import org.academy.api.client.config.SkillSettingsRegistry;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.Skill;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;
import org.academy.internal.common.ability.accelerator.skills.lv4.StormWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.WhiteWing;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlatinumWing;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.misaka.MisakaNetworkClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Real input/travel regression in an isolated world; not included in the distributed mod. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WingFlightClientSmoke {
    private static boolean opened, requested;
    private static int totalTicks, index, stage;
    private static volatile boolean stalled, resumed;
    private static Vec3 stoppedAt;

    private static Skill skill() {
        return switch (index) {
            case 0 -> Skills.STORM_WING.get();
            case 1 -> Skills.BLACK_WING.get();
            case 2 -> Skills.WHITE_WING.get();
            default -> Skills.PLATINUM_WING.get();
        };
    }

    private static void key(int key, boolean pressed) {
        InputSystem.handleKey(pressed ? InputConstants.PRESS : InputConstants.RELEASE,
                new KeyEvent(key, 0, 0), new CallbackInfo("wingSmoke", true));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.wingFlightSmoke")) return;
        var mc = Minecraft.getInstance();
        if (++totalTicks > 2400) throw new IllegalStateException("Wing flight smoke timed out, stage=" + stage);
        if (mc.level == null) {
            if (mc.gui.screen() instanceof LoadingErrorScreen warning) {
                for (var widget : warning.children()) {
                    if (widget instanceof Button button
                            && button.getMessage().getString().equals("Proceed to main menu")) {
                        button.onPress(new MouseButtonEvent(0, 0,
                                new MouseButtonInfo(0, 0)));
                        break;
                    }
                }
            }
            if (!opened && mc.gui.screen() instanceof TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().openWorld("black_wing", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        mc.getTutorial().setStep(TutorialSteps.NONE);
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        if (!requested) {
            requested = true;
            stage = 0;
            stalled = false;
            resumed = false;
            var selected = skill();
            // White/platinum are unfinished and hidden in production. Expose them only in this test JVM.
            try {
                var hidden = Skill.class.getDeclaredField("hidden");
                hidden.setAccessible(true);
                hidden.setBoolean(selected, false);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            var modules = SkillSettingsRegistry.getModules(selected);
            if (modules.stream().noneMatch(module -> module.id().equals("wing_flight")))
                throw new IllegalStateException("Missing flight settings for " + selected);
            StormWing.Client.CONFIG.setRemainingMomentum(0);
            BlackWing.Client.CONFIG.setRemainingMomentum(0);
            WhiteWing.Client.CONFIG.setRemainingMomentum(0);
            PlatinumWing.Client.CONFIG.setRemainingMomentum(0);
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                StormWing.Server.forceDeactivate(player);
                BlackWing.Server.forceDeactivate(player);
                WhiteWing.Server.forceDeactivate(player);
                PlatinumWing.Server.forceDeactivate(player);
                var system = AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get());
                system.setPlayerLevel(id, 5);
                system.addPlayerSkill(player, selected.getKeyString());
                var data = system.getPlayerData(id);
                data.setAcademyMaxCp(1_000_000);
                data.getCpData().setMaxCP(1_000_000);
                data.getCpData().setAvailableCP(1_000_000);
                player.setGameMode(GameType.SURVIVAL);
                player.setPermanentlyInvulnerable(true);
                player.connection.teleport(0, 180, 175, 0, 0);
                player.setDeltaMovement(Vec3.ZERO);
                selected.toggle(player);
            });
            return;
        }
        if (stage == 0) {
            boolean active = switch (index) {
                case 0 -> mc.player.getData(AttachmentTypes.ACTIVATED_STORM_WING.get());
                case 1 -> mc.player.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get());
                case 2 -> mc.player.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get());
                default -> mc.player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING.get());
            };
            if (!active) return;
            key(InputConstants.KEY_W, true);
            stage = 1;
        }
        stage++;
        if (stage == 8) {
            if (mc.player.getDeltaMovement().lengthSqr() < .01) throw new IllegalStateException("No local thrust");
            key(InputConstants.KEY_W, false);
            key(InputConstants.KEY_SPACE, true);
        }
        if (stage == 12) {
            mc.getSingleplayerServer().execute(() -> {
                stalled = true;
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                resumed = true;
            });
        }
        if (stage == 13 && !stalled) { stage--; return; }
        if (stage == 14) key(InputConstants.KEY_SPACE, false);
        if (stage == 16) stoppedAt = mc.player.position();
        if (stage == 20) {
            if (resumed) throw new IllegalStateException("Stall did not overlap local release");
            if (mc.player.getDeltaMovement().lengthSqr() > 1e-10
                    || mc.player.position().distanceToSqr(stoppedAt) > 1e-8)
                throw new IllegalStateException("Wing fell or drifted while server stalled: " + mc.player.getDeltaMovement());
            if (mc.player.getData(AttachmentTypes.WING_FLIGHT_POSE.get()) != WingFlightPose.Pose.IDLE)
                throw new IllegalStateException("Flight pose did not finish during stall");
            var imagePath = Path.of("screenshots", "wing_flight_" + index + ".png");
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    Files.createDirectories(imagePath.getParent());
                    image.writeToFile(imagePath);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            switch (index) {
                case 0 -> MisakaNetworkClient.send(StormWing.TogglePacket.INSTANCE);
                case 1 -> MisakaNetworkClient.send(BlackWing.TogglePacket.INSTANCE);
                case 2 -> MisakaNetworkClient.send(WhiteWing.TogglePacket.INSTANCE);
                default -> MisakaNetworkClient.send(PlatinumWing.TogglePacket.INSTANCE);
            }
        }
        if (stage > 45 && resumed && !WingFlightPose.hasActiveWing(mc.player)) {
            System.out.println("[wing-flight-smoke] PASS " + skill().getKeyString()
                    + " local thrust, 1500ms server stall, zero momentum, hover, pose exit, toggle acknowledgement, settings");
            if (++index == 4) {
                System.out.println("[wing-flight-smoke] ALL FOUR PASSED");
                mc.stop();
            } else requested = false;
        }
    }
}
