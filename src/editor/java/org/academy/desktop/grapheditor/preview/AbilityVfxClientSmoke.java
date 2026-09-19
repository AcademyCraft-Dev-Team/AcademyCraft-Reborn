package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.DarkmatterGraphEffects;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.ability.accelerator.skills.lv5.PlasmaGeneration;
import org.academy.internal.common.ability.electromaster.MagneticFieldRuntime;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.SpawnVfxGraphPacket;
import org.misaka.MisakaNetworkClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in isolated in-game validation. Never included in the mod jars. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AbilityVfxClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static int ticks, total;
    private static float pitch;
    private static float yaw;
    private static float releaseSize;
    private static net.minecraft.world.entity.LivingEntity hostile, friendly;
    private static volatile boolean gameplayPassed;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.abilityVfxSmoke")) return;
        var mc = Minecraft.getInstance();
        if (++total > 2800) throw new IllegalStateException("Ability VFX smoke timed out");
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
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            server(p -> {
                for (int x = -16; x <= 16; x++) for (int z = 160; z <= 245; z++) {
                    p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
                p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
                p.setGameMode(GameType.CREATIVE);
                learn(p, Skills.PLASMA_GENERATION.get(), Skills.VECTOR_BLAST.get());
                for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(80))) old.discard();
                var server = p.level().getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        if (ticks == 20) { pitch = -77; MisakaNetworkClient.send(PlasmaGeneration.StartPacket.INSTANCE); }
        if (ticks == 78) { require("plasma_cannon_focus"); capture("client_plasma_converging"); }
        if (ticks == 82) {
            releaseSize = layerSize(require("plasma_cannon_focus"), "plasma_core");
            require(releaseSize < 4f, "Three-second focus remains compact");
            MisakaNetworkClient.send(PlasmaGeneration.ReleasePacket.INSTANCE);
        }
        if (ticks == 85) {
            float projectile = layerSize(require("plasma_cannon_projectile"), "plasma_core");
            require(Math.abs(projectile - releaseSize) < 0.45, "Release preserves core size");
            capture("client_plasma_early_launch");
        }
        if (ticks == 125) {
            pitch = 0;
            server(p -> {
                var level = p.level();
                var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, level);
                var cow = new net.minecraft.world.entity.animal.cow.Cow(net.minecraft.world.entity.EntityTypes.COW, level);
                zombie.setPos(0, 81, 183); cow.setPos(0, 81, 188);
                for (var mob : java.util.List.of(zombie, cow)) {
                    mob.setNoAi(true); mob.setNoGravity(true);
                    mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                    mob.setHealth(100); level.addFreshEntity(mob);
                }
                hostile = zombie; friendly = cow;
            });
        }
        if (ticks == 135) server(p -> {
            p.setYRot(0); p.setXRot(0);
            require(VectorBlast.Server.tryAutomatedAttack(p), "Actual vector blast executes");
            require(hostile.getHealth() < 100, "Blast damages hostile target");
            require(friendly.getHealth() == 100, "Blast preserves friendly health");
            require(hostile.getDeltaMovement().z > 1 && friendly.getDeltaMovement().z > 1, "Both targets receive strong knockback");
            gameplayPassed = true;
        });
        if (ticks == 138) {
            var blast = require("vector_blast").effect();
            require(blast.buffer().count() == 1024, "Blast uses a bounded cloud volume");
            require(blast.arcBuffer().count() == 0, "Blast has no spiral lines or rings");
            require(maxAlpha(require("vector_blast")) <= 0.111f, "First-person cloud opacity is reduced");
            capture("client_vector_blast");
        }
        if (ticks == 139) {
            require("vector_blast").bind("time", () -> Value.of(0.18f));
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks == 141) {
            require(maxAlpha(require("vector_blast")) > 0.45f, "F5 restores original third-person opacity on the same effect");
            capture("client_vector_blast_third_person");
        }
        if (ticks == 142) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ticks == 144) {
            require(maxAlpha(require("vector_blast")) <= 0.111f, "F5 back to first-person reduces opacity again");
            capture("client_vector_blast_eye");
        }
        if (ticks == 160) { require(gameplayPassed, "Blast server checks complete"); server(p -> { hostile.discard(); friendly.discard(); }); }
        if (ticks == 161) {
            yaw = -90; pitch = 4;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server(p -> p.teleportTo(p.level(), -36, 86, 206.5, Set.of(), -90, 4, false));
        }
        if (ticks == 165) server(p -> SpawnVfxGraphPacket.broadcast(p.level(), AcademyCraft.academy("vfxgraph/vector_blast"),
                new Vec3(0, 82.6, 174.5), new Vec3(0, 0, 1), -1, 1f, 0.65f,
                Map.of("length", 64f, "width", 1f, "seed", 42f)));
        if (ticks == 168) { require("vector_blast"); capture("client_vector_blast_side"); }
        if (ticks == 172) {
            yaw = 0; pitch = 0;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false));
        }
        if (ticks == 175) server(p -> DarkmatterGraphEffects.interference(p, 24));
        if (ticks == 178) { require(org.academy.internal.client.render.vfx.InterferenceFieldClient.activeCount() == 1, "Interference range shader active"); capture("client_darkmatter_interference"); }
        if (ticks == 190) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (ticks == 200) server(DarkmatterGraphEffects::repair);
        if (ticks == 203) { require("darkmatter_repair"); capture("client_darkmatter_repair"); }
        if (ticks == 215) server(DarkmatterGraphEffects::disassemble);
        if (ticks == 218) { require("darkmatter_disassemble"); capture("client_darkmatter_disassemble"); }
        if (ticks == 240) server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            learn(p, Skills.MAGNET_MANIPULATION.get());
            p.teleportTo(p.level(), 0, 83, 174.5, Set.of(), 0, 0, false);
            require(MagneticFieldRuntime.toggle(p), "Levitation starts");
        });
        if (ticks == 265) {
            require(mc.player.getData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE), "Authoritative hovering state arrives");
            require("magnetic_levitation_support"); capture("client_magnetic_support");
        }
        if (ticks == 268) { mc.options.setCameraType(CameraType.FIRST_PERSON); pitch = 70; }
        if (ticks == 272) { require("magnetic_levitation_support"); capture("client_magnetic_support_first_person"); }
        if (ticks == 274) { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); pitch = 0; }
        if (ticks == 275) server(p -> p.teleportTo(p.level(), 3, 83, 180, Set.of(), 0, 0, false));
        if (ticks == 285) { require("magnetic_levitation_support"); capture("client_magnetic_support_moved"); }
        if (ticks == 290) server(MagneticFieldRuntime::toggle);
        if (ticks == 305) require(find("magnetic_levitation_support") == null, "Stopping levitation clears arcs");
        if (ticks == 315) server(p -> SpawnVfxGraphPacket.broadcast(p.level(), AcademyCraft.academy("vfxgraph/aeromanip_mist_vortex"),
                p.position().add(0, 1, 3), -1, 2f, Map.of()));
        if (ticks == 320) { require("aeromanip_mist_vortex"); capture("client_aeromanip"); }
        if (ticks == 390) {
            require(find("vector_blast") == null && find("darkmatter_repair") == null && find("aeromanip_mist_vortex") == null,
                    "One-shot graphs expire");
            capture("client_cleared");
            System.out.println("[ability-vfx-smoke] PASSED");
            mc.stop();
        }
    }

    private static void learn(ServerPlayer player, org.academy.api.common.ability.Skill... skills) {
        var system = AbilitySystemServer.getSystem(player);
        var id = player.getUUID();
        system.setPlayerAbilityCategory(id, skills[0].getCategory());
        system.setPlayerLevel(id, 5);
        for (var skill : skills) {
            system.addPlayerSkill(player, skill.getKeyString());
            if (!skill.isEnabled(player)) skill.toggle(player);
            system.setPlayerSkillProficiency(id, skill, 0);
        }
        var data = system.getPlayerData(id);
        data.setAcademyMaxCp(100000); data.getCpData().setMaxCP(100000); data.getCpData().setAvailableCP(100000);
        for (var kind : java.util.List.of(org.academy.api.common.ability.SyncTypes.CP_DATA,
                org.academy.api.common.ability.SyncTypes.SKILL_DATA, org.academy.api.common.ability.SyncTypes.ABILITY_CATEGORY)) {
            system.schedulePlayerSync(id, kind);
        }
    }
    private static float maxAlpha(ActiveEffect effect) {
        var buffer = effect.effect().buffer();
        float max = 0;
        for (int i = 0; i < buffer.count(); i++) max = Math.max(max, buffer.alpha(i));
        return max;
    }
    private static float layerSize(ActiveEffect effect, String layer) {
        var buffer = effect.effect().buffer();
        for (int i = 0; i < buffer.count(); i++) if (buffer.layer(i) == org.academy.api.client.render.vfxgraph.sim.ParticleBuffer.layerByte(layer)) return buffer.size(i);
        throw new IllegalStateException("Missing layer " + layer);
    }
    private static ActiveEffect find(String name) {
        return VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> e.assetKey().endsWith(name)).findFirst().orElse(null);
    }
    private static ActiveEffect require(String name) {
        var effect = find(name);
        require(effect != null, "Rendered graph " + name);
        return effect;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        System.out.println("[ability-vfx-smoke] " + message);
    }
    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }
    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.abilityVfxOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(output.getParent()); image.writeToFile(output); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }
}
