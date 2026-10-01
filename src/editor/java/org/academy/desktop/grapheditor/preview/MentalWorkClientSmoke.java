package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.mentalout.control.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in real client/server work benchmark in an isolated copy of the validation world. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MentalWorkClientSmoke {
    private static final Identifier SOURCE = Identifier.parse("academy:mental_work_smoke");
    private static final List<LivingEntity> WORKERS = new ArrayList<>();
    private static final List<BlockWorkRegion> REGIONS = new ArrayList<>();
    private static final List<BlockPos> OUTPUTS = new ArrayList<>();
    private static final List<Long> TIMES = new ArrayList<>();
    private static final List<Long> TICK_TIMES = new ArrayList<>();
    private static final List<LivingEntity> BACKGROUND = new ArrayList<>();
    private static final long[] GROUP_OUTPUT = new long[4];
    private static final long[] OPERATIONS = new long[WorkBudget.Operation.values().length];
    private static boolean opened, configured;
    private static volatile boolean ready, done;
    private static volatile Throwable failure;
    private static ServerPlayer owner;
    private static int age;
    private static long harvested, fixtureNanos, tickStart;

    @SubscribeEvent public static void discardOldFixtures(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (Boolean.getBoolean("academy.mentalWorkSmoke") && event.loadedFromDisk()
                && (event.getEntity().entityTags().contains("academy_work_smoke")
                || event.getEntity().entityTags().contains("academy_area_smoke"))) event.setCanceled(true);
    }

    @SubscribeEvent public static void client(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("academy.mentalWorkSmoke")) return;
        var mc = Minecraft.getInstance();
        if (failure != null) {
            Files.createDirectories(output());
            Files.writeString(output().resolve("failure.txt"), failure.toString());
            mc.stop();
            return;
        }
        if (done) { mc.stop(); return; }
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
        if (mc.player == null || mc.getSingleplayerServer() == null || configured) return;
        configured = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.BLOCKS).set(0.0);
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { setup(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable problem) { failure = problem; }
        });
    }

    private static void setup(ServerPlayer player) {
        owner = player;
        var level = player.level().getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        GroupControlRuntime.clear();
        var data = WorkOrderData.get(level.getServer());
        if (Boolean.getBoolean("academy.mentalWorkRequireRecovery")) {
            var saved = data.entries().stream().filter(entry -> entry.source().equals(SOURCE) && !entry.returning()).toList();
            require(saved.size() == 64, "Process restart did not recover 64 saved assignments: " + saved.size());
            require(saved.stream().map(WorkOrderData.Entry::orderId).distinct().count() == 4, "Process restart changed saved group identity");
            System.out.println("[mental-work-smoke] process restart: 64 assignments / 4 stable groups recovered from disk");
        }
        for (var entry : data.entries()) data.remove(entry.key());
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(level, 28.5, 207, -2.5, Set.of(), 0, 15, false);
        org.academy.api.server.ability.AbilitySystemServer.getSystem(player).interruptActiveSkills(player);
        player.getAbilities().flying = true; player.onUpdateAbilities();
        for (int x = -2; x < 66; x++) for (int z = -2; z < 20; z++) {
            level.setBlock(new BlockPos(x, 203, z), Blocks.STONE.defaultBlockState(), 2);
        }
        for (int group = 0; group < 4; group++) {
            var first = new BlockPos(group * 16 + 8, 204, 8);
            var region = new BlockWorkRegion(level.dimension().identifier(), first, first.offset(7, 7, 7));
            REGIONS.add(region);
            for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
            var output = new BlockPos(group * 16 + 2, 204, 6);
            OUTPUTS.add(output);
            level.setBlock(output, Blocks.CHEST.defaultBlockState(), 2);
            var workers = new ArrayList<LivingEntity>();
            for (int i = 0; i < 16; i++) {
                var worker = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
                worker.setPos(group * 16 + 1.5 + i % 4, 204, 1.5 + i / 4);
                worker.setSilent(true); worker.setInvulnerable(true); worker.addTag("academy_work_smoke"); level.addFreshEntity(worker);
                MentalControlMemory.remember(player, worker);
                workers.add(worker); WORKERS.add(worker);
            }
            var settings = new WorkSettings(WorkSettings.Mode.MINING, true, true, false, false,
                    List.of("minecraft:dirt"), Optional.empty(), Optional.of(output), WorkSettings.MiningReach.ADAPTIVE);
            var result = GroupControlApi.dispatch(new GroupControlRequest(player, SOURCE, workers,
                    new GroupControlCommand.Work(region, settings), 1000));
            require(result.applied() == 16, "All workers must accept the order");
        }
        for (int i = 0; i < 4160; i++) {
            var mob = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            mob.setPos(-10 + i % 64 * 1.3, 205, 24 + i / 64 * 1.3);
            mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.setInvulnerable(true);
            mob.addTag("academy_work_smoke"); level.addFreshEntity(mob);
            BACKGROUND.add(mob);
        }
        ready = true;
        System.out.println("[mental-work-smoke] setup: 64 workers, 4 orders, 4160 stationary background cows");
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void beginServerTick(ServerTickEvent.Pre event) {
        if (Boolean.getBoolean("academy.mentalWorkSmoke")) tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void server(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.mentalWorkSmoke") || !ready || done || failure != null) return;
        try {
            age++;
            var metrics = WorkScheduling.budget(event.getServer()).metrics();
            for (var operation : WorkBudget.Operation.values()) {
                require(metrics.used()[operation.ordinal()] <= operation.limit(), "Exceeded " + operation + " budget");
            }
            if (age > 400) {
                TIMES.add(metrics.nanos()); TICK_TIMES.add(System.nanoTime() - tickStart);
                var counts = metrics.used();
                for (var op : WorkBudget.Operation.values()) OPERATIONS[op.ordinal()] += counts[op.ordinal()];
            }
            if (age % 200 == 0) {
                require(WORKERS.stream().allMatch(worker -> worker.isAlive() && !worker.isRemoved()), "Benchmark lost a worker");
                require(BACKGROUND.stream().allMatch(entity -> entity.isAlive() && !entity.isRemoved()), "Benchmark lost background entities");
                require(GroupControlRuntime.activeTasks() == 64 && GroupControlRuntime.activeWorkGroups() == 4,
                        "Benchmark workload changed before sampling finished");
                long started = System.nanoTime();
                for (var region : REGIONS) {
                    int restored = 0;
                    for (var pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                        if (owner.level().getBlockState(pos).isAir()) {
                            owner.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
                            if (++restored == 256) break;
                        }
                    }
                }
                for (int group = 0; group < OUTPUTS.size(); group++) if (owner.level().getBlockEntity(OUTPUTS.get(group)) instanceof Container container) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        int amount = container.getItem(slot).getCount();
                        harvested += amount; GROUP_OUTPUT[group] += amount; container.setItem(slot, ItemStack.EMPTY);
                    }
                    container.setChanged();
                }
                fixtureNanos += System.nanoTime() - started;
                System.out.println("[mental-work-smoke] tick=" + age + " output=" + harvested + " groups=" + GroupControlRuntime.activeWorkGroups());
            }
            if (age == 100) GroupControlApi.pauseWork(event.getServer(), owner.getUUID(), Set.of(WORKERS.getFirst().getUUID()), true);
            if (age == 110) require(MentalControlApi.hasAiTakeover(WORKERS.getFirst()), "Paused work lost AI takeover: alive="
                    + WORKERS.getFirst().isAlive() + " removed=" + WORKERS.getFirst().getRemovalReason()
                    + " tasks=" + GroupControlRuntime.activeTasks());
            if (age == 120) GroupControlApi.pauseWork(event.getServer(), owner.getUUID(), Set.of(WORKERS.getFirst().getUUID()), false);
            if (age == 250) {
                event.getServer().saveEverything(false, true, true);
                owner.level().getDataStorage().saveAndJoin();
                require(!WorkOrderData.get(event.getServer()).isDirty(), "Work orders remained dirty after disk save");
            }
            if (age == 300) GroupControlRuntime.clear();
            if (age == 350) {
                require(GroupControlRuntime.activeTasks() == 64, "Incremental recovery lost workers");
                require(GroupControlRuntime.activeWorkGroups() == 4, "Recovery split shared orders");
            }
            int duration = Integer.getInteger("academy.mentalWorkTicks", 6400);
            if (age >= duration) {
                require(harvested > 0, "Workers never delivered output");
                for (long amount : GROUP_OUTPUT) require(amount > 0, "A work group never delivered output");
                var values = new ArrayList<>(TIMES); values.sort(Long::compare);
                var ticks = new ArrayList<>(TICK_TIMES); ticks.sort(Long::compare);
                var report = String.format(Locale.ROOT,
                        "64 workers / 4 groups / 512 blocks per group / 4160 stationary no-AI background cows%n"
                        + "samples=%d p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms%noutput=%d fixtureMaintenance=%.3fms%n"
                        + "Pause, shared recovery, count budgets and explicit disk save passed.%n",
                        values.size(), percentile(values, 50), percentile(values, 95), percentile(values, 99),
                        values.getLast() / 1e6, harvested, fixtureNanos / 1e6);
                report += String.format(Locale.ROOT, "serverPrePost p95=%.3fms p99=%.3fms max=%.3fms%n",
                        percentile(ticks, 95), percentile(ticks, 99), ticks.getLast() / 1e6);
                report += "groupOutput=" + Arrays.toString(GROUP_OUTPUT) + "\noperations=" + Arrays.toString(OPERATIONS) + "\n";
                Files.createDirectories(output()); Files.writeString(output().resolve("report.txt"), report);
                GroupControlApi.cancelWork(event.getServer(), owner.getUUID(), WORKERS.stream().map(LivingEntity::getUUID).collect(java.util.stream.Collectors.toSet()));
                done = true;
            }
        } catch (Throwable problem) { failure = problem; problem.printStackTrace(); }
    }

    private static double percentile(List<Long> values, int percentile) { return values.get(Math.min(values.size() - 1, values.size() * percentile / 100)) / 1e6; }
    private static Path output() { return Path.of(System.getProperty("academy.mentalWorkOutput")); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
