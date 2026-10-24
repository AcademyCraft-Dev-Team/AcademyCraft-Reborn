package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.*;
import org.academy.internal.common.structure.upgrade.lifecycle.*;

import java.nio.file.*;
import java.util.*;

/** Actual payload capture, lift, live inventory edit and automatic restoration on either backend. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructurePayloadSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structurePayloadSmoke");
    private static final List<String> CASES = List.of("chest", "barrel", "double_trapped_chest", "waterlogged", "unopened_loot", "placement_cancel_retry");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final Map<BlockPos, StructureBlockData> EXPECTED = new LinkedHashMap<>(), TARGETS = new LinkedHashMap<>();
    private static volatile boolean ready, complete, failed, closing;
    private static Path directory;
    private static ServerPlayer player;
    private static BlockPos seed;
    private static StructureRef reference;
    private static int scenario, phase, ticks, age, cancelled;
    private static List<BlockPos> positions;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 9000, "payload fixture deadline");
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst(); player.setGameMode(GameType.SURVIVAL);
                org.academy.internal.common.world.damagesource.DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
            }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            ticks++;
            if (phase == 0) { prepare(); phase = 1; ticks = 0; return; }
            if (phase == 1) {
                if (ticks < 25) return;
                var request = new StructureCaptureRequest(positions, 16, 8, StructureCaptureRequest.ALLOW, true)
                        .withSkillCreation(scenario == 1 ? UUID.randomUUID() : player.getUUID(), AcademyCraft.academy("kinetic_throw"));
                var capture = StructureCaptureExtractions.capture(player.level(), player, request, 3, 200);
                check(capture.succeeded() || capture.pending(), "payload capture rejected: " + capture);
                reference = capture.structure().orElseThrow(); phase = 2; ticks = 0; return;
            }
            if (phase == 2) {
                check(ticks < 500, "payload lift timeout");
                var progress = StructureCaptureExtractions.advance(player.level(), player, reference);
                check(!StructureCaptureExtractions.failed(progress), "payload extraction failed: " + progress);
                if (!StructureCaptureExtractions.arrived(progress)) return;
                var world = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().world();
                for (var cell : EXPECTED.entrySet()) {
                    check(StructureCellPayload.normalize(world.read(cell.getKey())).equals(cell.getValue()), "captured payload differs at " + cell.getKey());
                    check(player.level().getBlockState(seed.offset(cell.getKey())).isAir(), "source survived capture");
                }
                if (scenario != 4) {
                    var local = EXPECTED.keySet().stream().filter(pos -> EXPECTED.get(pos).blockEntityData() != null).findFirst().orElse(null);
                    if (local != null) {
                        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) verifyNativeMenu(local);
                        var stack = new ItemStack(Items.DIAMOND, 37); stack.set(DataComponents.CUSTOM_NAME, Component.literal("after capture"));
                        ((Container) liveEntity(local)).setItem(0, stack);
                        EXPECTED.put(local, StructureCellPayload.normalize(world.read(local)));
                    }
                }
                check(!StructureTransactions.discard(player.level().getServer(), reference), "payload discarded after failed adoption");
                StructureCaptureExtractions.release(player, reference); phase = 3; ticks = 0; return;
            }
            if (LiveStructureProvenance.metadata(player.level().getServer(), reference).isPresent()) {
                if (ticks % 100 == 0) AcademyCraft.LOGGER.info("PAYLOAD_REST_WAIT {} {}", CASES.get(scenario), SkillStructureRestoration.detail(player.level().getServer(), reference));
                check(ticks < 1200, "automatic payload restoration timeout"); return;
            }
            check(!TARGETS.isEmpty(), "automatic restoration event missing");
            for (var cell : TARGETS.entrySet()) check(StructureCellPayload.read(player.level(), cell.getKey()).equals(cell.getValue()), "restored payload differs at " + cell.getKey());
            check(LiveStructureProvenance.metadata(player.level().getServer(), reference).isEmpty(), "restoration mark retained");
            check(player.level().getEntitiesOfClass(ItemEntity.class, new AABB(seed).inflate(12)).isEmpty(), "capture/restoration duplicated drops");
            if (scenario == 5) check(cancelled == 1, "placement rollback not exercised");
            RESULTS.add(Map.of("case", CASES.get(scenario), "backend", reference.backend().toString(), "cells", EXPECTED.size(),
                    "automatic_restoration", true, "inventory_after_live_edit", scenario != 4, "drops", 0, "passed", true));
            reference = null;
            if (++scenario == CASES.size()) { complete = true; write(""); }
            else { phase = 0; ticks = 0; }
        } catch (Throwable error) { failed = true; AcademyCraft.LOGGER.error("PAYLOAD_SMOKE_FAILED scenario={} phase={}", scenario, phase, error); write(error.toString()); }
    }

    private static void verifyNativeMenu(BlockPos local) {
        var entity = liveEntity(local);
        var id = new org.academy.internal.common.sublevel.SubLevelId(reference.id());
        var hit = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().pose().localToWorldPoint(Vec3.atCenterOf(local));
        BlockEntity partner = null;
        if (entity.getBlockState().getBlock() instanceof ChestBlock && entity.getBlockState().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            partner = liveEntity(ChestBlock.getConnectedBlockPos(local, entity.getBlockState()));
        }
        var lockOwner = UUID.randomUUID();
        if (partner != null) {
            PayloadIsolation.lock((net.minecraft.server.level.ServerLevel) partner.getLevel(), List.of(partner.getBlockPos()), lockOwner);
            check(org.academy.internal.common.sublevel.SubLevelInteractionService.useBlock(player.level().getServer(), id,
                    local, Direction.UP, hit, player, net.minecraft.world.InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.FAIL,
                    "double chest opened through its locked other half");
            PayloadIsolation.release(player.level().getServer(), lockOwner);
        }
        check(org.academy.internal.common.sublevel.SubLevelInteractionService.useBlock(player.level().getServer(), id,
                local, Direction.UP, hit, player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(), "native menu did not open");
        check(player.containerMenu != player.inventoryMenu && player.containerMenu.stillValid(player), "native menu is invalid");
        check(player.containerMenu.getSlot(0).getItem().getCount() == 19, "native menu lost inventory");
        var guarded = partner == null ? entity : partner;
        PayloadIsolation.lock((net.minecraft.server.level.ServerLevel) guarded.getLevel(), List.of(guarded.getBlockPos()), lockOwner);
        check(!player.containerMenu.stillValid(player), "pending container retained a usable menu");
        player.closeContainer();
        PayloadIsolation.release(player.level().getServer(), lockOwner);
    }

    private static BlockEntity liveEntity(BlockPos local) {
        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) {
            var runtime = org.academy.internal.common.sublevel.SubLevelServer.runtime(player.level()).orElseThrow();
            var body = runtime.get(new org.academy.internal.common.sublevel.SubLevelId(reference.id())).orElseThrow();
            return runtime.storage().blockEntity(body.plot(), local);
        }
        return SableFixture.entity(local);
    }
    private static final class SableFixture {
        static BlockEntity entity(BlockPos local) {
            var body = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.level()).getSubLevel(reference.id());
            return player.level().getBlockEntity(body.getPlot().getCenterBlock().offset(local));
        }
    }

    private static void prepare() {
        seed = new BlockPos(-2304 + scenario * 32, player.level().getMaxY() - 48, -1536);
        for (var pos : BlockPos.betweenClosed(seed.offset(-6,-1,-6), seed.offset(7,8,6))) {
            player.level().getChunkAt(pos); check(player.level().getBlockState(pos).isAir(), "fixture area occupied");
        }
        for (var pos : BlockPos.betweenClosed(seed.offset(-5,-1,-5), seed.offset(6,-1,5))) player.level().setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), 2);
        player.connection.teleport(seed.getX()-4, seed.getY()+2, seed.getZ()-4, 0,0);
        var cells = new ArrayList<BlockPos>(); cells.add(seed);
        var state = scenario == 1 ? Blocks.BARREL.defaultBlockState() : Blocks.CHEST.defaultBlockState();
        if (scenario == 2) state = Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        if (scenario == 3) state = state.setValue(BlockStateProperties.WATERLOGGED, true);
        player.level().setBlock(seed, state, 2);
        if (scenario == 2) {
            var other = seed.relative(ChestBlock.getConnectedDirection(state)); cells.add(other);
            player.level().setBlock(other, state.setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
            check(!StructureCellPayload.completePairs(player.level(), List.of(seed)), "half double chest accepted");
            var refused = StructureTransactions.capture(player.level(), new StructureCaptureRequest(List.of(seed),16,8,StructureCaptureRequest.ALLOW,true));
            check(!refused.succeeded() && !refused.pending(), "partial double chest transaction accepted");
        }
        if (scenario == 3) {
            cells.add(seed.east()); cells.add(seed.east(2));
            player.level().setBlock(seed.east(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true), 2);
            player.level().setBlock(seed.east(2), Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true), 2);
        }
        for (var pos : cells) if (player.level().getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
            if (scenario == 4) { container.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    net.minecraft.resources.Identifier.withDefaultNamespace("chests/simple_dungeon"))); container.setLootTableSeed(981234); }
            else { var stack = new ItemStack(Items.DIAMOND, 19); stack.set(DataComponents.CUSTOM_NAME, Component.literal("before capture")); container.setItem(0, stack); }
            container.setChanged();
        }
        // Barrier exercises both direct inventory/capability-style access and world destruction.
        var image = StructureCellPayload.read(player.level(), seed); var owner = UUID.randomUUID();
        PayloadIsolation.lock(player.level(), List.of(seed), owner);
        var container = (Container) player.level().getBlockEntity(seed);
        check(container.getItem(0).isEmpty() && !container.canPlaceItem(1, new ItemStack(Items.DIRT)), "pending inventory accessible");
        check(!player.level().destroyBlock(seed,true), "pending image destroyed");
        check(StructureCellPayload.read(player.level(), seed).equals(image), "barrier access changed payload or generated loot");
        PayloadIsolation.release(player.level().getServer(), owner);
        positions = List.copyOf(cells); EXPECTED.clear(); TARGETS.clear();
        // Use the exact minimum corner, including the connected chest's possibly negative offset.
        seed = new BlockPos(cells.stream().mapToInt(BlockPos::getX).min().orElseThrow(), seed.getY(), cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        for (var pos : cells) EXPECTED.put(pos.subtract(seed), StructureCellPayload.read(player.level(), pos));
    }

    @SubscribeEvent public static void restoration(StructureRestorationEvent event) {
        if (!ENABLED || reference == null || !event.reference().id().equals(reference.id())) return;
        reference = event.reference();
        var body = StructureBackendRouter.orientationAccess(event.host(), reference).orElseThrow().observe(event.host(), reference).orElseThrow();
        var alignment = RestorationGrid.align(body.motion().pose(), List.copyOf(EXPECTED.keySet()));
        TARGETS.clear();
        for (var placement : event.placements()) {
            var data = EXPECTED.get(placement.localPosition());
            TARGETS.put(placement.targetPosition(), new StructureBlockData(data.state().rotate(alignment.rotation()), data.blockEntityData()));
        }
    }
    @SubscribeEvent public static void placement(BlockEvent.EntityMultiPlaceEvent event) {
        if (ENABLED && scenario == 5 && phase == 3 && cancelled == 0 && event.getLevel() == player.level()) { cancelled++; event.setCanceled(true); }
    }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("structure-payload-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("passed", !failed, "failure", failure, "cases", RESULTS, "server_ticks", age))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Payload report failed",error); }
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (complete || failed) { closing = true; minecraft.stop(); return; }
        if (minecraft.player == null || minecraft.level == null) return;
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"), "isolated world required");
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());
        ready = true;
    }
    private static void check(boolean condition, String detail) { if (!condition) throw new IllegalStateException(detail); }
}
