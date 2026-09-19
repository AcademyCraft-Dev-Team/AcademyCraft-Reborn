package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.common.entitycontrol.MentalImmunity;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.MagneticFieldRuntime;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.misaka.MisakaNetworkClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in real client/server network and HUD regression; never packaged in the mod. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ElectromasterReworkClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static int total, ticks;
    private static volatile LivingEntity target;
    private static volatile net.minecraft.world.entity.Entity magnetTarget;
    private static volatile float before;
    private static double holdDistanceBefore;
    private static double pushedDistance;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.electromasterReworkSmoke")) return;
        var mc = Minecraft.getInstance();
        if (++total > 1800) throw new IllegalStateException("Rework smoke timed out");
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
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            server(player -> {
                var level = player.level();
                player.setGameMode(GameType.SURVIVAL);
                for (int x = -24; x <= 24; x++) for (int z = 160; z <= 220; z++) {
                    level.setBlock(new net.minecraft.core.BlockPos(x, 80, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
                player.teleportTo(level, 0, 83, 174.5, Set.of(), 0, 0, false);
                var system = AbilitySystemServer.getSystem(player);
                var id = player.getUUID();
                system.setPlayerAbilityCategory(id, AbilityCategories.ELECTROMASTER.get());
                system.setPlayerLevel(id, 5);
                for (var skill : java.util.List.of(Skills.MAGNET_MANIPULATION.get(), Skills.IRON_SAND_ARSENAL.get())) {
                    system.addPlayerSkill(player, skill.getKeyString());
                    if (!skill.isEnabled(player)) skill.toggle(player);
                    system.setPlayerSkillProficiency(id, skill, 3000);
                }
                var data = system.getPlayerData(id);
                data.setAcademyMaxCp(10000);
                data.getCpData().setMaxCP(10000);
                data.getCpData().setAvailableCP(10000);
                system.getIronSandResourceService().reconcileCapacity(player);
                system.getIronSandResourceService().account(player).recover(10000);
                org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon.Server.forceDisable(player);
                for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(64))) old.discard();
                var pig = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, level);
                pig.setPos(0, 83, 181); pig.setNoAi(true); pig.setNoGravity(true);
                pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);
                pig.setHealth(1024); level.addFreshEntity(pig); target = pig;
                org.academy.api.server.ability.HostileTargets.mark(player, pig);
                for (var kind : java.util.List.of(org.academy.api.common.ability.SyncTypes.CP_DATA,
                        org.academy.api.common.ability.SyncTypes.SKILL_DATA, org.academy.api.common.ability.SyncTypes.ABILITY_CATEGORY)) system.schedulePlayerSync(id, kind);
                var commands = mc.getSingleplayerServer().getCommands();
                commands.performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(), "time set 1000");
                commands.performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(), "weather clear");
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        if (ticks == 25) {
            MisakaNetworkClient.send(MagnetManipulation.MoveStartPacket.PLAYER_TO_TARGET);
            MisakaNetworkClient.send(IronSandArsenal.TogglePacket.INSTANCE);
        }
        if (ticks == 40) {
            require(mc.player.getData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE), "Levitation attachment reaches client");
            require(mc.player.getData(AttachmentTypes.IRON_SAND_DATA).defense(), "Defense attachment reaches client");
            require(AbilitySystemClient.getMaxMP() > 0, "MP HUD receives capacity");
            server(p -> require(MentalImmunity.isImmune(p), "Learned magnetic field grants immunity"));
            capture("levitation_defense");
        }
        if (ticks == 50) { server(p -> before = target.getHealth()); MisakaNetworkClient.send(new IronSandArsenal.ActionPacket(1, 0)); }
        if (Boolean.getBoolean("academy.ironSandVisualSmoke")) ironSandVisuals();
        if (ticks == 52) MisakaNetworkClient.send(new IronSandArsenal.ActionPacket(1, 1));
        if (ticks == 54) capture("whip");
        if (ticks == 65) server(p -> {
            require(target.getHealth() < before, "Tap packet produces one whip");
            require(MagneticFieldRuntime.isHovering(p), "Whip does not stop levitation");
            before = target.getHealth();
        });
        if (ticks == 75) MisakaNetworkClient.send(new IronSandArsenal.ActionPacket(2, 0));
        if (ticks > 75 && ticks < 130 && ticks % 10 == 0) MisakaNetworkClient.send(new IronSandArsenal.ActionPacket(2, 3));
        if (ticks == 100) {
            require(mc.player.getData(AttachmentTypes.IRON_SAND_DATA).cloudRadius() > 0, "Hold starts cloud");
            capture("cloud_third_person");
            server(p -> require(target.getHealth() < before, "Paid cloud pulse damages target"));
        }
        if (ticks == 108) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ticks == 118) capture("cloud_first_person");
        if (ticks == 130) MisakaNetworkClient.send(new IronSandArsenal.ActionPacket(2, 2));
        if (ticks == 145) {
            require(mc.player.getData(AttachmentTypes.IRON_SAND_DATA).cloudRadius() == 0, "Cancellation removes cloud");
            MisakaNetworkClient.send(IronSandArsenal.TogglePacket.INSTANCE);
            MisakaNetworkClient.send(MagnetManipulation.MoveStartPacket.PLAYER_TO_TARGET);
        }
        if (ticks == 165) {
            require(!mc.player.getData(AttachmentTypes.IRON_SAND_DATA).active(), "Defense and cloud clear");
            require(!mc.player.getData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED), "Levitation toggles off");
            server(p -> require(!p.isNoGravity(), "Server gravity is restored"));
            capture("cleared");
        }
        // R-key target control: sustained hold past the old heartbeat timeout, then wheel distance.
        if (ticks == 167) server(p -> {
            // An iron ingot entity is magnetic and has no AI input, so the pull stays deterministic.
            var magnetic = new net.minecraft.world.entity.item.ItemEntity(p.level(),
                    p.getX(), p.getEyeY() - 0.125, p.getZ() + 6,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            magnetic.setNoGravity(true);
            magnetic.setNeverPickUp();
            magnetic.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.level().addFreshEntity(magnetic);
            magnetTarget = magnetic;
            p.setYRot(0); p.yRotO = 0; p.setXRot(0); p.xRotO = 0;
        });
        if (ticks == 170) MisakaNetworkClient.send(MagnetManipulation.MoveStartPacket.TARGET_TO_PLAYER);
        if (ticks > 170 && ticks < 266 && ticks % 10 == 0) {
            MisakaNetworkClient.send(MagnetManipulation.MoveStartPacket.TARGET_TO_PLAYER);
        }
        if (ticks == 215) {
            require(mc.player.getData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE), "Hold survives past the old heartbeat timeout");
            holdDistanceBefore = mc.player.distanceTo(clientTarget());
        }
        // Wheel-up (positive yOffset) must push the target away; wheel-down must draw it back.
        if (ticks == 220 || ticks == 230) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new org.academy.api.client.input.MouseScrollEvent(0, 1));
        }
        if (ticks == 245) {
            pushedDistance = mc.player.distanceTo(clientTarget());
            require(pushedDistance > holdDistanceBefore + 0.5, "Wheel-up pushes the controlled target farther");
        }
        if (ticks == 250 || ticks == 256) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new org.academy.api.client.input.MouseScrollEvent(0, -1));
        }
        if (ticks == 264) {
            require(mc.player.distanceTo(clientTarget()) < pushedDistance - 0.2, "Wheel-down pulls the controlled target closer");
            MisakaNetworkClient.send(MagnetManipulation.MoveStopPacket.INSTANCE);
        }
        if (ticks == 270) require(!mc.player.getData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE),
                "Release clears the magnetic hold");
        if (ticks == 276) {
            System.out.println("[electromaster-rework] PASSED: network tap/hold/cancel, defense, levitation, magnet hold/scroll, MP HUD and cleanup");
            mc.stop();
        }
    }

    private static net.minecraft.world.entity.Entity clientTarget() {
        var entity = Minecraft.getInstance().level == null || magnetTarget == null ? null
                : Minecraft.getInstance().level.getEntity(magnetTarget.getId());
        require(entity != null, "Controlled target is present on the client");
        return entity;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void ironSandVisuals() {
        if (ticks == 43 || ticks == 66) server(p -> {
            var origin = target.position();
            target.setPos(p.position().add(ticks == 43 ? -4 : 4, 0, 0));
            var hp = p.getHealth();
            var resource = AbilitySystemServer.getSystem(p).getIronSandResourceService().account(p);
            var mass = resource.current();
            p.invulnerableTime = 0;
            p.hurtServer(p.level(), p.damageSources().mobAttack(target), 4);
            require(p.getHealth() == hp, "Iron sand absorbs actual health loss");
            require(resource.current() < mass, "Shield only reports paid absorption");
            target.setPos(origin);
        });
        if (ticks == 46 || ticks == 69) {
            requireGraph("guard");
            capture(ticks == 46 ? "guard_left" : "guard_right");
        }
        if (ticks == 70) server(p -> {
            var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(net.minecraft.world.entity.EntityTypes.ARROW, p.level());
            arrow.setPos(p.position().add(-3, 1, 0));
            arrow.setDeltaMovement(2, 0, 0);
            p.level().addFreshEntity(arrow);
            require(IronSandArsenal.Server.intercept(p, arrow), "Fast hostile projectile intercepted on swept entry");
            require(arrow.isRemoved(), "Intercepted projectile is destroyed");
        });
        if (ticks == 72) { requireGraph("intercept"); capture("projectile_cut"); }
        if (ticks == 73) {
            requireGraph("intercept");
            var bolt = org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager.INSTANCE.activeEffects().stream()
                    .filter(e -> e.assetKey().endsWith("iron_sand_intercept")).findFirst().orElseThrow();
            var arcs = bolt.effect().arcBuffer();
            require(arcs != null && arcs.count() > 0, "Intercept renders electric arc geometry");
            var arc = arcs.arc(0);
            System.out.println("[iron-sand-smoke] bolt age=" + bolt.gameAgeSeconds() + " start=" + arc.y(0)
                    + " tip=" + arc.y(arc.size() - 1));
            capture("projectile_cut_extended");
        }
        if (ticks == 165) require(org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager.INSTANCE.activeEffects().stream()
                .noneMatch(e -> !e.isExpired() && e.assetKey().contains("iron_sand_")), "All iron sand graphs clean up");
    }

    private static void requireGraph(String name) {
        require(org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager.INSTANCE.activeEffects().stream()
                .anyMatch(e -> !e.isExpired() && e.assetKey().endsWith("iron_sand_" + name)), "Client received " + name + " graph");
    }
    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }
    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.electromasterReworkOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(output.getParent()); image.writeToFile(output); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }
}
