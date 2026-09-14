package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.RailgunRay;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Opt-in network/entity/render smoke test in an isolated save; excluded from both mod jars. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class RailgunClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static int ticks, totalTicks;
    private static float yaw = 90, pitch = 5;
    private static RailgunRay earlyRemoved;
    private static int observed;
    private record Probe(java.util.UUID id, String name, float length, float width, boolean reflected) {}
    private static volatile Probe probe;
    private static final Set<String> verified = new java.util.HashSet<>();
    private static boolean awaitingHandCast;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.railgunSmoke")) return;
        var mc = Minecraft.getInstance();
        if (++totalTicks > 2400) throw new IllegalStateException("Railgun smoke timed out");
        if (mc.level == null) {
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
                mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        boolean hideHud = ticks < 550;
        if (mc.gui.hud.isHidden() != hideHud) mc.gui.hud.toggle();
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                var player = server.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, 35, 84, 195, Set.of(), yaw, pitch, false);
                for (int x = -8; x <= 8; x++) {
                    for (int z = 169; z <= 219; z++) {
                        level.setBlock(new net.minecraft.core.BlockPos(x, 80, z),
                                net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    }
                }
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 1000");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                ready = true;
                System.out.println("[railgun-smoke] ready");
            });
        }
        if (!ready) return;
        ticks++;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof RailgunRay ray) {
                observed++;
                if (awaitingHandCast) {
                    awaitingHandCast = false;
                    var offset = ray.position().subtract(mc.player.position());
                    if (Math.abs(Math.abs(offset.x) - 0.4) > 0.06 || Math.abs(offset.y - 1.2) > 0.06
                            || Math.abs(offset.z - 0.5) > 0.06) {
                        throw new IllegalStateException("Skill did not originate at the firing hand: " + offset);
                    }
                    probe = new Probe(ray.getUUID(), "ingame_hand_cast", ray.getBeamLength(), 1.5f, false);
                    System.out.println("[railgun-smoke] real skill hand offset=" + offset);
                }
                var current = probe;
                if (current != null && ray.getUUID().equals(current.id()) && ray.tickCount >= 3
                        && ray.tickCount < 12 && !verified.contains(current.name())) {
                    if (ray.getBeamLength() != current.length() || ray.getBeamWidthMultiplier() != current.width()
                            || ray.isReflectionActive() != current.reflected()) {
                        throw new IllegalStateException("Incorrect synchronized beam dimensions: " + current);
                    }
                    int expectedGraphs = current.reflected() ? 2 : 1;
                    if (VfxGraphManager.INSTANCE.frameStatistics().active() != expectedGraphs)
                        throw new IllegalStateException("Missing graph segments: " + current);
                    verified.add(current.name());
                    capture(current.name());
                    System.out.println("[railgun-smoke] verified " + current + " age=" + ray.tickCount);
                }
            }
        }
        if (ticks == 100) emit(50, 1, false, false, "ingame_normal_confirmed");
        if (ticks == 102) capture("ingame_peak");
        if (ticks == 108) capture("ingame_medium");
        if (ticks == 129) capture("ingame_afterglow");
        if (ticks == 146) assertCleared("natural expiration");
        if (ticks == 160) emit(74, 2.5f, false, false, "ingame_heavy_ammo");
        if (ticks == 210) emit(50, 1, true, false, "ingame_reflection");
        if (ticks == 260) {
            yaw = 0;
            pitch = 0;
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.teleportTo(player.level(), 0, 80.8, 174.5, Set.of(), yaw, pitch, false);
            });
        }
        if (ticks == 280) emit(50, 1, false, false, "ingame_first_person");
        if (ticks == 330) emit(50, 1, false, true, "ingame_before_removal");
        if (ticks == 335) mc.getSingleplayerServer().execute(() -> earlyRemoved.discard());
        if (ticks == 345) assertCleared("early entity removal");
        if (ticks == 370) emit(58, 1.5f, false, false, "ingame_iron_ingot");
        if (ticks == 420) emit(66, 2f, false, false, "ingame_iron_block");
        if (ticks == 470) emit(512, 6f, false, false, "ingame_extended_range");
        if (ticks == 520) assertCleared("all ammo and extended range");
        if (ticks == 550) {
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
                var skill = org.academy.internal.common.ability.Skills.RAILGUN.get();
                system.setPlayerAbilityCategory(id, org.academy.internal.common.ability.AbilityCategories.ELECTROMASTER.get());
                system.setPlayerLevel(id, 5);
                system.addPlayerSkill(player, skill.getKeyString());
                var data = system.getPlayerData(id);
                data.setAcademyMaxCp(1_000_000);
                data.getCpData().setMaxCP(1_000_000);
                data.getCpData().setAvailableCP(1_000_000);
                if (!skill.isEnabled(player)) skill.toggle(player);
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 16));
            });
        }
        if (ticks == 590) {
            var status = org.academy.api.client.ability.AbilitySystemClient.getSkillUseStatus(
                    org.academy.internal.common.ability.Skills.RAILGUN.get());
            if (!status.allowed()) throw new IllegalStateException("Real railgun unavailable: " + status);
            org.academy.internal.common.ability.electromaster.skills.lv4.Railgun.Client.start();
        }
        if (ticks == 622) {
            awaitingHandCast = true;
            org.academy.internal.common.ability.electromaster.skills.lv4.Railgun.Client.end();
        }
        if (ticks == 675) {
            capture("ingame_cleared");
            if (observed < 20) throw new IllegalStateException("Client did not receive the railgun entities");
            if (verified.size() != 9) throw new IllegalStateException("Unverified scenarios: " + verified);
            assertCleared("real skill hand cast");
            System.out.println("[railgun-smoke] PASSED: " + verified + "; observations=" + observed);
            mc.stop();
        }
    }

    private static void emit(float length, float width, boolean reflect, boolean removeEarly, String name) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var ray = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), server.overworld());
            // Match the skill's right-hand offset: 0.4 sideways, 1.2 up, 0.5 forward.
            ray.setPos(name.equals("ingame_first_person") || name.equals("ingame_iron_ingot")
                    || name.equals("ingame_iron_block") || name.equals("ingame_extended_range") ? -0.4 : 0, 82, 175);
            ray.setYRot(0);
            ray.setXRot(0);
            ray.setBeamPath(length, width, reflect, 20, reflect ? 18 : 0, new Vec3(1, 0.05, -0.4));
            server.overworld().addFreshEntity(ray);
            probe = new Probe(ray.getUUID(), name, length, width, reflect);
            if (removeEarly) earlyRemoved = ray;
            System.out.println("[railgun-smoke] emit width=" + width + " reflected=" + reflect);
        });
    }

    private static void assertCleared(String phase) {
        if (VfxGraphManager.INSTANCE.frameStatistics().active() != 0) {
            throw new IllegalStateException("Graph leak after " + phase + ": " + VfxGraphManager.INSTANCE.frameStatistics());
        }
        System.out.println("[railgun-smoke] cleared: " + phase);
    }

    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.railgunSmokeOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
                System.out.println("[railgun-smoke] capture " + output);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
