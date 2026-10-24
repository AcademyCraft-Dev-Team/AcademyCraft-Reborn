package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.client.sublevel.SubLevelMiningClient;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelRuntime;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.mixin.client.MixinMinecraftNativeMiningAccess;

import java.util.List;
import java.util.UUID;

/** Actual Minecraft input/network regression, restricted to a disposable game directory. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelMiningInteractionSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelMiningSmoke");
    private static final String MODE = System.getProperty("academy.sublevelMiningMode", "pickaxe");
    private static final boolean CREATIVE = MODE.equals("creative_seeds");
    private static final boolean SEEDS = CREATIVE || MODE.equals("survival_seeds");
    private static final Component MARKER = Component.literal("SubLevel global drops mining probe");
    private static volatile MinecraftServer server;
    private static volatile UUID playerId;
    private static volatile SubLevelId id;
    private static volatile Vec3 target;
    private static volatile String failure;
    private static volatile boolean ready, completed;
    private static boolean started, swing, cracks, finished;
    private static int ticks, settleTicks, finishTicks;
    private static int globalEvents, ironUnits;
    private static BlockPos plotPosition;
    private static SubLevelRuntime runtime;

    private SubLevelMiningInteractionSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (server == null) {
            require(minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                            .startsWith("sublevel-mining-smoke-"),
                    "Probe requires an isolated sublevel-mining-smoke-* game directory");
            server = minecraft.getSingleplayerServer();
            require(server != null, "Probe requires an integrated server");
            playerId = minecraft.player.getUUID();
            server.execute(SubLevelMiningInteractionSmoke::prepare);
        }
        if (failure != null) throw new IllegalStateException(failure);
        require(++ticks < 1600, "SubLevel mining input timeout");
        if (!ready) return;
        minecraft.gui.setScreen(null);
        minecraft.mouseHandler.grabMouse();
        minecraft.player.getAbilities().flying = true;
        minecraft.player.getInventory().setSelectedSlot(0);
        if (completed) {
            minecraft.options.keyAttack.setDown(false);
            ((MixinMinecraftNativeMiningAccess) minecraft).academy$continueNativeMiningAttack(false);
            if (++finishTicks < 20) return;
            require(swing && (CREATIVE || cracks),
                    "Actual input must produce arm swings and survival server-authored cracks");
            AcademyCraft.LOGGER.info("SUBLEVEL_MINING_INTERACTION_SMOKE_PASSED structure={} "
                    + "mode={} arm_swing={} cracks={} global_drop_events={} drop_units={}",
                    id, MODE, swing, cracks, globalEvents, ironUnits);
            finished = true;
            minecraft.stop();
            return;
        }
        if (target == null || !MARKER.equals(minecraft.player.getMainHandItem().get(DataComponents.CUSTOM_NAME))) return;
        var direction = target.subtract(minecraft.player.getEyePosition());
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        minecraft.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
        var eye = minecraft.player.getEyePosition();
        var hit = SubLevelClientState.raycast(minecraft.level, eye,
                eye.add(minecraft.player.getViewVector(1).scale(minecraft.player.blockInteractionRange())));
        if (hit == null || !id.equals(hit.id())) return;
        if (++settleTicks < 30) return;
        minecraft.options.keyAttack.setDown(true);
        var input = (MixinMinecraftNativeMiningAccess) minecraft;
        if (!started) {
            input.academy$startNativeMiningAttack();
            started = true;
        } else input.academy$continueNativeMiningAttack(true);
        swing |= minecraft.player.swinging;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity projection
                    && id.equals(SubLevelClientState.subLevelOf(projection))) {
                cracks |= SubLevelMiningClient.cracks(id, projection.view()).values().stream()
                        .anyMatch(stage -> stage > 0 && stage < 9);
            }
        }
    }

    private static void prepare() {
        try {
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null, "Missing local player");
            var host = player.level();
            var source = new BlockPos(player.blockPosition().getX(), Math.min(host.getMaxY() - 10,
                    player.blockPosition().getY() + 25), player.blockPosition().getZ() + 8);
            for (var cell : BlockPos.betweenClosed(source.offset(-4, -2, -5), source.offset(4, 4, 4))) {
                host.getChunkAt(cell);
                host.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
            }
            for (var cell : BlockPos.betweenClosed(source.offset(-3, -1, -3), source.offset(3, -1, 3))) {
                host.setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
            }
            host.setBlock(source, SEEDS && !CREATIVE ? Blocks.DIRT.defaultBlockState()
                    : Blocks.IRON_BLOCK.defaultBlockState(), 2);
            var result = StructureTransactions.capture(host, new StructureCaptureRequest(
                    List.of(source), 1, 1, StructureCaptureRequest.ALLOW, true));
            require(result.succeeded(), "Iron capture failed: " + result);
            id = new SubLevelId(result.structure().orElseThrow().id());
            runtime = SubLevelServer.runtime(host).orElseThrow();
            var subLevel = runtime.get(id).orElseThrow();
            plotPosition = runtime.storage().worldPosition(subLevel.plot(), BlockPos.ZERO);
            var pick = new ItemStack(SEEDS ? Items.WHEAT_SEEDS : Items.IRON_PICKAXE);
            pick.set(DataComponents.CUSTOM_NAME, MARKER);
            player.getInventory().setItem(0, pick);
            player.getInventory().setSelectedSlot(0);
            player.setGameMode(CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            target = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(BlockPos.ZERO));
            var eye = target.add(0, 1.4, -2.8);
            var feet = eye.add(0, -player.getEyeHeight(), 0);
            player.connection.teleport(feet.x, feet.y, feet.z, 0, 0);
            player.connection.send(player.getInventory().createInventoryUpdatePacket(0));
            player.inventoryMenu.broadcastChanges();
            ready = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_MINING_INTERACTION_SMOKE_READY id={} plot={}", id, plotPosition);
        } catch (Throwable problem) {
            failure = problem.toString();
            AcademyCraft.LOGGER.error("SubLevel mining fixture failed", problem);
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || completed || failure != null || event.getServer() != server) return;
        try {
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null, "Mining player disconnected");
            var subLevel = runtime.get(id).orElseThrow();
            target = subLevel.pose().localToWorldPoint(Vec3.atCenterOf(BlockPos.ZERO));
            if (!runtime.storage().blockState(subLevel.plot(), BlockPos.ZERO).isAir()) return;
            int expectedDrops = CREATIVE ? 0 : 1;
            require(globalEvents == expectedDrops && ironUnits == expectedDrops, "Missing or duplicated global event/drop: "
                    + globalEvents + "/" + ironUnits);
            require(SEEDS ? player.getMainHandItem().is(Items.WHEAT_SEEDS)
                            && !player.getMainHandItem().has(DataComponents.DAMAGE)
                            && player.getMainHandItem().getCount() == 1
                            : player.getMainHandItem().getDamageValue() == 1,
                    "Held item was modified incorrectly");
            completed = true;
        } catch (Throwable problem) {
            failure = problem.toString();
            AcademyCraft.LOGGER.error("SubLevel mining verification failed", problem);
        }
    }

    @SubscribeEvent
    public static void blockDrops(BlockDropsEvent event) {
        if (!ENABLED || !ready || completed || event.getLevel() != runtime.storage().level()
                || !event.getPos().equals(plotPosition)) return;
        globalEvents++;
        require(event.getState().is(SEEDS ? Blocks.DIRT : Blocks.IRON_BLOCK) && event.getBreaker() != null
                        && event.getBreaker().getUUID().equals(playerId)
                        && event.getTool().is(SEEDS ? Items.WHEAT_SEEDS : Items.IRON_PICKAXE),
                "Global event lost its actual state/player/tool");
        require(StructureContexts.current().isPresent(), "Global event lost the structure coordinate context");
    }

    @SubscribeEvent
    public static void entitySpawn(EntityJoinLevelEvent event) {
        if (!ENABLED || !ready || completed || event.getLevel() != runtime.host()
                || !(event.getEntity() instanceof ItemEntity item)
                || !item.getItem().is(SEEDS ? Items.DIRT : Items.IRON_BLOCK)
                || item.position().distanceToSqr(target) > 16) return;
        ironUnits += item.getItem().getCount();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
