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
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.BlockSurfaceOcclusionSampler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.render.vfx.DarkmatterSixWingsVfxClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/** Actual block collision sampling, separated from the GPU fixture-plane photographs. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SixWingsBlockClientSmoke {
    private static boolean opened, configured, finished;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static int total, warmup, ticks;
    private static ActiveEffect wings;
    private static int blockedCount;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.sixWingsBlockSmoke") || finished) return;
        if (failure != null) throw new IllegalStateException("Wing block scenario failed", failure);
        if (++total > 2500) throw new IllegalStateException("Wing block scenario timed out");
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed to main menu")) {
                    button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0))); break;
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true; mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.yBodyRot = 0; mc.player.yBodyRotO = 0;
        mc.player.setXRot(0); mc.player.xRotO = 0;
        if (!configured) {
            configured = true;
            // Other isolated pressure fixtures can persist a gravity-free viewer in this shared save.
            mc.player.setNoGravity(false);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.fov().set(100);
            server(p -> {
                for (int x = -16; x <= 16; x++) for (int z = 169; z <= 200; z++) {
                    p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    for (int y = 81; y <= 94; y++) p.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(100))) old.discard();
                for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(100))) old.discard();
                p.setGameMode(GameType.CREATIVE); p.setNoGravity(false); p.getInventory().clearContent(); p.removeAllEffects();
                p.getAbilities().flying = false; p.onUpdateAbilities();
                p.teleportTo(p.level(), 0, 81, 185, Set.of(), 0, 0, false);
                command(p, "time set noon"); command(p, "weather clear");
                var system = AbilitySystemServer.getSystem(p);
                var skill = Skills.DARKMATTER_SIX_WINGS.get();
                system.setPlayerAbilityCategory(p.getUUID(), skill.getCategory());
                system.setPlayerLevel(p.getUUID(), 5); system.addPlayerSkill(p, skill.getKeyString());
                var data = system.getPlayerData(p.getUUID()); data.setAcademyMaxCp(2000);
                data.getCpData().setMaxCP(2000); data.getCpData().setAvailableCP(2000);
                var resources = system.getDarkmatterResourceManager();
                resources.reconcile(p); resources.debugSetPools(p, 100, 0, 0, 0);
                require(DarkmatterSixWings.Server.debugSetActive(p, false), "Server gamma state starts disabled");
                ready = true;
            });
        }
        if (!ready || ++warmup < 200) return;
        ticks++;
        require(BlockSurfaceOcclusionSampler.sharedTickQueryCount() <= BlockSurfaceOcclusionSampler.sharedTickQueryLimit(),
                "Shared collision budget is bounded", false);
        if (ticks == 10) server(p -> require(DarkmatterSixWings.Server.debugSetActive(p, true), "Authoritative gamma state enabled"));
        if (ticks == 25) {
            wings = VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> e.assetKey().endsWith("darkmatter_six_wings"))
                    .findFirst().orElseThrow();
            diagnoseBoundary("open");
            require(mc.player.onGround() && !mc.player.isNoGravity() && !mc.player.getAbilities().flying,
                    "Open view uses a real grounded player with gravity enabled");
            require(clipped() == 0, "Open space has no artificial clip plane"); capture("angles_open");
        }
        if (ticks == 30) server(p -> wall(p, true));
        if (ticks == 45) {
            blockedCount = clipped();
            require(blockedCount > 0 && blockedCount < wings.effect().buffer().count(), "Real wall clips only the intersecting wing groups");
            require(wings.effect().floatParameter("block_penetration", -1) == 0, "Default material obeys block boundary");
            capture("wall_blocked");
        }
        if (ticks == 55) wings.effect().setLiveParam("block_penetration", Value.of(.35f));
        if (ticks == 65) { require(clipped() > 0, "Partial penetration retains the real boundary"); capture("wall_partial"); }
        if (ticks == 75) wings.effect().setLiveParam("block_penetration", Value.of(1f));
        if (ticks == 85) { require(clipped() == 0, "Full visual penetration bypasses boundary sampling"); capture("wall_penetrating"); }
        if (ticks == 95) { wings.effect().setLiveParam("block_penetration", Value.of(0f)); server(p -> wall(p, false)); }
        if (ticks == 115) { require(clipped() == 0, "Removing real blocks releases cached boundaries"); capture("wall_removed"); }
        if (ticks == 120) server(p -> require(DarkmatterSixWings.Server.debugSetActive(p, false), "Server gamma state stopped"));
        if (ticks == 140) {
            require(DarkmatterSixWingsVfxClient.activeCount() == 0, "Stopped wings release their sampler and graph");
            try {
                Files.createDirectories(output());
                Files.writeString(output().resolve("result.txt"), "PASSED\nReal wall clipped " + blockedCount
                        + " of 132 feather slots.\nBlocked / 0.35 partial / full penetration checked.\nRemoval and stop clear boundaries.\n");
            } catch (Exception e) { throw new RuntimeException(e); }
            finished = true; System.out.println("[six-wings-block-smoke] PASSED"); mc.stop();
        }
    }

    private static int clipped() {
        var buffer = wings.effect().buffer(); int count = 0;
        for (int i = 0; i < buffer.count(); i++) if (Math.abs(buffer.surfaceClipX(i)) + Math.abs(buffer.surfaceClipY(i))
                + Math.abs(buffer.surfaceClipZ(i)) > .001f) count++;
        return count;
    }
    private static void diagnoseBoundary(String stage) {
        var actor = Minecraft.getInstance().player;
        var graph = wings.effect();
        System.out.println("[six-wings-block-smoke] Diagnostic " + stage + " position=" + actor.position()
                + " onGround=" + actor.onGround() + " flying=" + actor.getAbilities().flying
                + " noGravity=" + actor.isNoGravity()
                + " fallFlying=" + actor.isFallFlying() + " anchor=" + wings.position() + " rotation=" + wings.rotation()
                + " grounded=" + graph.floatParameter("grounded", -1) + " spread=" + graph.floatParameter("spread", -1)
                + " span=" + graph.floatParameter("span", -1) + " rootHeight=" + graph.floatParameter("root_height", -1)
                + " rootDepth=" + graph.floatParameter("root_depth", -1) + " time=" + graph.floatParameter("time", -1));
        var buffer = graph.buffer();
        for (int i = 0; i < buffer.count(); i++) {
            if (Math.abs(buffer.surfaceClipX(i)) + Math.abs(buffer.surfaceClipY(i)) + Math.abs(buffer.surfaceClipZ(i)) < .001f) continue;
            System.out.println("[six-wings-block-smoke] Diagnostic slot=" + i + " plane=" + buffer.surfaceClipX(i)
                    + "," + buffer.surfaceClipY(i) + "," + buffer.surfaceClipZ(i) + "," + buffer.surfaceClipW(i)
                    + " alpha=" + buffer.alpha(i)
                    + " root=" + buffer.positionX(i) + "," + buffer.positionY(i) + "," + buffer.positionZ(i)
                    + " axis=" + buffer.velocityX(i) + "," + buffer.velocityY(i) + "," + buffer.velocityZ(i));
        }
    }
    private static void wall(ServerPlayer p, boolean present) {
        for (int y = 81; y <= 86; y++) for (int z = 181; z <= 189; z++)
            p.level().setBlock(new BlockPos(-2, y, z), present ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
    }
    private static void command(ServerPlayer p, String text) {
        var server = p.level().getServer(); server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), text);
    }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable e) { failure = e; }
        });
    }
    private static void require(boolean condition, String message) { require(condition, message, true); }
    private static void require(boolean condition, String message, boolean log) {
        if (!condition) throw new IllegalStateException(message);
        if (log) System.out.println("[six-wings-block-smoke] " + message);
    }
    private static Path output() { return Path.of(System.getProperty("academy.sixWingsBlockOutput")); }
    private static void capture(String name) {
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(output()); image.writeToFile(output().resolve(name + ".png")); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
