package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv2.LightningNova;
import org.academy.internal.common.world.entity.skill.ArcEffect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in real skill/network/rendering and repeated-cast regression in an isolated test save. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class LightningNovaClientSmoke {
    private static boolean opened, configured, observed;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static int ticks, total, peakEffects;
    private static Pig target;
    private static float outwardHealth;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.lightningNovaSmoke")) return;
        var mc = Minecraft.getInstance();
        if (serverFailure != null) throw new IllegalStateException("Nova server assertion failed", serverFailure);
        if (++total > 2400) throw new IllegalStateException("Nova smoke timed out");
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
        mc.player.setXRot(25); mc.player.xRotO = 25;
        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            server(p -> {
                var system = AbilitySystemServer.getSystem(p);
                system.interruptActiveSkills(p);
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.level(), 0, 81, 175, Set.of(), 0, 25, false);
                system.setPlayerAbilityCategory(p.getUUID(), AbilityCategories.ELECTROMASTER.get());
                system.setPlayerLevel(p.getUUID(), 5);
                var skill = Skills.LIGHTNING_NOVA.get();
                system.addPlayerSkill(p, skill.getKeyString());
                if (!skill.isEnabled(p)) skill.toggle(p);
                system.setPlayerSkillProficiency(p.getUUID(), skill, 3000);
                var data = system.getPlayerData(p.getUUID());
                data.setAcademyMaxCp(1000);
                data.getCpData().setMaxCP(1000);
                data.getCpData().setAvailableCP(1000);
                for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        p.getBoundingBox().inflate(64))) old.discard();
                target = new Pig(EntityTypes.PIG, p.level());
                target.setPos(0, 81, 180); target.setNoAi(true); target.setNoGravity(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                target.setHealth(1024);
                p.level().addFreshEntity(target);
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        int effects = 0;
        for (var effect : VfxGraphManager.INSTANCE.activeEffects()) {
            if (!effect.assetKey().endsWith("lightning_nova")) continue;
            effects++;
            int arcs = effect.effect().arcBuffer().count();
            if (arcs > 8) throw new IllegalStateException("Unbounded ring geometry: " + arcs);
            if (arcs == 8) observed = true;
        }
        peakEffects = Math.max(peakEffects, effects);
        if (effects > 24) throw new IllegalStateException("Repeated casts accumulated effects: " + effects);
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArcEffect) throw new IllegalStateException("Nova spawned a legacy ArcEffect");
        }
        if (ticks == 30) server(LightningNovaClientSmoke::cast);
        if (ticks == 110) {
            if (!observed) throw new IllegalStateException("Missing nova VFX");
            capture("outward");
            server(p -> {
                outwardHealth = target.getHealth();
                if (outwardHealth >= 1024) throw new IllegalStateException("Main wave missed target");
            });
        }
        if (ticks == 180) server(p -> {
            if (target.getHealth() != outwardHealth) throw new IllegalStateException("Main wave hit twice");
        });
        if (ticks == 320) capture("echo");
        if (ticks == 490) server(p -> {
            float echoDamage = outwardHealth - target.getHealth();
            if (Math.abs(echoDamage - (1024 - outwardHealth) * 0.5f) > 0.01f)
                throw new IllegalStateException("Echo damage mismatch: " + echoDamage);
            target.discard();
        });
        // Twenty overlapping casts, each with outward and echo phases.
        if (ticks >= 520 && ticks < 720 && ticks % 10 == 0) server(LightningNovaClientSmoke::cast);
        if (ticks == 740) capture("repeated_casts");
        if (ticks == 1190) {
            if (effects != 0) throw new IllegalStateException("Nova effects survived their lifetime");
            System.out.println("[lightning-nova] PASSED: blue-white VFX, single hits and half-damage echo, 20 overlapping casts, no legacy entities, cleanup; peak graphs=" + peakEffects);
            capture("cleared");
            mc.stop();
        }
    }

    private static void cast(ServerPlayer player) {
        if (!LightningNova.Server.tryActivate(player)) throw new IllegalStateException("Nova activation rejected");
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.lightningNovaOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
