package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Opt-in smoke probe, only enabled by the isolated development run. Never packaged in the mod. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SkyStrikeClientSmoke {
    private static int ticks;
    private static volatile Vec3 impact;
    private static boolean configured;
    private static boolean openedWorld;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.skyStrikeSmoke")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!openedWorld && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                openedWorld = true;
                System.out.println("[sky-strike-smoke] opening isolated test world");
                mc.createWorldOpenFlows().openWorld("sky_strike", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        mc.player.setYRot(180);
        mc.player.setXRot(-10);
        mc.player.yRotO = 180;
        mc.player.xRotO = -10;

        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(id);
                if (player == null) return;
                var level = player.level();
                var p = new net.minecraft.core.BlockPos(0, 64, 175);
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
                impact = new Vec3(p.getX() + 0.5, y + 0.1, p.getZ() + 0.5);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, impact.x, y + 20, impact.z + 72, Set.of(), 180, -10, false);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
                System.out.println("[sky-strike-smoke] ready " + impact);
            });
        }
        if (impact == null) return;
        ticks++;
        if (ticks == 100) emit(SkyStrikeProfile.THUNDERCLAP, impact);
        if (ticks == 107) capture("ingame_thunderclap");
        if (ticks == 142) capture("ingame_surface_attachment");
        if (ticks >= 180 && ticks <= 240 && (ticks - 180) % 3 == 0) {
            int n = (ticks - 180) / 3;
            double angle = n * 2.399963;
            emit(SkyStrikeProfile.LIGHTNING_STORM, impact.add(Math.cos(angle) * 7, 0, Math.sin(angle) * 7));
        }
        if (ticks == 211) capture("ingame_storm");
        if (ticks == 325) capture("ingame_afterglow_cleared");
        if (ticks == 350) {
            System.out.println("[sky-strike-smoke] completed thunderclap and 21 storm strikes");
            mc.stop();
        }
    }

    private static void emit(SkyStrikeProfile profile, Vec3 point) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> ElectromasterArcEffects.spawnSkyStrike(server.overworld(), point, profile));
        System.out.println("[sky-strike-smoke] emit " + profile + " tick=" + ticks);
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        var output = Path.of(System.getProperty("academy.skyStrikeSmokeOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
                System.out.println("[sky-strike-smoke] capture " + output);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
