package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.AreaQueryService;
import org.academy.internal.client.ability.aeromanip.FlowSenseClient;
import org.academy.internal.client.ability.aeromanip.VacuumVisualClient;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.FlowSenseRuntime;
import org.academy.internal.common.ability.aeromanip.skills.lv2.BreathingBubble;
import org.misaka.MisakaNetworkClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Opt-in actual skill/visual validation, plus a virtual-tick query load matrix in a disposable world. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AeromanipAreaClientSmoke {
    private static boolean opened, configured;
    private static volatile boolean ready, matrixDone, reloadHeartbeatReady;
    private static volatile long reloadHeartbeatAt = Long.MAX_VALUE;
    private static volatile Throwable failure;
    private static int total, ticks;
    private static volatile long serverTicks;
    private static long previousServerTicks;
    private static CompletableFuture<Void> reload;
    private static final List<Cow> TARGETS = new ArrayList<>();
    private static final List<Long> serviceTimes = new ArrayList<>(), indexTimes = new ArrayList<>(), renderTimes = new ArrayList<>();
    private static final List<Long> prepareTimes = new ArrayList<>(), uploadTimes = new ArrayList<>();
    private static final StringBuilder REPORT = new StringBuilder();
    private static long sentStart, sentEnd;
    private static int observed;
    private static Matrix matrix;
    private static ServerPlayer owner;
    private static final List<Long> damageTicks = new ArrayList<>();
    private static float previousHealth = 1000;
    private static int minimumAir = Integer.MAX_VALUE;
    private static org.academy.api.client.render.vfxgraph.runtime.ActiveEffect vacuumEffect;

    @SubscribeEvent public static void discardPreviousFixtures(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (Boolean.getBoolean("academy.aeromanipAreaSmoke") && event.loadedFromDisk()
                && event.getEntity().entityTags().contains("academy_area_smoke")) event.setCanceled(true);
    }

    @SubscribeEvent public static void client(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("academy.aeromanipAreaSmoke")) return;
        if (failure != null) throw new IllegalStateException("Area smoke failed", failure);
        if (++total > 5000) throw new IllegalStateException("Area smoke timed out: " + ticks);
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
        mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(4); mc.player.xRotO = 4;
        if (!configured) {
            configured = true;
            server(p -> {
                owner = p;
                p.setGameMode(GameType.CREATIVE); p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(p.level().getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD),
                        .5, 205, 160.5, Set.of(), 0, 4, false);
                var system = AbilitySystemServer.getSystem(p);
                system.interruptActiveSkills(p);
                system.setPlayerAbilityCategory(p.getUUID(), AbilityCategories.AEROMANIP.get());
                system.setPlayerLevel(p.getUUID(), 5);
                for (var skill : List.of(Skills.FLOW_SENSE.get(), Skills.VACUUM_DOMAIN.get(), Skills.BREATHING_BUBBLE.get())) {
                    system.addPlayerSkill(p, skill.getKeyString()); system.setPlayerSkillProficiency(p.getUUID(), skill, 3000);
                }
                for (var skill : List.of(Skills.FLOW_SENSE.get(), Skills.VACUUM_DOMAIN.get())) if (skill.isEnabled(p)) skill.toggle(p);
                var data = system.getPlayerData(p.getUUID());
                data.setAcademyMaxCp(1000); data.getCpData().setMaxCP(1000); data.getCpData().setAvailableCP(1000);
                data.getCpData().setCurrMP(128);
                for (var entity : p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        p.getBoundingBox().inflate(80), entity -> entity.entityTags().contains("academy_area_smoke"))) entity.discard();
                for (int x = -15; x <= 15; x++) for (int y = 203; y <= 210; y++)
                    p.level().setBlock(new BlockPos(x, y, 166), Blocks.STONE.defaultBlockState(), 2);
                spawn(p, 96);
                ready = true;
            });
        }
        if (!ready || mc.gui.screen() != null || reload != null && !reload.isDone()) return;
        if (ticks == 219 && !reloadHeartbeatReady || ticks == 850 && !matrixDone) return;
        if (serverTicks == previousServerTicks) return;
        previousServerTicks = serverTicks;
        ticks++;
        if (ticks == 30) server(p -> Skills.FLOW_SENSE.get().toggle(p));
        if (ticks == 100) {
            require(FlowSenseClient.markerCount() >= 96, "All 96 targets, including invisible targets, must be marked");
            require(FlowSenseClient.renderer().drawn() > 0, "Flow marker draw must run");
            capture("flow-through-wall");
            server(p -> sentStart = FlowSenseRuntime.packetsSent());
        }
        if (ticks == 120) server(p -> {
            sentEnd = FlowSenseRuntime.packetsSent();
            require(sentEnd - sentStart < 30, "Stationary 96-target packet rate must fall by over 90% from 768/sec");
            TARGETS.getFirst().setPos(70, 205, 170);
        });
        if (ticks == 140) {
            require(FlowSenseClient.markerCount() == 95, "Leaving range removes a marker");
            server(p -> TARGETS.getFirst().setPos(0, 205, 172));
        }
        if (ticks == 170) reload = mc.reloadResourcePacks().thenRun(() -> server(p -> reloadHeartbeatAt = p.level().getGameTime() + 40));
        if (ticks == 220) {
            var field = FlowSenseClient.class.getDeclaredField("CACHE"); field.setAccessible(true);
            var cache = (org.academy.internal.client.ability.aeromanip.FlowObservationCache) field.get(null);
            var observedIds = new HashSet<Integer>(); cache.markers().forEach(marker -> observedIds.add(marker.sample.id()));
            capture("flow-reloaded"); server(p -> {
                var missing = new StringBuilder();
                for (var target : TARGETS) if (!observedIds.contains(target.getId())) missing.append(" id=").append(target.getId())
                        .append(" removed=").append(target.isRemoved()).append(" alive=").append(target.isAlive())
                        .append(" distance=").append(target.distanceTo(p)).append(" pos=").append(target.position());
                require(observedIds.size() >= 96, "Heartbeats refill markers after reload; count=" + observedIds.size() + " serverTick=" + p.level().getGameTime() + missing);
                Skills.FLOW_SENSE.get().toggle(p);
            });
        }
        if (ticks == 240) {
            require(FlowSenseClient.markerCount() == 0, "Disabling clears markers");
            server(p -> { TARGETS.getFirst().setInvulnerable(false); TARGETS.getFirst().setAirSupply(20); Skills.VACUUM_DOMAIN.get().toggle(p); });
        }
        if (ticks == 260) {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            vacuumEffect = vacuumEffect();
        }
        if (ticks == 280) {
            require(VacuumVisualClient.count() == 1, "One persistent vacuum visual must be active");
            require(vacuumEffect == vacuumEffect() && vacuumEffect.effect().buffer().count() > 0,
                    "Vacuum heartbeat must retain its active particle simulator");
            capture("vacuum-sustained");
            server(p -> {
                require(TARGETS.getFirst().getHealth() < 1000 && minimumAir <= -20,
                        "Vacuum must exhaust air and deal damage; health=" + TARGETS.getFirst().getHealth()
                                + " minimumAir=" + minimumAir + " removed=" + TARGETS.getFirst().isRemoved());
                require(damageTicks.size() >= 3, "Vacuum applies periodic damage");
                for (int i = 1; i < damageTicks.size(); i++) require(damageTicks.get(i) - damageTicks.get(i - 1) >= 10, "No catch-up damage bursts");
                Skills.VACUUM_DOMAIN.get().toggle(p);
            });
        }
        if (ticks == 310) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            require(VacuumVisualClient.count() == 0, "Vacuum visual must stop on disable");
            server(p -> require(!AbilitySystemServer.getSystem(p).getAeromanipResourceManager().isInUse(p), "Vacuum usage lease must close"));
            MisakaNetworkClient.send(BreathingBubble.CastPacket.INSTANCE);
        }
        if (ticks == 330) server(p -> {
            require(BreathingBubble.Server.protects(p), "Active breathing bubble protects its owner");
            var wolf = new net.minecraft.world.entity.animal.wolf.Wolf(EntityTypes.WOLF, p.level());
            wolf.setPos(p.getX() + 1, p.getY(), p.getZ()); wolf.tame(p); wolf.setNoAi(true); wolf.setNoGravity(true);
            wolf.addTag("academy_area_smoke"); p.level().addFreshEntity(wolf);
            require(BreathingBubble.Server.protects(wolf), "Nearby own pet is protected at milestone three");
            wolf.setPos(p.getX() + 10, p.getY(), p.getZ());
            require(!BreathingBubble.Server.protects(wolf), "A pet outside the actual bubble is not protected");
            wolf.setPos(p.getX() + 1, p.getY(), p.getZ()); BreathingBubble.Server.stop(p);
            require(!BreathingBubble.Server.protects(wolf), "Stopping a bubble invalidates its spatial index"); wolf.discard();
            spawn(p, 4224); TARGETS.getFirst().setInvulnerable(true);
            Skills.FLOW_SENSE.get().toggle(p);
        });
        if (ticks == 500) FlowSenseClient.renderer().beginGpuProfile();
        if (ticks > 500 && ticks < 790) {
            renderTimes.add(FlowSenseClient.renderer().lastNanos());
            prepareTimes.add(FlowSenseClient.renderer().prepareNanos());
            uploadTimes.add(FlowSenseClient.renderer().uploadNanos());
        }
        if (ticks == 790) {
            observed = FlowSenseClient.markerCount();
            REPORT.append(stats("marker GPU draw", FlowSenseClient.renderer().gpuSamples()));
            require(observed >= 4224, "No permanent 96-target display cap; observed=" + observed);
            capture("flow-dense-4224");
            server(p -> { Skills.FLOW_SENSE.get().toggle(p); matrix = new Matrix(p); });
        }
        if (ticks == 860) server(p -> {
            Skills.VACUUM_DOMAIN.get().toggle(p);
            AbilitySystemServer.getSystem(p).getPlayerData(p.getUUID()).getCpData().setCurrMP(0);
        });
        if (ticks == 890) {
            require(VacuumVisualClient.count() == 0, "Resource exhaustion clears the vacuum visual");
            server(p -> {
                require(!Skills.VACUUM_DOMAIN.get().isEnabled(p)
                        && !AbilitySystemServer.getSystem(p).getAeromanipResourceManager().isInUse(p), "Resource exhaustion disables and closes lease");
                Skills.FLOW_SENSE.get().toggle(p);
            });
        }
        if (ticks == 940) server(p -> p.teleportTo(p.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER),
                .5, 205, 160.5, Set.of(), 0, 4, false));
        if (ticks == 980) {
            require(mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER) && FlowSenseClient.markerCount() == 0,
                    "Changing dimension clears old observations");
            require(matrixDone, "Query matrix must finish");
            REPORT.append("Actual skill checks: 96 markers, invisible + wall, movement removal, reload, disable, vacuum damage/lease/visual, bubble owner/pet/exit/stop: PASS\n");
            REPORT.append("Stationary packets / 20 ticks: ").append(sentEnd - sentStart).append("; old theoretical full-rate: 768\n");
            REPORT.append("Dense marker count: ").append(observed).append('\n');
            REPORT.append("Vacuum damage ticks: ").append(damageTicks).append("; exhaustion and actual dimension transition: PASS\n");
            REPORT.append(stats("actual area service", serviceTimes)).append(stats("index maintenance", indexTimes)).append(stats("marker CPU frame", renderTimes));
            REPORT.append(stats("marker CPU preparation", prepareTimes)).append(stats("marker CPU upload", uploadTimes));
            Files.createDirectories(output()); Files.writeString(output().resolve("result.txt"), REPORT);
            System.out.println("[aeromanip-area-smoke] PASS\n" + REPORT);
            mc.stop();
        }
    }
    private static void spawn(ServerPlayer p, int maximum) {
        for (int i = TARGETS.size(); i < maximum; i++) {
            var mob = new Cow(EntityTypes.COW, p.level()) {
                @Override public boolean isPushable() { return false; }
            };
            mob.setPos((i % 24 - 12) * .7, 205 + (i / 24 % 16) * .5 - 4, 170 + i / 384);
            mob.setNoAi(true); mob.setNoGravity(true); mob.setInvulnerable(true); mob.setInvisible(i % 2 == 0);
            mob.setSilent(true); mob.addTag("academy_area_smoke");
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            mob.setHealth(1000); p.level().addFreshEntity(mob); TARGETS.add(mob);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void serverTick(LevelTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.aeromanipAreaSmoke") || owner == null || event.getLevel() != owner.level() || failure != null) return;
        try {
            serverTicks++;
            if (owner.level().getGameTime() >= reloadHeartbeatAt) reloadHeartbeatReady = true;
            if (ticks >= 240 && ticks <= 280 && !TARGETS.isEmpty()) {
                float health = TARGETS.getFirst().getHealth();
                minimumAir = Math.min(minimumAir, TARGETS.getFirst().getAirSupply());
                if (health < previousHealth) damageTicks.add(owner.level().getGameTime());
                previousHealth = health;
            }
            if (ticks >= 500 && ticks < 790) {
                var metrics = AreaQueryService.metrics(owner.level());
                require(metrics.candidates() <= AreaQueryService.MAX_CANDIDATES && metrics.effects() <= AreaQueryService.MAX_EFFECTS
                        && metrics.probes() <= AreaQueryService.MAX_PROBES, "Actual skill must obey shared hard budgets");
                serviceTimes.add(metrics.nanos());
                indexTimes.add(AreaQueryService.indexNanos(owner.level()));
            }
            if (matrix != null && !matrixDone) matrix.advance();
        } catch (Throwable problem) { failure = problem; }
    }
    private static final class Matrix {
        final ServerPlayer player;
        final int[] counts = {32, 96, 512, 4224}, owners = {1, 8, 32}, radii = {32, 400};
        final List<AreaQueryService.Session> sessions = new ArrayList<>();
        final List<Set<Integer>> seen = new ArrayList<>();
        final List<Long> samples = new ArrayList<>();
        int scenario, age;
        long virtualTick;
        Matrix(ServerPlayer player) { this.player = player; virtualTick = player.level().getGameTime(); setup(); }
        void setup() {
            age = 0; sessions.clear(); seen.clear(); samples.clear();
            int count = counts[scenario % 4], users = owners[scenario / 4 % 3];
            var ids = new HashSet<Integer>(); for (int i = 0; i < count; i++) ids.add(TARGETS.get(i).getId());
            for (int i = 0; i < users; i++) {
                var covered = new HashSet<Integer>(); seen.add(covered);
                sessions.add(AreaQueryService.open(player.level(), player, AreaQueryService.Point.BOUNDS_CENTER, new AreaQueryService.Effect() {
                    public boolean matches(net.minecraft.world.entity.LivingEntity target) { return ids.contains(target.getId()); }
                    public void apply(net.minecraft.world.entity.LivingEntity target, long tick) { covered.add(target.getId()); }
                }));
            }
        }
        void advance() {
            var level = player.level(); var clock = (ServerLevelData) level.getLevelData(); long original = level.getGameTime();
            try {
                for (int quantum = 0; quantum < 24 && !matrixDone; quantum++) {
                    clock.setGameTime(++virtualTick);
                    for (var session : sessions) session.update(player.position(), radii[scenario / 12], 2, 96);
                    AreaQueryService.tick(new LevelTickEvent.Post(() -> true, level));
                    var metrics = AreaQueryService.metrics(level); samples.add(metrics.nanos()); age++;
                    require(metrics.candidates() <= 256 && metrics.effects() <= 96 && metrics.probes() <= 4096, "Matrix hard budget");
                    int count = counts[scenario % 4];
                    if (seen.stream().allMatch(targets -> targets.size() == count)) {
                        REPORT.append("queries=").append(sessions.size()).append(" targets=").append(count)
                                .append(" radius=").append(radii[scenario / 12]).append(" fullCoverageTicks=").append(age)
                                .append(' ').append(stats("service", samples));
                        System.out.println("[aeromanip-area-smoke] matrix " + scenario + " ticks=" + age);
                        sessions.forEach(AreaQueryService.Session::close);
                        if (++scenario == 24) { matrixDone = true; break; }
                        setup();
                    }
                    require(age < 12000, "All sessions must eventually cover every target, scenario=" + scenario);
                }
            } finally { clock.setGameTime(original); }
        }
    }
    private static String stats(String label, List<Long> times) {
        if (times.isEmpty()) return label + ": no samples\n";
        var copy = new ArrayList<>(times); copy.sort(Long::compare);
        return String.format(Locale.ROOT, "%s n=%d p50=%.3f p95=%.3f max=%.3f ms%n", label, copy.size(),
                copy.get(copy.size()/2)/1e6, copy.get(copy.size()*95/100)/1e6, copy.getLast()/1e6);
    }
    private static org.academy.api.client.render.vfxgraph.runtime.ActiveEffect vacuumEffect() {
        return org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager.INSTANCE.activeEffects().stream()
                .filter(effect -> effect.assetKey().contains("aeromanip_vacuum_sustained") && !effect.isStopped())
                .findFirst().orElseThrow();
    }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> { try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable problem) { failure = problem; } });
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static Path output() { return Path.of(System.getProperty("academy.aeromanipAreaOutput")); }
    private static void capture(String name) {
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(output()); image.writeToFile(output().resolve(name + ".png")); }
            catch (Exception problem) { throw new IllegalStateException(problem); }
        });
    }
}
