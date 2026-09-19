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
import org.academy.internal.client.render.vfx.DarkmatterVfxClient;
import org.academy.internal.common.ability.darkmatter.skills.lv3.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterRepair;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.DarkmatterGraphEffects;
import org.academy.internal.common.ability.Skills;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in isolated in-game validation. Never included in the mod jars. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DarkmatterVfxClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static int ticks, total;
    private static float pitch;
    private static float yaw;
    private static net.minecraft.world.entity.LivingEntity hostile, friendly;
    private static volatile Throwable serverFailure;
    private static org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile feather;
    private static net.minecraft.world.entity.LivingEntity behind;
    private static float targetBefore, behindBefore;
    private static boolean observedExit;
    private static boolean capturedContact;

    @SubscribeEvent public static void entityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (event.getEntity() == feather && !feather.level().isClientSide() && friendly != null) {
            observedExit |= feather.hasPassedTarget() && !feather.isRemoved()
                    && feather.getZ() > friendly.getBoundingBox().maxZ;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.darkmatterVfxSmoke")) return;
        var mc = Minecraft.getInstance();
        if (serverFailure != null) throw new IllegalStateException("Server-side visual check failed", serverFailure);
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
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            server(p -> {
                for (int x = -16; x <= 16; x++) for (int z = 160; z <= 245; z++) {
                    p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
                for (int x=-8;x<=8;x++) for(int z=170;z<=190;z++) for(int y=81;y<=85;y++) p.level().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
                p.setGameMode(GameType.CREATIVE);
                p.getInventory().clearContent();
                p.setHealth(p.getMaxHealth());
                learn(p, Skills.DARKMATTER_GENERATION.get(), Skills.DARKMATTER_SHAPING.get(),
                        Skills.DARKMATTER_PHASE_TUNING.get(), Skills.DARKMATTER_RADIATION.get(),
                        Skills.DARKMATTER_REPAIR.get(), Skills.DARKMATTER_DISASSEMBLE.get());
                Skills.DARKMATTER_REPAIR.get().toggle(p);
                var manager = AbilitySystemServer.getSystem(p).getDarkmatterResourceManager();
                manager.reconcile(p); manager.debugSetPools(p, 100, 0, 0, 0); manager.setAlphaPoints(p, 200);
                for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(80))) old.discard();
                var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityTypes.ZOMBIE, p.level());
                zombie.setPos(0, 81, 180); zombie.setNoAi(true); zombie.setNoGravity(true);
                zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
                zombie.setHealth(1000); p.level().addFreshEntity(zombie); hostile = zombie;
                command(p, "time set noon");
                command(p, "weather clear");
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        if (ticks == 25) capture("client_day_before");
        if (ticks >= 30 && ticks <= 88 && ticks % 20 == 10) server(p -> {
            AbilitySystemServer.getSystem(p).getDarkmatterResourceManager().debugSetPools(p, 100, 0, 0, 0);
            require(DarkmatterRadiation.Server.beginChannel(p), "Actual radiation channel active");
        });
        if (ticks == 40) require(org.academy.internal.client.render.vfx.InterferenceFieldClient.activeCount() == 1, "Authoritative range field active");
        if (ticks == 46) {
            require(org.academy.internal.client.render.vfx.InterferenceFieldClient.activeCount() == 1, "Repeated pulses reuse the same field");
            require(find("darkmatter_interference") == null, "Range uses scene shaders without interference beams or arcs");
            capture("client_day_active");
        }
        if (ticks >= 40 && ticks <= 64 && ticks % 2 == 0) capture("motion/day_" + String.format("%03d", ticks));
        var contact = find("darkmatter_light_contact");
        if (!capturedContact && ticks < 90 && contact != null && maxAlpha(contact) > 0.4f) {
            capturedContact = true;
            capture("client_interference_contact");
        }
        if (ticks == 70) server(p -> command(p,"time set midnight"));
        if (ticks == 84) capture("client_night_active");
        if (ticks == 90) server(DarkmatterRadiation.Server::endChannel);
        if (ticks == 104) {
            require(org.academy.internal.client.render.vfx.InterferenceFieldClient.activeCount() == 0, "Explicit stop clears maintained field");
            capture("client_night_after");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            server(p -> { command(p,"time set noon"); hostile.discard(); });
        }
        if (ticks == 115) server(p -> Skills.DARKMATTER_REPAIR.get().toggle(p));
        if (ticks >= 116 && ticks <= 140 && ticks % 5 == 1) server(p -> {
            AbilitySystemServer.getSystem(p).getDarkmatterResourceManager().debugSetPools(p, 100, 0, 0, 0);
            p.setHealth(8); require(DarkmatterRepair.Server.tryPulse(p), "Actual healing pulse succeeds");
        });
        if (ticks == 119) capture("client_repair_body");
        if (ticks == 129) {
            require(DarkmatterVfxClient.activeCount() <= 1, "Repair pulses merge into one attachment");
            capture("client_repair_body_continuous");
        }
        if (ticks == 145) server(p -> { Skills.DARKMATTER_REPAIR.get().toggle(p); p.setHealth(p.getMaxHealth()); });
        if (ticks == 155) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> {
                var stack = new ItemStack(org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get());
                DarkmatterItemUtil.setIntegrity(stack, 0); p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
                AbilitySystemServer.getSystem(p).getDarkmatterResourceManager().debugSetPools(p, 100, 0, 0, 0);
                Skills.DARKMATTER_REPAIR.get().toggle(p);
            });
        }
        if (ticks == 163) { require("darkmatter_repair"); capture("client_repair_hand"); }
        if (ticks == 175) server(p -> {
            p.getInventory().clearContent();
            var stack = new ItemStack(org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get());
            DarkmatterItemUtil.setIntegrity(stack, 0); p.getInventory().setItem(10, stack);
            AbilitySystemServer.getSystem(p).getDarkmatterResourceManager().debugSetPools(p, 100, 0, 0, 0);
        });
        if (ticks == 191) {
            require(find("darkmatter_repair") == null, "Inventory-only repair has no world patch");
            capture("client_repair_inventory_only");
            server(p -> {
                p.getInventory().clearContent();
                var manager = AbilitySystemServer.getSystem(p).getDarkmatterResourceManager();
                float before = manager.getView(p).totalMatter();
                require(!DarkmatterRepair.Server.tryPulse(p), "Idle repair does no work");
                require(manager.getView(p).totalMatter() == before, "Idle repair spends no MP");
                Skills.DARKMATTER_REPAIR.get().toggle(p);
                var cow = new net.minecraft.world.entity.animal.cow.Cow(net.minecraft.world.entity.EntityTypes.COW, p.level());
                cow.setPos(0, 81, 178); cow.setNoAi(true); cow.setNoGravity(true);
                cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                cow.setHealth(1000); p.level().addFreshEntity(cow); friendly = cow;
            });
        }
        if (ticks == 205) server(p -> require(DarkmatterDisassemble.Server.tryProgramAttack(p, friendly, 16, 0.01f, 0), "Actual disassemble hit succeeds"));
        if (ticks == 207) server(p -> require(friendly.isAlive(), "Nonterminal target remains alive"));
        if (ticks == 208) { require("darkmatter_disassemble"); capture("client_disassemble_surface"); }
        if (ticks >= 208 && ticks <= 217) capture("motion/disassemble_" + ticks);
        if (ticks == 213) capture("client_disassemble_smoke");
        if (ticks == 225) server(p -> { friendly.setHealth(1); require(DarkmatterDisassemble.Server.tryProgramAttack(p, friendly, 16, 2, 0), "Actual terminal disassemble succeeds"); });
        if (ticks == 227) server(p -> require(!friendly.isAlive(), "Terminal target actually died"));
        if (ticks == 228) capture("client_disassemble_terminal");
        if (ticks == 246) server(p -> {
            var pos = new BlockPos(0, 82, 178);
            p.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            require(DarkmatterDisassemble.Server.tryProgramDestroyBlock(p, pos, 16, 0), "Actual block removal succeeds");
            require(p.level().getBlockState(pos).isAir(), "Block effect follows actual removal");
        });
        if (ticks == 249) capture("client_disassemble_block");
        if (ticks >= 249 && ticks <= 258) capture("motion/block_" + ticks);
        if (ticks == 254) capture("client_disassemble_block_smoke");
        if (ticks == 270) server(p -> {
            for (int x=-7;x<=7;x++) for(int z=170;z<=190;z++) p.level().setBlock(new BlockPos(x,84,z),Blocks.STONE.defaultBlockState(),3);
            AbilitySystemServer.getSystem(p).getDarkmatterResourceManager().debugSetPools(p, 100, 0, 0, 0);
            DarkmatterRadiation.Server.beginChannel(p);
        });
        if (ticks == 282) capture("client_indoor_active");
        if (ticks == 286) server(DarkmatterRadiation.Server::endChannel);
        if (ticks == 300) server(p -> DarkmatterGraphEffects.interference(p, 16));
        if (ticks == 320) require(org.academy.internal.client.render.vfx.InterferenceFieldClient.activeCount() == 0, "Lost refresh times out safely");
        if (ticks == 335) {
            yaw = -22;
            server(p -> {
            p.teleportTo(p.level(), -3, 81, 174.5, Set.of(), yaw, 0, false);
            for(int z=174;z<=190;z++) for(int x=-7;x<=7;x++) p.level().setBlock(new BlockPos(x,84,z),Blocks.AIR.defaultBlockState(),3);
            for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40))) old.discard();
            var target = new net.minecraft.world.entity.animal.cow.Cow(net.minecraft.world.entity.EntityTypes.COW, p.level());
            var other = new net.minecraft.world.entity.animal.cow.Cow(net.minecraft.world.entity.EntityTypes.COW, p.level());
            target.setPos(0,81,180); other.setPos(0,81,183);
            for(var mob : java.util.List.of(target,other)) { mob.setNoAi(true); mob.setNoGravity(true); p.level().addFreshEntity(mob); }
            friendly=target; behind=other; targetBefore=target.getHealth(); behindBefore=other.getHealth();
            feather = new org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile(
                    org.academy.internal.common.world.entity.EntityTypes.DARKMATTER_FEATHER_PROJECTILE.get(), p.level());
            feather.configure(p,target,p.getLookAngle(),1,0,true);
            feather.setPos(0, p.getEyeY() - 0.15, 174.5);
            feather.setDeltaMovement(target.getBoundingBox().getCenter().subtract(feather.position()).normalize().scale(1.65));
            p.level().addFreshEntity(feather);
            });
        }
        if(ticks>=336 && ticks<=345) capture("motion/feather_"+String.format("%03d",ticks));
        if(ticks==337) { require("darkmatter_feather"); capture("client_feather_flight"); }
        if(ticks==350) server(p -> {
            require(observedExit, "Feather remains visible beyond the target's far surface");
            require(friendly.getHealth()<targetBefore, "Feather damages its target");
            require(behind.getHealth()==behindBefore, "Visual penetration does not damage a second target");
            require(feather.isRemoved(), "Feather exit has a bounded lifetime");
        });
        if (ticks == 365) {
            require(DarkmatterVfxClient.activeCount() == 0, "All darkmatter effects clear after stop/leases");
            capture("client_cleared");
            System.out.println("[darkmatter-vfx-smoke] PASSED");
            mc.stop();
        }
    }

    private static void command(ServerPlayer player, String command) {
        var server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
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
        data.setAcademyMaxCp(500); data.getCpData().setMaxCP(500); data.getCpData().setAvailableCP(500);
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
        System.out.println("[darkmatter-vfx-smoke] " + message);
    }
    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static void capture(String name) {
        var output = Path.of(System.getProperty("academy.darkmatterVfxOutput")).resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(output.getParent()); image.writeToFile(output); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }
}
