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
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.api.server.ability.ElectromasterGraphEffects;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
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
public final class ElectromasterExtensionClientSmoke {
    private static boolean opened, configured, lanceCaptured;
    private static volatile boolean ready;
    private static int ticks, total;
    private static float yaw, pitch;
    private static int expectedRange, expectedAmmo = -1;
    private static boolean arcObserved, shieldObserved, bladeTrail, bladeImpact, bladeFirstPerson;
    private static final Set<java.util.UUID> rays = new java.util.HashSet<>();
    private static net.minecraft.world.entity.LivingEntity inside, outside, weaponTarget;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.electromasterExtensionSmoke")) return;
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
                for (int x = -20; x <= 20; x++) {
                    for (int z = 169; z <= 265; z++) {
                        int height = 0;
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
                    system.setPlayerSkillProficiency(id, skill, 0);
                }
                var data = system.getPlayerData(id);
                org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon.Server.forceDisable(player);
                if (Skills.ELECTROMAGNETIC_SHIELD.get().isEnabled(player)) Skills.ELECTROMAGNETIC_SHIELD.get().toggle(player);
                for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(100))) old.discard();
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
        observe();
        if (ticks == 40) { expectedRange = 16; arcObserved = false; server(p -> ArcGenerate.Server.tryAutomatedAttack(p)); }
        if (ticks == 70 && !arcObserved) throw new IllegalStateException("Missing base arc");
        if (ticks == 85) server(p -> org.academy.api.server.ability.AbilitySystemServer.getSystem(p)
                .setPlayerSkillProficiency(p.getUUID(), Skills.ARC_GENERATE.get(), 2000));
        if (ticks == 100) { expectedRange = 20; arcObserved = false; server(p -> ArcGenerate.Server.tryAutomatedAttack(p)); }
        if (ticks == 130 && !arcObserved) throw new IllegalStateException("Missing proficient arc");
        for (int ammo = 0; ammo < 4; ammo++) {
            int tier = ammo;
            int start = 170 + ammo * 100;
            if (ticks == start) {
                expectedAmmo = ammo;
                server(p -> prepareAmmo(p, tier));
            }
            if (ticks == start + 15) Railgun.Client.start();
            if (ticks == start + 48) Railgun.Client.end();
            if (ticks == start + 78) {
                if (rays.size() != ammo + 1) throw new IllegalStateException("Missing ammo ray " + ammo);
                if (ammo == 0) server(p -> {
                    if (inside.isAlive() || !outside.isAlive()) throw new IllegalStateException("Damage radius boundary failed");
                    if (!p.level().getBlockState(new net.minecraft.core.BlockPos(0, 82, 198)).isAir()
                            || p.level().getBlockState(new net.minecraft.core.BlockPos(2, 82, 198)).isAir())
                        throw new IllegalStateException("Block destruction radius boundary failed");
                    System.out.println("[electric-extension] verified damage radius 2 / destruction radius 1");
                    outside.discard();
                });
            }
        }
        if (ticks == 585) {
            expectedAmmo = -1;
            server(p -> {
                var skill = Skills.ELECTROMAGNETIC_SHIELD.get();
                org.academy.api.server.ability.AbilitySystemServer.getSystem(p).addPlayerSkill(p, skill.getKeyString());
                if (!skill.isEnabled(p)) skill.toggle(p);
            });
        }
        if (ticks == 605) { verifyShield(); capture("shield_first_person"); }
        if (ticks == 610) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (ticks == 620) { verifyShield(); capture("shield_third_person"); }
        if (ticks == 625) { yaw = 40; server(p -> p.teleportTo(p.level(), 4, 81, 174.5, Set.of(), 40, 0, false)); }
        if (ticks == 640) { verifyShield(); capture("shield_following"); }
        if (ticks == 650) server(p -> { if (Skills.ELECTROMAGNETIC_SHIELD.get().isEnabled(p)) Skills.ELECTROMAGNETIC_SHIELD.get().toggle(p); });
        if (ticks == 665 && find("electromagnetic_shield") != null) throw new IllegalStateException("Shield survived disable");
        if (ticks == 670) {
            yaw = 0;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> {
                p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
                ElectromasterArcEffects.spawnShieldInterceptRing(p.level(), new Vec3(0, 82.5, 178), new Vec3(0, 0, -1));
            });
        }
        if (ticks >= 671 && ticks < 680 && !shieldObserved && find("electromagnetic_shield") != null) {
            require("electromagnetic_shield"); capture("shield_interception"); shieldObserved = true;
        }
        if (ticks == 690) server(p -> {
            var skill = Skills.MAGNETIC_WEAPON.get();
            var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(p);
            system.addPlayerSkill(p, skill.getKeyString());
            var data = system.getPlayerData(p.getUUID());
            data.setAcademyMaxCp(10_000);
            data.getCpData().setMaxCP(10_000);
            data.getCpData().setAvailableCP(10_000);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            // A marked passive target survives the isolated save's Peaceful difficulty.
            var target = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, p.level());
            target.setPos(3, 81.5, 179); target.setNoAi(true); target.setNoGravity(true);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);
            target.setHealth(1024);
            p.level().addFreshEntity(target); weaponTarget = target;
            if (!org.academy.api.server.ability.HostileTargets.mark(p, target)) throw new IllegalStateException("Could not mark blade target");
        });
        if (ticks == 715) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            org.misaka.MisakaNetworkClient.send(org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon.TogglePacket.INSTANCE);
        }
        if (ticks == 805) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ticks >= 740 && ticks < 850 && ticks % 20 == 0) {
            server(p -> System.out.println("[electric-extension] weapon state active="
                    + org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon.Server.isActive(p)
                    + " enabled=" + Skills.MAGNETIC_WEAPON.get().isEnabled(p)
                    + " target=" + weaponTarget.isAlive() + " sight=" + p.hasLineOfSight(weaponTarget)
                    + " marked=" + org.academy.api.server.ability.HostileTargets.isMarked(p, weaponTarget)));
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof org.academy.internal.common.world.entity.skill.MagneticWeaponBlade blade)
                    System.out.println("[electric-extension] client blade attack=" + blade.getAttackTick()
                            + " graphs=" + VfxGraphManager.INSTANCE.effectCount());
            }
        }
        if (ticks == 855) {
            if (!bladeTrail || !bladeImpact || !bladeFirstPerson || !shieldObserved) throw new IllegalStateException("Incomplete attachment scenarios trail=" + bladeTrail + " impact=" + bladeImpact + " firstPerson=" + bladeFirstPerson + " intercept=" + shieldObserved);
            server(p -> {
                org.academy.internal.common.ability.electromaster.skills.lv3.MagneticWeapon.Server.forceDisable(p);
                weaponTarget.discard();
            });
        }
        if (ticks == 885) {
            assertCleared(); capture("extension_cleared");
            System.out.println("[electric-extension] PASSED: 16/20-block endpoints, four real ammo casts, separate damage/destruction bounds, shield follow/interception/disable, magnetic trail/impact/cleanup");
            mc.stop();
        }
    }

    private static void observe() {
        var mc = Minecraft.getInstance();
        if (expectedRange > 0 && !arcObserved) {
            var arc = find("arc_generate");
            if (arc != null && arc.effect().arcBuffer().count() > 0) {
                var core = arc.effect().arcBuffer().arc(1);
                var end = new float[3];
                arc.worldTransform().apply(0, core.y(core.size() - 1), 0, end);
                if (Math.abs(end[2] - mc.player.getZ() - expectedRange) > 0.1
                        || Math.abs(end[0] - mc.player.getX()) > 0.1)
                    throw new IllegalStateException("Arc endpoint mismatch: " + java.util.Arrays.toString(end));
                arcObserved = true; capture("arc_range_" + expectedRange);
                System.out.println("[electric-extension] verified arc endpoint " + expectedRange);
            }
        }
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof org.academy.internal.common.world.entity.skill.RailgunRay ray
                    && expectedAmmo >= 0 && ray.tickCount >= 3 && !rays.contains(ray.getUUID())) {
                float length = 48 + expectedAmmo * 8;
                if (Math.abs(ray.getBeamLength() - length) > 0.01 || ray.getBeamWidthMultiplier() != 1 + expectedAmmo * 0.5f)
                    throw new IllegalStateException("Ammo dimensions mismatch " + expectedAmmo + ": " + ray.getBeamLength());
                var graph = require("railgun_shot");
                var beam = graph.effect().arcBuffer().arc(0);
                if (Math.abs(beam.z(beam.size() - 1) - length) > 0.01) throw new IllegalStateException("Beam endpoint mismatch");
                rays.add(ray.getUUID()); capture("railgun_ammo_" + expectedAmmo);
                System.out.println("[electric-extension] verified ammo " + expectedAmmo + " length=" + length);
            }
            if (entity instanceof org.academy.internal.common.world.entity.skill.MagneticWeaponBlade blade) {
                var effect = find("magnetic_weapon");
                if (effect == null || effect.effect().arcBuffer().count() == 0) continue;
                int attack = blade.getAttackTick();
                if (!bladeTrail && attack >= 8) { bladeTrail = true; capture("magnetic_trail"); }
                if (!bladeImpact && attack >= 6 && effect.effect().arcBuffer().count() >= 8) { bladeImpact = true; capture("magnetic_impact"); }
                if (!bladeFirstPerson && ticks > 805 && ticks < 850 && attack >= 6 && attack <= 8) {
                    bladeFirstPerson = true; capture("magnetic_first_person");
                }
                if (effect.effect().arcBuffer().count() > 32) throw new IllegalStateException("Unbounded blade paths");
            }
        }
    }

    private static void verifyShield() {
        var shield = require("electromagnetic_shield");
        if (shield.position().distance(Minecraft.getInstance().player.position().toVector3f()) > 0.25)
            throw new IllegalStateException("Shield detached from player");
        if (shield.effect().arcBuffer().count() != 12) throw new IllegalStateException("Shield missing blue-white layers");
    }

    private static void prepareAmmo(ServerPlayer p, int tier) {
        var item = switch (tier) {
            case 0 -> org.academy.internal.common.world.item.Items.COIN.get();
            case 1 -> net.minecraft.world.item.Items.IRON_INGOT;
            case 2 -> net.minecraft.world.item.Items.IRON_BLOCK;
            default -> net.minecraft.world.item.Items.ANVIL;
        };
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item, 16));
        if (tier != 0) return;
        p.setData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get(), true);
        p.level().setBlock(new net.minecraft.core.BlockPos(0, 82, 198), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        p.level().setBlock(new net.minecraft.core.BlockPos(2, 82, 198), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        inside = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, p.level());
        outside = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityTypes.PIG, p.level());
        inside.setPos(1.3, 82, 193); outside.setPos(3, 82, 193);
        for (var target : java.util.List.of(inside, outside)) { target.setNoGravity(true); ((net.minecraft.world.entity.Mob) target).setNoAi(true); p.level().addFreshEntity(target); }
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
        var output = Path.of(System.getProperty("academy.electromasterExtensionOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
                System.out.println("[electric-extension] capture " + output);
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
