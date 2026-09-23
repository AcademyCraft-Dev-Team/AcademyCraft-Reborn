package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.arc.ElectromasterArcEffects;
import org.academy.api.server.ability.ElectromasterGraphEffects;
import org.academy.internal.common.ability.electromaster.skystrike.SkyStrikeProfile;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.ability.electromaster.skills.lv2.ThunderLance;
import org.academy.internal.common.ability.electromaster.skills.lv4.Railgun;
import org.academy.internal.common.attachment.AttachmentTypes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in skill/network/attachment capture in run/electromaster-arcs, excluded from shipped jars. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ElectromasterArcsClientSmoke {
    private static boolean opened, configured, lanceCaptured;
    private static volatile boolean ready;
    private static int ticks, total;
    private static float yaw, pitch;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.electromasterArcsSmoke")) return;
        var mc = Minecraft.getInstance();
        if (++total > 2400) throw new IllegalStateException("Electric arc smoke timed out");
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
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            server(player -> {
                var level = player.level();
                for (int x = -12; x <= 12; x++) {
                    for (int z = 169; z <= 225; z++) {
                        int height = x >= 4 && x <= 8 && z >= 185 && z <= 200 ? (z - 185) / 4 : 0;
                        for (int y = 80; y <= 80 + height; y++) level.setBlock(new net.minecraft.core.BlockPos(x, y, z),
                                net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    }
                }
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, 0, 81, 174.5, Set.of(), 0, 0, false);
                var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
                var id = player.getUUID();
                system.setPlayerAbilityCategory(id, AbilityCategories.ELECTROMASTER.get());
                system.setPlayerLevel(id, 5);
                for (var skill : java.util.List.of(Skills.RAILGUN.get(), Skills.ARC_GENERATE.get(), Skills.THUNDER_LANCE.get())) {
                    system.addPlayerSkill(player, skill.getKeyString());
                    if (!skill.isEnabled(player)) skill.toggle(player);
                }
                var data = system.getPlayerData(id);
                data.setAcademyMaxCp(1_000_000);
                data.getCpData().setMaxCP(1_000_000);
                data.getCpData().setAvailableCP(1_000_000);
                system.schedulePlayerSync(id, org.academy.api.common.ability.SyncTypes.CP_DATA);
                system.schedulePlayerSync(id, org.academy.api.common.ability.SyncTypes.SKILL_DATA);
                system.schedulePlayerSync(id, org.academy.api.common.ability.SyncTypes.ABILITY_CATEGORY);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 32));
                player.setMainArm(HumanoidArm.RIGHT);
                var commands = mc.getSingleplayerServer().getCommands();
                commands.performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(), "time set 1000");
                commands.performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(), "weather clear");
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        if (ticks == 100) Railgun.Client.start();
        if (ticks == 125) { verifyCharge(true); capture("charge_right"); }
        if (ticks == 140) { yaw = 48; pitch = 22; }
        if (ticks == 150) { verifyCharge(true); capture("charge_turn"); }
        if (ticks == 160) Railgun.Client.end();
        if (ticks == 164 && find("railgun_charge") != null) throw new IllegalStateException("Charge graph survived release");
        if (ticks == 205) { yaw = 0; pitch = 0; server(p -> p.setMainArm(HumanoidArm.LEFT)); }
        if (ticks == 220) Railgun.Client.start();
        if (ticks == 240) { verifyCharge(false); capture("charge_left"); }
        if (ticks == 250) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (ticks == 260) {
            var hand = require("railgun_charge");
            if (!hand.renderVisible() || hand.position().distance(mc.player.position().toVector3f()) > 2)
                throw new IllegalStateException("Third-person hand attachment is not on the visible avatar");
            capture("charge_third_person");
        }
        if (ticks == 265) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (ticks == 268) { require("railgun_charge"); capture("charge_third_person_front"); }
        if (ticks == 270) Railgun.Client.end();
        if (ticks == 315) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> p.setMainArm(HumanoidArm.RIGHT));
        }
        if (ticks == 340) server(p -> {
            if (!ArcGenerate.Server.tryAutomatedAttack(p)) throw new IllegalStateException("Arc Generate rejected");
        });
        if (ticks == 345) { require("arc_generate"); capture("arc_generate"); }
        if (ticks == 380) {
            var status = org.academy.api.client.ability.AbilitySystemClient.getSkillUseStatus(Skills.THUNDER_LANCE.get());
            if (!status.allowed()) throw new IllegalStateException("Thunder Lance test setup rejected: " + status);
            ThunderLance.Client.onQuickUse();
        }
        if (ticks >= 383 && ticks <= 399 && !lanceCaptured && find("thunder_lance") != null) {
            require("thunder_lance"); capture("thunder_lance"); lanceCaptured = true;
        }
        if (ticks == 400 && !lanceCaptured) throw new IllegalStateException("Thunder Lance packet did not produce an effect");
        if (ticks == 420) server(p -> {
            var mirror = new Vec3(0, 82, 195);
            ElectromasterGraphEffects.spawnBolt(p.level(), new Vec3(-0.4, 82, 175), mirror,
                    ElectromasterGraphEffects.BoltStyle.LANCE);
            ElectromasterGraphEffects.spawnBolt(p.level(), mirror, new Vec3(7, 82, 183),
                    ElectromasterGraphEffects.BoltStyle.LANCE);
        });
        if (ticks == 425) {
            long count = VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> e.assetKey().endsWith("thunder_lance")).count();
            if (count != 2) throw new IllegalStateException("Missing reflected segment: " + count);
            capture("reflected_lance");
        }
        if (ticks == 445) {
            pitch = 8;
            server(p -> p.teleportTo(p.level(), 0, 86, 170, Set.of(), 0, 8, false));
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
        if (ticks == 460) server(p -> ElectromasterArcEffects.spawnSkyStrike(p.level(), new Vec3(3, 81, 192), SkyStrikeProfile.THUNDERCLAP));
        if (ticks == 466) capture("thunderclap");
        if (ticks == 480) { verifyAttachment("sky_strike_thunderclap"); capture("surface_attachment"); }
        if (ticks == 500) {
            pitch = -27;
            server(p -> p.teleportTo(p.level(), 0, 129, 160, Set.of(), 0, -27, false));
        }
        if (ticks == 515) server(p -> ElectromasterArcEffects.spawnSkyStrike(p.level(), new Vec3(3, 81, 192), SkyStrikeProfile.THUNDERCLAP));
        if (ticks == 528) { verifyAttachment("sky_strike_thunderclap"); capture("cloud_attachment"); }
        if (ticks == 537) {
            pitch = 8;
            server(p -> p.teleportTo(p.level(), 0, 86, 170, Set.of(), 0, 8, false));
        }
        if (ticks >= 550 && ticks <= 577 && (ticks - 550) % 3 == 0) {
            int n = (ticks - 550) / 3;
            server(p -> ElectromasterArcEffects.spawnSkyStrike(p.level(),
                    new Vec3(3 + Math.cos(n * 2.4) * 5, 81, 192 + Math.sin(n * 2.4) * 5), SkyStrikeProfile.LIGHTNING_STORM));
        }
        if (ticks == 581) { verifyAttachment("sky_strike_storm"); capture("storm_attachment"); }
        if (ticks == 650) assertCleared();
        if (ticks == 660) {
            pitch = 0;
            server(p -> {
                p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
                p.setData(AttachmentTypes.RAILGUN_DATA, new Railgun.Data(true, false, 20, false, true));
            });
        }
        if (ticks == 665) {
            long hands = VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> e.assetKey().endsWith("railgun_charge")).count();
            if (hands != 2) throw new IllegalStateException("Coin return hint lost its second hand");
            capture("coin_return_hint");
        }
        if (ticks == 672) server(p -> p.removeData(AttachmentTypes.RAILGUN_DATA));
        if (ticks == 680) assertCleared();
        if (ticks == 700) server(p -> ElectromasterGraphEffects.spawnBolt(p.level(), new Vec3(-0.4, 82, 175),
                new Vec3(-0.4, 82, 687), ElectromasterGraphEffects.BoltStyle.ARC));
        if (ticks == 705) { require("arc_generate"); capture("extended_range"); }
        if (ticks == 760) {
            assertCleared();
            capture("cleared");
            System.out.println("[electric-smoke] PASSED: hands, turning, third person, release, both skills, reflected segments, terrain/cloud attachment, storm overlap, hint, 512 blocks");
            mc.stop();
        }
    }

    private static ActiveEffect find(String name) {
        return VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> e.assetKey().endsWith(name)).findFirst().orElse(null);
    }

    private static ActiveEffect require(String name) {
        return VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> e.assetKey().endsWith(name)
                && e.effect().arcBuffer() != null && e.effect().arcBuffer().count() > 0).findFirst()
                .orElseThrow(() -> new IllegalStateException("No rendered graph: " + name));
    }

    private static void verifyCharge(boolean right) {
        var effect = require("railgun_charge");
        var mc = Minecraft.getInstance();
        var relative = effect.position().sub(mc.gameRenderer.mainCamera().position().toVector3f(), new org.joml.Vector3f());
        relative.rotate(new org.joml.Quaternionf(mc.gameRenderer.mainCamera().rotation()).conjugate());
        if (Math.abs(relative.x - (right ? 0.30f : -0.30f)) > 0.08f || Math.abs(relative.z + 0.46f) > 0.08f)
            throw new IllegalStateException("Hand anchor drifted: " + relative);
    }

    private static void verifyAttachment(String name) {
        var effect = require(name);
        int shells = 0, cores = 0;
        var arcs = effect.effect().arcBuffer();
        for (int i = 0; i < arcs.count(); i++) {
            var arc = arcs.arc(i);
            if (!arc.layer().equals("electrical_attachment")) continue;
            if (arc.r() == 0.12f) shells++;
            if (arc.r() == 0.78f) cores++;
        }
        if (shells == 0 || shells != cores) throw new IllegalStateException("Incomplete surface layers");
    }

    private static void assertCleared() {
        if (VfxGraphManager.INSTANCE.effectCount() != 0) throw new IllegalStateException("Graph lifetime leak");
    }

    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }

    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.electromasterArcsOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
                System.out.println("[electric-smoke] capture " + output);
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
