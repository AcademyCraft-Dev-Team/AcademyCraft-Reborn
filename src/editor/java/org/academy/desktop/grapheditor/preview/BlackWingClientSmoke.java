package org.academy.desktop.grapheditor.preview;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.render.vfx.WingVfx;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv5.BlackWing;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** Isolated real-client render/network smoke; editor source set only, never shipped in the mod. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class BlackWingClientSmoke {
    private static boolean opened, configured;
    private static int ticks, loadingTicks;
    private static volatile Vec3 center;
    private static final ArrayList<ServerPlayer> actors = new ArrayList<>();
    private static int maxVisible;
    private static boolean culled, reentered;

    private static void enable(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(player.getUUID(), 5);
        system.addPlayerSkill(player, Skills.BLACK_WING.get().getKeyString());
        var data = system.getPlayerData(player.getUUID());
        data.setAcademyMaxCp(1_000_000);
        data.getCpData().setMaxCP(1_000_000);
        data.getCpData().setAvailableCP(1_000_000);
        if (!Skills.BLACK_WING.get().isEnabled(player)) Skills.BLACK_WING.get().toggle(player);
        player.setNoGravity(true);
        player.setInvulnerable(true);
        player.setGameMode(GameType.CREATIVE);
    }

    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.blackWingSmoke")) return;
        // Embedded connections have no movement packets to drive ServerPlayer.doTick().
        for (var actor : actors) actor.doTick();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.blackWingSmoke")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (++loadingTicks > 1800) { System.out.println("[black-wing-smoke] loading timeout " + mc.gui.screen()); mc.stop(); return; }
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) {
                    if (widget instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getString().equals("Proceed to main menu")) {
                        button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                                new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                        break;
                    }
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                System.out.println("[black-wing-smoke] opening isolated world");
                mc.createWorldOpenFlows().openWorld("black_wing", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        float yaw = ticks >= 345 && ticks < 370 ? 0 : 180;
        mc.player.setYRot(yaw); mc.player.setXRot(ticks < 180 ? 0 : 5);
        mc.player.yBodyRot = yaw; mc.player.yBodyRotO = yaw;
        mc.player.yRotO = yaw; mc.player.xRotO = ticks < 180 ? 0 : 5;
        if (!configured) {
            configured = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(id);
                var level = player.level(); level.getChunk(0, 10);
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 175) + 28;
                center = new Vec3(0, y, 175);
                player.teleportTo(level, center.x, center.y, center.z, Set.of(), 180, 0, false);
                enable(player);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                System.out.println("[black-wing-smoke] ready");
            });
        }
        if (center == null) return;
        ticks++;
        if (ticks == 60) capture("hero_idle");
        if (ticks >= 70 && ticks < 150 && (ticks - 70) % 16 == 0) {
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> BlackWing.Server.onLeftClickSwing(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
        }
        if (ticks >= 78 && ticks < 158 && (ticks - 78) % 16 == 0) capture("hero_attack_" + ((ticks - 78) / 16));
        if (ticks == 180) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var viewer = server.getPlayerList().getPlayer(id);
                BlackWing.Server.forceDeactivate(viewer);
                viewer.setGameMode(GameType.SPECTATOR);
                viewer.teleportTo(viewer.level(), center.x, center.y + 4, center.z + 42, Set.of(), 180, 5, false);
                for (int i = 0; i < 8; i++) {
                    var profile = new GameProfile(UUID.randomUUID(), "wing-probe-" + i);
                    var cookie = CommonListenerCookie.createInitial(profile, false);
                    var actor = new ServerPlayer(server, viewer.level(), profile, cookie.clientInformation()) { };
                    var connection = new Connection(PacketFlow.SERVERBOUND);
                    new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
                    server.getPlayerList().placeNewPlayer(connection, actor, cookie);
                    actor.connection.markClientLoaded();
                    actor.teleportTo(viewer.level(), center.x + (i % 4 - 1.5) * 11,
                            center.y, center.z - (i / 4) * 14, Set.of(), 0, 0, false);
                    enable(actor); actors.add(actor);
                }
                System.out.println("[black-wing-smoke] eight tracked server players ready");
            });
        }
        if (ticks == 220 || ticks == 280) {
            for (var player : mc.level.players()) System.out.println("[black-wing-smoke] client actor " + player.getName().getString()
                    + " position=" + player.position() + " active=" + player.getData(org.academy.internal.common.attachment.AttachmentTypes.ACTIVATED_BLACK_WING.get()));
            mc.getSingleplayerServer().execute(() -> {
                for (var actor : actors) System.out.println("[black-wing-smoke] server actor " + actor.getName().getString()
                        + " position=" + actor.position() + " active=" + BlackWing.Server.isActive(actor)
                        + " enabled=" + Skills.BLACK_WING.get().isEnabled(actor) + " ticks=" + actor.tickCount
                        + " connected=" + !actor.hasDisconnected());
            });
        }
        if (ticks == 245) capture("eight_idle");
        if (ticks >= 260 && ticks < 340 && (ticks - 260) % 16 == 0)
            mc.getSingleplayerServer().execute(() -> actors.forEach(BlackWing.Server::onLeftClickSwing));
        if (ticks >= 268 && ticks < 348 && (ticks - 268) % 16 == 0) capture("eight_attack_" + ((ticks - 268) / 16));
        var diagnostics = WingVfx.blackDiagnostics();
        maxVisible = Math.max(maxVisible, diagnostics.visibleEffects());
        if (ticks % 20 == 0) System.out.println("[black-wing-smoke] tick=" + ticks + " " + diagnostics);
        if (ticks == 365) culled = diagnostics.visibleEffects() == 0 && diagnostics.reservedVertices() == 0;
        if (ticks == 390) reentered = diagnostics.visibleEffects() == 8;
        if (ticks == 400) {
            boolean passed = maxVisible >= 8 && diagnostics.started() == 40 && diagnostics.duplicate() == 0
                    && diagnostics.coalesced() == 0 && diagnostics.expired() == 0 && culled && reentered;
            System.out.println("[black-wing-smoke] completed maxVisible=" + maxVisible + " culled=" + culled
                    + " reentered=" + reentered + " passed=" + passed);
            if (!passed) throw new IllegalStateException("Black-wing client smoke did not satisfy playback/culling assertions");
            mc.getSingleplayerServer().execute(() -> {
                var players = mc.getSingleplayerServer().getPlayerList();
                actors.forEach(players::remove); actors.clear();
            });
        }
        if (ticks == 415) mc.stop();
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        var path = Path.of(System.getProperty("academy.blackWingSmokeOutput"), name + ".png");
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(path.getParent()); image.writeToFile(path); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            System.out.println("[black-wing-smoke] captured " + name);
        });
    }
}
