package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in visual regression in a copied world; excluded from mod artifacts. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SkillRepairClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static int ticks, total;
    private static boolean reconnected;
    private static ServerPlayer remote;
    private static net.minecraft.world.entity.Mob subject;
    private static org.academy.api.common.entitycontrol.ControlHandle relation;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.skillRepairSmoke")) return;
        var mc = Minecraft.getInstance();
        if (failure != null) throw new IllegalStateException("Skill repair smoke failed", failure);
        if (++total > 4000) throw new IllegalStateException("Skill repair smoke timed out");
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
        mc.player.setYRot(0); mc.player.yRotO = 0;
        mc.player.setXRot(0); mc.player.xRotO = 0;
        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.guiScale().set(2);
            mc.resizeGui();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            AbilitySystemClient.setActiveHUD(true);
            server(player -> {
                var level = player.level();
                for (int x = -8; x <= 8; x++) for (int z = 170; z <= 190; z++) {
                    level.setBlock(new net.minecraft.core.BlockPos(x, 80, z),
                            net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    for (int y = 81; y <= 87; y++) level.setBlock(new net.minecraft.core.BlockPos(x, y, z),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
                level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack(), "time set 1000");
                player.getInventory().clearContent();
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(player.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
                var system = AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.DARKMATTER.get());
                system.setPlayerLevel(player.getUUID(), 5);
                for (var skill : AbilityCategories.DARKMATTER.get().getSkills())
                    system.addPlayerSkill(player, skill.getKeyString());
                var manager = system.getDarkmatterResourceManager();
                manager.reconcile(player);
                manager.setAlphaPoints(player, 200);
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        if (remote != null && ticks >= 260 && ticks < 470) {
            var entity = mc.level.getEntity(remote.getId());
            if (entity instanceof net.minecraft.world.entity.player.Player avatar) {
                avatar.setPose(ticks >= 350 && ticks < 410
                        ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
                avatar.setYRot(180); avatar.yRotO = 180;
                avatar.setYHeadRot(180); avatar.yHeadRotO = 180;
                avatar.setYBodyRot(180); avatar.yBodyRotO = 180;
            }
        }
        if (ticks == 100) capture("phase_80_20_scale2");
        if (ticks == 120) server(player -> AbilitySystemServer.getSystem(player)
                .getDarkmatterResourceManager().setAlphaPoints(player, 50));
        if (ticks == 170) capture("phase_20_80_scale2");
        if (ticks == 190) { mc.options.guiScale().set(3); mc.resizeGui(); }
        if (ticks == 230) capture("phase_20_80_scale3");
        if (ticks == 260) {
            mc.options.guiScale().set(2); mc.resizeGui();
            server(player -> {
                var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ChargeProbe");
                var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
                remote = new ServerPlayer(player.level().getServer(), player.level(), profile, cookie.clientInformation());
                var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                new io.netty.channel.embedded.EmbeddedChannel(connection);
                player.level().getServer().getPlayerList().placeNewPlayer(connection, remote, cookie);
                remote.teleportTo(player.level(), 0, 81, 178, Set.of(), 180, 0, false);
                remote.setYHeadRot(180); remote.setYBodyRot(180);
                remote.setData(org.academy.internal.common.attachment.AttachmentTypes.RAILGUN_DATA,
                        new org.academy.internal.common.ability.electromaster.skills.lv4.Railgun.Data(true, true, 40, false, false));
            });
        }
        if (ticks == 330) captureImage("charge_remote_right");
        if (ticks == 350) server(player -> remote.setShiftKeyDown(true));
        if (ticks == 390) captureImage("charge_remote_crouching");
        if (ticks == 410) server(player -> {
            remote.setShiftKeyDown(false);
            remote.setData(org.academy.internal.common.attachment.AttachmentTypes.RAILGUN_DATA,
                    new org.academy.internal.common.ability.electromaster.skills.lv4.Railgun.Data(false, true, 40, false, false));
        });
        if (ticks == 450) captureImage("charge_remote_left");
        if (ticks == 470) server(player -> {
            player.level().getServer().getPlayerList().remove(remote);
            var system = AbilitySystemServer.getSystem(player);
            system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.MENTALOUT.get());
            system.setPlayerLevel(player.getUUID(), 5);
            for (var skill : AbilityCategories.MENTALOUT.get().getSkills())
                system.addPlayerSkill(player, skill.getKeyString());
            for (var skill : java.util.List.of(Skills.MENTAL_INTERVENTION.get(), Skills.MENTAL_INTRUSION.get(), Skills.MENTAL_TAKEOVER.get()))
                if (!skill.isEnabled(player)) skill.toggle(player);
            var data = system.getPlayerData(player.getUUID());
            data.setAcademyMaxCp(1_000_000);
            data.getCpData().setMaxCP(1_000_000);
            data.getCpData().setAvailableCP(1_000_000);
            subject = net.minecraft.world.entity.EntityTypes.COW.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            subject.snapTo(0, 81, 177, 123, -12);
            subject.setYHeadRot(123); subject.setYBodyRot(123);
            subject.setPersistenceRequired();
            player.level().addFreshEntity(subject);
            var added = org.academy.internal.common.ability.mentalout.control.MentaloutControlContext.addTarget(player, subject);
            if (!added.name().equals("ADDED")) throw new IllegalStateException("Roster: " + added);
        });
        if (ticks == 510) startTakeover();
        if (ticks == 540) verifyTakeover("standing");
        if (ticks == 560) server(player -> org.academy.internal.common.ability.mentalout.control.PlayerControlSessionManager.toggle(player));
        if (ticks == 590) server(player -> {
            relation = org.academy.api.common.entitycontrol.MentalControlApi.apply(
                    org.academy.api.common.entitycontrol.ControlRequest.permanent(player, subject,
                            net.minecraft.resources.Identifier.parse("academy:skill_repair_probe"), 100,
                            new org.academy.api.common.entitycontrol.ControlDirective.ImpressionAlliance()));
            player.teleportTo(player.level(), subject.getX(), subject.getY(), subject.getZ() - 1, Set.of(), 0, 0, false);
            var result = org.academy.internal.common.ability.mentalout.control.ImpressionRidingManager.requestMount(player, subject);
            if (!result.name().equals("PENDING")) throw new IllegalStateException("Mount: " + result);
        });
        if (ticks == 620) {
            server(player -> { if (player.getVehicle() != subject) throw new IllegalStateException("Riding setup failed"); });
            startTakeover();
        }
        if (ticks == 650) verifyTakeover("riding");
        if (ticks == 670) server(player -> {
            org.academy.internal.common.ability.mentalout.control.PlayerControlSessionManager.toggle(player);
            relation.close(); subject.discard();
        });
        if (ticks == 710) {
            System.out.println("[skill-repair-smoke] PASSED phase HUD, remote charge, standing/riding takeover");
            if (!reconnected) {
                reconnected = true;
                opened = false; configured = false; ready = false; ticks = 0;
                remote = null; subject = null; relation = null;
                mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
                return;
            }
            System.out.println("[skill-repair-smoke] PASSED reconnect and fresh server session revisions");
            mc.stop();
        }
    }

    private static void startTakeover() {
        server(player -> {
            var session = org.academy.internal.common.ability.mentalout.control.MentalIntrusionManager.startPrecision(player, subject, Long.MAX_VALUE);
            if (session == null) throw new IllegalStateException("Intrusion setup failed");
            var result = org.academy.internal.common.ability.mentalout.control.PlayerControlSessionManager.toggle(player);
            if (!result.name().equals("STARTED")) throw new IllegalStateException("Takeover: " + result);
        });
    }

    private static void verifyTakeover(String name) {
        if (!org.academy.internal.client.ability.mentalout.PlayerControlClientState.isController())
            throw new IllegalStateException("Takeover immediately cancelled: " + name);
        server(player -> {
            var input = org.academy.internal.common.ability.mentalout.control.PlayerControlSessionManager.mobDirectInput(subject);
            if (input.isEmpty()) throw new IllegalStateException("Missing active mob input: " + name);
            System.out.println("[skill-repair-smoke] takeover " + name + " yaw=" + subject.getYRot());
        });
        captureImage("takeover_" + name);
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable error) { failure = error; }
        });
    }

    private static void capture(String name) {
        var expected = name.startsWith("phase_80") ? 0.8f : 0.2f;
        if (Math.abs(AbilitySystemClient.getDarkmatterAlpha() - expected) > 0.001f)
            throw new IllegalStateException("Phase synchronization failed: " + AbilitySystemClient.getDarkmatterAlpha());
        captureImage(name);
    }

    private static void captureImage(String name) {
        var output = Path.of(System.getProperty("academy.skillRepairOutput"))
                .resolve((reconnected ? "reconnect_" : "") + name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
            } catch (Exception error) { failure = error; }
        });
        System.out.println("[skill-repair-smoke] captured " + name);
    }
}
