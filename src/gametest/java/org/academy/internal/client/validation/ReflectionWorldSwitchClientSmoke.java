package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalImmunityLease;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Same-JVM regression for leaving a protected player, creating a new world and reopening the old
 * world with the same UUID. Opt-in only, with an isolated saves/test copy and a previously absent
 * saves/fresh world. Uses the real client disconnect/create/open paths and server lifecycle events.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ReflectionWorldSwitchClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.reflectionWorldSwitchSmoke");
    private enum Phase { LOAD, VERIFYING, LEAVE, CREATE, REOPEN, FINISHED }
    private static volatile Phase phase = Phase.LOAD;
    private static volatile String failure;
    private static volatile MinecraftServer owner;
    private static volatile ServerPlayer protectedPlayer;
    private static volatile TemporalImmunityLease oldLease;
    private static volatile UUID playerId;
    private static volatile int stoppedCount, logoutCount;
    private static int visit, ticks, stableTicks;
    private static boolean started;

    private ReflectionWorldSwitchClientSmoke() {}

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) throws IOException {
        if (!ENABLED || phase == Phase.FINISHED) return;
        var mc = Minecraft.getInstance();
        require(failure == null, "Server probe failed: " + failure);
        require(++ticks < 6000, "World switch timed out at " + phase + " visit " + visit);
        if (!started) {
            var directory = mc.gameDirectory.toPath().toRealPath();
            require(directory.getFileName().toString().startsWith("reflection-world-switch-smoke-"),
                    "Requires an isolated reflection-world-switch-smoke-* game directory");
            require(Files.isRegularFile(directory.resolve("saves/test/level.dat")), "Missing old world copy");
            require(!Files.exists(directory.resolve("saves/fresh")), "Fresh world must not exist before test");
            mc.options.pauseOnLostFocus = false;
            started = true;
        }
        if (phase == Phase.CREATE) {
            phase = Phase.LOAD;
            stableTicks = 0;
            mc.createWorldOpenFlows().createFreshLevel("fresh",
                    new LevelSettings("Reflection world switch smoke", GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                            true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261020L, false, false),
                    WorldPresets::createNormalWorldDimensions, new TitleScreen());
            return;
        }
        if (phase == Phase.REOPEN) {
            phase = Phase.LOAD;
            stableTicks = 0;
            mc.createWorldOpenFlows().openWorld("test", () -> {
                throw new AssertionError("Old world could not reopen after normal shutdown");
            });
            return;
        }
        if (mc.level == null || mc.player == null) return;
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        if (phase == Phase.LOAD && ++stableTicks >= 40) {
            phase = Phase.VERIFYING;
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            require(server != null, "Requires integrated server");
            if (visit == 0) playerId = id;
            require(id.equals(playerId), "All visits must use the same player UUID");
            require(server != owner, "A switch must create a distinct server instance");
            owner = server;
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(id);
                    require(player != null, "Player not joined");
                    protectedPlayer = player;
                    verifyReflection(player);
                    AcademyCraft.LOGGER.info("REFLECTION_WORLD_SWITCH_VISIT_PASSED visit={} uuid={}", visit, id);
                    phase = Phase.LEAVE;
                } catch (Throwable error) {
                    fail(error);
                }
            });
        } else if (phase == Phase.LEAVE) {
            // Keep reflection enabled until vanilla invokes ServerStopping and PlayerList.remove.
            var previous = owner;
            mc.disconnectFromWorld(Component.literal("Reflection world switch regression"));
            require(failure == null, "Shutdown probe failed: " + failure);
            require(previous.isShutdown(), "Old integrated server did not shut down");
            require(stoppedCount == visit + 1 && logoutCount == visit + 1,
                    "Missing normal stop/logout callbacks: " + stoppedCount + "/" + logoutCount);
            require(!oldLease.isActive(), "Old immunity lease survived shutdown");
            if (visit++ == 0) {
                phase = Phase.CREATE;
            } else if (visit == 2) {
                phase = Phase.REOPEN;
            } else {
                // Taking the vanilla storage lock proves the last shutdown also released it.
                try (var ignored = mc.getLevelSource().validateAndCreateAccess("test")) {
                    AcademyCraft.LOGGER.info("REFLECTION_WORLD_SWITCH_SMOKE_PASSED same_uuid=true "
                            + "fresh_world_created=true old_world_reopened=true stops={} logouts={} "
                            + "toggle_reacquire=true late_maintain_noop=true owner_thread_guard=true storage_lock_released=true",
                            stoppedCount, logoutCount);
                } catch (Exception error) {
                    throw new IllegalStateException("World lock was not released", error);
                }
                phase = Phase.FINISHED;
                mc.stop();
            }
        }
    }

    private static void verifyReflection(ServerPlayer player) {
        var temporal = TemporalApi.get(player);
        if (visit == 1) {
            require(!VectorReflection.Server.isActive(player), "Fresh world inherited old skill state");
            require(!temporal.isTimeStopImmune(player), "Fresh world inherited old immunity");
        }
        if (visit > 0) {
            // A deliberately retained old handle still rejects use from a different server thread.
            // The fix must isolate owners, not disable TemporalRuntime's thread checks.
            boolean rejected = false;
            try {
                oldLease.close();
            } catch (IllegalStateException expected) {
                rejected = expected.getMessage().contains("server thread");
            }
            require(rejected, "Stale lease lost its owning-thread guard");
        }
        var system = AbilitySystemServer.getSystem(player);
        var id = player.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(id, 5);
        system.setPlayerBaseMaxCP(id, 1000000);
        system.setPlayerAvailableCP(id, 1000000);
        system.addPlayerSkill(player, Skills.VECTOR_REFLECTION.get().getKeyString());
        var skill = Skills.VECTOR_REFLECTION.get();
        if (!skill.isEnabled(player)) skill.toggle(player);
        VectorReflectionRuntime.maintain(player);
        require(VectorReflection.Server.isActive(player) && temporal.isTimeStopImmune(player),
                "Reflection did not acquire immunity");
        VectorReflection.Server.forceDeactivate(player);
        require(!VectorReflection.Server.isActive(player) && !temporal.isTimeStopImmune(player),
                "Deactivation left immunity behind");
        skill.toggle(player);
        VectorReflectionRuntime.maintain(player);
        require(VectorReflection.Server.isActive(player) && temporal.isTimeStopImmune(player),
                "Reflection could not reacquire immunity");
        oldLease = temporal.acquireTimeStopImmunity(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopping(ServerStoppingEvent event) {
        if (!ENABLED || event.getServer() != owner || protectedPlayer == null) return;
        try {
            // AcademyCraftServer has already stopped both services. A late removal-defense
            // callback must not reacquire a lease, even while the saved skill is still enabled.
            require(!oldLease.isActive(), "Temporal service was not stopped first");
            VectorReflectionRuntime.maintain(protectedPlayer);
            require(!TemporalApi.get(protectedPlayer).isTimeStopImmune(protectedPlayer),
                    "Late maintenance reacquired immunity");
        } catch (Throwable error) {
            fail(error);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ENABLED || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().getServer() != owner || !player.getUUID().equals(playerId)) return;
        logoutCount++;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        if (ENABLED && event.getServer() == owner) stoppedCount++;
    }

    private static void fail(Throwable error) {
        failure = error.toString();
        AcademyCraft.LOGGER.error("REFLECTION_WORLD_SWITCH_SMOKE_FAILED visit=" + visit, error);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
