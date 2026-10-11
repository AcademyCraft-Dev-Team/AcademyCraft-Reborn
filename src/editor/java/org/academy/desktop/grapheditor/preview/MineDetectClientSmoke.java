package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.electromaster.mine.MineDetectClient;
import org.academy.internal.client.ability.electromaster.mine.OreScanCache;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in real skill, scan, GPU and block-update regression in a copied disposable test world. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MineDetectClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static int ticks, total, activeTicks, nearReadyTick;
    private static float yaw;
    private static final ArrayList<Long> timings = new ArrayList<>();
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static com.mojang.blaze3d.systems.GpuQueryPool queries;
    private record FrameSample(int slot, boolean enabled) { }
    private static final ArrayList<FrameSample> frames = new ArrayList<>();
    private static int recording = -1, baselineFrames, enabledFrames;
    private static final java.util.List<BlockPos> STRESS = stressPositions();

    private static java.util.List<BlockPos> stressPositions() {
        if (!Boolean.getBoolean("academy.mineDetectSmoke")) return java.util.List.of();
        var result = new ArrayList<BlockPos>();
        for (int y = -24; y <= 24; y += 3) for (int x = -45; x <= 45; x += 3) for (int z = -45; z <= 45; z += 3) {
            int distance = x * x + y * y + z * z;
            if (distance <= 100 || distance > 57 * 57) continue;
            result.add(new BlockPos(x, 161 + y, 160 + z));
            if (result.size() == 12288) return java.util.List.copyOf(result);
        }
        throw new IllegalStateException("Stress fixture has too few positions");
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.mineDetectSmoke")) return;
        var mc = Minecraft.getInstance();
        if (serverFailure != null) throw new IllegalStateException("Mine detect fixture failed", serverFailure);
        if (++total > 3000) throw new IllegalStateException("Mine detect smoke timed out");
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed to main menu")) {
                    button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                    break;
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
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(12); mc.player.xRotO = 12;
        if (!configured) {
            configured = true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            server(p -> {
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(p.level(), 0.5, 161, 160.5, Set.of(), 0, 12, false);
                var system = AbilitySystemServer.getSystem(p);
                system.interruptActiveSkills(p);
                system.setPlayerAbilityCategory(p.getUUID(), AbilityCategories.ELECTROMASTER.get());
                system.setPlayerLevel(p.getUUID(), 5);
                system.addPlayerSkill(p, Skills.MINE_DETECT.get().getKeyString());
                if (Skills.MINE_DETECT.get().isEnabled(p)) Skills.MINE_DETECT.get().toggle(p);
                var data = system.getPlayerData(p.getUUID());
                data.setAcademyMaxCp(1000);
                data.getCpData().setMaxCP(1000);
                data.getCpData().setAvailableCP(1000);
                for (int x = -16; x < 16; x++) for (int y = 132; y < 148; y++) for (int z = 184; z < 208; z++)
                    p.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                for (var pos : STRESS) p.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                for (int x = -18; x <= 18; x++) for (int z = 155; z <= 182; z++)
                    p.level().setBlock(new BlockPos(x, 159, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                p.level().setBlock(new BlockPos(0, 161, 162), Blocks.DIAMOND_ORE.defaultBlockState(), 2);
                p.level().setBlock(new BlockPos(0, 165, 160), Blocks.IRON_ORE.defaultBlockState(), 2);
                p.level().setBlock(new BlockPos(0, 161, 168), Blocks.GOLD_ORE.defaultBlockState(), 2);
                for (int x = -6; x <= -4; x++) for (int y = 160; y <= 162; y++)
                    p.level().setBlock(new BlockPos(x, y, 170), Blocks.EMERALD_ORE.defaultBlockState(), 2);
                for (int x = 3; x <= 6; x++) for (int y = 160; y <= 164; y++)
                    p.level().setBlock(new BlockPos(x, y, 173), Blocks.STONE.defaultBlockState(), 2);
                p.level().setBlock(new BlockPos(4, 161, 175), Blocks.REDSTONE_ORE.defaultBlockState(), 2);
                ready = true;
            });
        }
        if (!ready || reload != null && !reload.isDone()) return;
        ticks++;
        var scan = MineDetectClient.scan();
        if (scan != null) {
            activeTicks++;
            timings.add(MineDetectClient.lastTickNanos());
            if (nearReadyTick == 0 && scan.oreAt(0, 161, 162) != null && scan.oreAt(0, 165, 160) != null
                    && scan.oreAt(0, 161, 168) != null) nearReadyTick = activeTicks;
        }
        if (ticks == 30) capture("disabled");
        if (ticks == 40) {
            activeTicks = nearReadyTick = 0;
            server(p -> Skills.MINE_DETECT.get().toggle(p));
        }
        if (ticks == 100) {
            require(nearReadyTick > 0 && nearReadyTick <= 4, "Near ores must be discovered in the first four active ticks");
            require(MineDetectClient.renderer() != null && MineDetectClient.renderer().frames() > 0, "GPU silhouette passes must run");
            capture("silhouette");
            captureMask("mask");
        }
        if (ticks == 120) yaw = 25;
        if (ticks == 130) capture("oblique");
        if (ticks == 140) server(p -> p.level().setBlock(new BlockPos(0, 161, 162), Blocks.AIR.defaultBlockState(), 3));
        if (ticks == 148) require(scan != null && scan.oreAt(0, 161, 162) == null, "Mining must remove cached ore immediately");
        if (ticks == 160) server(p -> p.level().setBlock(new BlockPos(0, 161, 162), Blocks.COPPER_ORE.defaultBlockState(), 3));
        if (ticks == 168) require(scan != null && scan.oreAt(0, 161, 162) != null, "Placed ore must enter the cache immediately");
        if (ticks == 180) {
            yaw = 0;
            server(p -> {
                for (var pos : STRESS) p.level().setBlock(pos, Blocks.COAL_ORE.defaultBlockState(), 2);
            });
        }
        if (ticks == 440) {
            require(scan != null, "Skill remains enabled");
            int count = 0;
            for (var pos : STRESS) if (scan.oreAt(pos.getX(), pos.getY(), pos.getZ()) != null) count++;
            require(count == 12288, "All 12,288 stress ores must remain cached, actual=" + count);
            capture("dense");
            reload = mc.reloadResourcePacks();
        }
        if (ticks == 520) {
            require(scan != null && scan.oreAt(0, 161, 162) != null, "Resource reload must recover nearby ore");
            capture("reloaded");
            server(p -> Skills.MINE_DETECT.get().toggle(p));
        }
        if (ticks == 540) require(MineDetectClient.scan() == null, "Disabling releases scan and GPU resources");
        if (ticks == 550) server(p -> Skills.MINE_DETECT.get().toggle(p));
        if (ticks == 590) {
            require(scan != null && scan.oreAt(0, 161, 162) != null, "Re-enabling starts a new nearby scan");
            timings.sort(Long::compare);
            var text = "PASSED: nearReadyTicks=" + nearReadyTick + ", stressCount=12288, scanTickMedianMs="
                    + timings.get(timings.size() / 2) / 1e6 + ", scanTickP95Ms="
                    + timings.get(timings.size() * 95 / 100) / 1e6 + ", scanTickMaxMs=" + timings.getLast() / 1e6;
            try { Files.writeString(output().resolve("result.txt"), text); }
            catch (Exception e) { throw new IllegalStateException(e); }
            System.out.println("[mine-detect-smoke] " + text);
            reportGpu();
            mc.stop();
        }
    }

    @SubscribeEvent public static void beforeFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        if (!Boolean.getBoolean("academy.mineDetectSmoke")) return;
        boolean baseline = ticks >= 525 && ticks < 549 && baselineFrames < 120;
        boolean enabled = ticks >= 390 && ticks < 430 && enabledFrames < 120;
        if (!baseline && !enabled) return;
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        if (queries == null) queries = device.createTimestampQueryPool(512);
        recording = frames.size() * 2;
        frames.add(new FrameSample(recording, enabled));
        if (enabled) enabledFrames++; else baselineFrames++;
        device.createCommandEncoder().writeTimestamp(queries, recording);
    }

    @SubscribeEvent public static void afterFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if (recording < 0) return;
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, recording + 1);
        recording = -1;
    }

    private static void reportGpu() {
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        var before = new ArrayList<Double>();
        var after = new ArrayList<Double>();
        for (var sample : frames) {
            var start = queries.getValue(sample.slot);
            var end = queries.getValue(sample.slot + 1);
            if (start.isPresent() && end.isPresent()) (sample.enabled ? after : before).add(
                    (end.getAsLong() - start.getAsLong()) * device.getDeviceInfo().timestampPeriod() / 1_000_000.0);
        }
        before.sort(Double::compare);
        after.sort(Double::compare);
        require(before.size() >= 20 && after.size() >= 20, "GPU timestamp samples must be available");
        String report = String.format(java.util.Locale.ROOT,
                "1280x720 GPU frame time (ms): disabled n=%d median=%.3f p95=%.3f; enabled n=%d median=%.3f p95=%.3f%n",
                before.size(), before.get(before.size() / 2), before.get(before.size() * 95 / 100),
                after.size(), after.get(after.size() / 2), after.get(after.size() * 95 / 100));
        try { Files.writeString(output().resolve("performance.txt"), report); }
        catch (Exception e) { throw new IllegalStateException(e); }
        System.out.print("[mine-detect-smoke] " + report);
        queries.close();
        queries = null;
    }

    private static Path output() { return Path.of(System.getProperty("academy.mineDetectOutput")); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static void capture(String name) { save(name, Minecraft.getInstance().gameRenderer.mainRenderTarget()); }
    private static void captureMask(String name) { save(name, MineDetectClient.renderer().mask()); }
    private static void save(String name, com.mojang.blaze3d.pipeline.RenderTarget target) {
        Screenshot.takeScreenshot(target, image -> {
            try (image) {
                Files.createDirectories(output());
                image.writeToFile(output().resolve(name + ".png"));
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
