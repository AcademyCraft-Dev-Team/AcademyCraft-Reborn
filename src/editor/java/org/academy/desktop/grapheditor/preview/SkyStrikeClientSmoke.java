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
    public static void screenInitialized(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (Boolean.getBoolean("academy.skyStrikeSmoke"))
            System.out.println("[sky-strike-smoke] screen " + event.getScreen().getClass().getName());
    }

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
                // The isolated client may start before this saved chunk enters its view distance.
                level.getChunk(p.getX() >> 4, p.getZ() >> 4);
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
        // Wait for the initial chunk/entity tracking setup before probing real query results.
        if (ticks == 80) mc.getSingleplayerServer().execute(() -> verifyImpactTargets(mc.getSingleplayerServer().overworld(), impact));
        if (ticks == 100) emit(SkyStrikeProfile.THUNDERCLAP, impact);
        if (ticks == 107) capture("ingame_thunderclap");
        if (ticks == 122) capture("ingame_surface_attachment");
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

    private static void verifyImpactTargets(net.minecraft.server.level.ServerLevel level, Vec3 origin) {
        var center = origin.add(0, 120, 0);
        for (var profile : SkyStrikeProfile.values()) {
            double radius = profile.ringEndRadius();
            var subjects = new java.util.ArrayList<net.minecraft.world.entity.LivingEntity>();
            try {
                var offsets = new Vec3[]{new Vec3(radius - 0.25, 0, 0), new Vec3(radius + 0.25, 0, 0),
                        new Vec3(radius * 0.8, 0, radius * 0.8), Vec3.ZERO};
                for (var offset : offsets) {
                    var subject = net.minecraft.world.entity.EntityTypes.COW.create(
                            level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    if (subject == null) throw new IllegalStateException("Cannot create radius probe");
                    subject.setPos(center.add(offset));
                    subject.setNoAi(true);
                    subject.setNoGravity(true);
                    if (!level.addFreshEntity(subject)) throw new IllegalStateException("Cannot add radius probe");
                    subjects.add(subject);
                }
                var selected = org.academy.api.server.ability.AreaEffectTargets.inSphere(level, center, radius,
                        target -> subjects.contains(target) && target != subjects.get(3));
                if (!selected.equals(java.util.List.of(subjects.getFirst()))) {
                    throw new IllegalStateException("Impact radius selected incorrect targets: " + profile
                            + " selected=" + selected.stream().map(subjects::indexOf).toList()
                            + " contained=" + subjects.stream().map(subject ->
                            org.academy.api.server.ability.AreaEffectTargets.contains(center, subject.position(), radius)).toList());
                }
                float health = subjects.getFirst().getHealth();
                for (var target : selected) target.hurtServer(level, level.damageSources().generic(), 1f);
                if (subjects.getFirst().getHealth() >= health
                        || subjects.get(1).getHealth() != subjects.get(1).getMaxHealth()
                        || subjects.get(2).getHealth() != subjects.get(2).getMaxHealth()
                        || subjects.get(3).getHealth() != subjects.get(3).getMaxHealth()) {
                    throw new IllegalStateException("Impact boundary damage check failed: " + profile);
                }
                System.out.println("[sky-strike-smoke] impact radius passed " + profile + " radius=" + radius);
            } finally {
                subjects.forEach(net.minecraft.world.entity.Entity::discard);
            }
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
