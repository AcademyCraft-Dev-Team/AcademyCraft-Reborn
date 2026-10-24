package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in actual capture/drop-event/permission regression in a disposable integrated world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureSoftCaptureSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureSoftCaptureSmoke");
    private static final List<String> CASES = List.of("snow", "grass", "flower", "powder_snow", "dry_grass", "snow_block", "cancel_break",
            "permission", "chest", "waterlogged", "unbreakable");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static volatile boolean ready, complete, failed, closing;
    private static Path directory;
    private static ServerPlayer player;
    private static BlockPos seed, soft;
    private static StructureRef reference;
    private static String backend = "";
    private static int scenario, age, ticks, breaks, drops;
    private static boolean started, staged;

    private StructureSoftCaptureSmoke() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || complete || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            if (++age > 2500) throw new IllegalStateException("soft capture smoke deadline");
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
            }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO);
            if (!started) { prepare(); return; }
            if (++ticks > 400) throw new IllegalStateException("capture/lift deadline: " + CASES.get(scenario));
            var result = StructureCaptureExtractions.advance(player.level(), player, reference);
            check(!StructureCaptureExtractions.failed(result), "extraction failed: " + result);
            if (!StructureCaptureExtractions.arrived(result)) return;
            var handle = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow();
            var states = handle.world();
            check(states.read(BlockPos.ZERO).state().is(Blocks.GRASS_BLOCK), "grass support was not preserved");
            if (CASES.get(scenario).equals("snow_block")) {
                check(states.read(BlockPos.ZERO.above()).state().is(Blocks.SNOW_BLOCK), "full snow block lost");
                check(breaks == 0 && drops == 0, "solid snow must move without duplicate drops");
            } else {
                check(breaks > 0 && drops > 0, "soft cell bypassed break/drop events");
                check(player.level().getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(seed).inflate(6)).stream()
                        .anyMatch(item -> item.getItem().is(Items.DIAMOND)), "drop listener addition did not spawn");
            }
            var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
            double rise = motion.pose().worldOrigin().y - seed.getY();
            check(Math.abs(rise - 3) <= .1, "incorrect hover height " + rise);
            check(handle.lifecycle() == StructureLifecycle.ACTIVE, "body not active");
            finish(true, "rise=" + rise);
        } catch (Throwable error) {
            failed = true;
            AcademyCraft.LOGGER.error("STRUCTURE_SOFT_CAPTURE_FAILED case={}",
                    scenario < CASES.size() ? CASES.get(scenario) : "sable_payload_transfer", error);
            write(false, error.toString());
        }
    }

    private static void prepare() {
        String name = CASES.get(scenario);
        if (!staged) {
            seed = new BlockPos(-2160 + scenario * 24, player.level().getMaxY() - 48, -1480);
            soft = seed.above();
            for (var cell : BlockPos.betweenClosed(seed.offset(-6, -3, -6), seed.offset(6, 8, 6))) {
                player.level().getChunkAt(cell);
                check(player.level().getBlockState(cell).isAir(), "fixture occupied: " + cell);
            }
            player.connection.teleport(seed.getX() - 4, seed.getY() + 1, seed.getZ() + .5, -90, 0);
            player.level().setBlock(seed, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            var state = switch (name) {
                case "grass" -> Blocks.SHORT_GRASS.defaultBlockState();
                case "flower" -> Blocks.DANDELION.defaultBlockState();
                case "powder_snow" -> Blocks.POWDER_SNOW.defaultBlockState();
                case "dry_grass" -> Blocks.SHORT_DRY_GRASS.defaultBlockState();
                case "snow_block" -> Blocks.SNOW_BLOCK.defaultBlockState();
                case "chest" -> Blocks.CHEST.defaultBlockState();
                case "waterlogged" -> Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
                case "unbreakable" -> Blocks.BEDROCK.defaultBlockState();
                default -> Blocks.SNOW.defaultBlockState();
            };
            player.level().setBlock(soft, state, 2);
            breaks = 0; drops = 0; ticks = 0;
            staged = true;
            return;
        }
        // Let the player's new tracking tickets reach Sable before allocating the fixture body.
        // Capturing in the teleport tick tests unload/reload authority, not source admission.
        if (++ticks < 20) return;
        ticks = 0;
        var state = player.level().getBlockState(soft);
        var request = new StructureCaptureRequest(List.of(seed, soft), 256, 24,
                (host, position) -> !name.equals("permission") || !position.equals(soft), true)
                .withSkillCreation(player.getUUID(), AcademyCraft.academy("kinetic_throw"));
        var result = StructureCaptureExtractions.capture(player.level(), player, request, 3, 200);
        boolean reject = scenario >= 6;
        if (reject) {
            check(!result.succeeded() && !result.pending() && result.structure().isEmpty(), "unsafe capture accepted");
            check(player.level().getBlockState(seed).is(Blocks.GRASS_BLOCK)
                    && player.level().getBlockState(soft).equals(state), "rejected capture changed source");
            check(drops == 0, "rejected capture generated drops");
            finish(true, result.detail());
            return;
        }
        check(result.pending() || result.succeeded(), "capture rejected: " + result);
        reference = result.structure().orElseThrow(); backend = reference.backend().toString();
        if (!name.equals("snow_block")) check(player.level().getBlockState(soft).isAir(), "soft source was not cleared");
        started = true;
    }

    @SubscribeEvent public static void breaking(BreakBlockEvent event) {
        if (!ENABLED || player == null || event.getLevel() != player.level() || !event.getPos().equals(soft)) return;
        breaks++;
        if (CASES.get(scenario).equals("cancel_break")) event.setCanceled(true);
    }

    @SubscribeEvent public static void dropping(BlockDropsEvent event) {
        if (!ENABLED || player == null || event.getLevel() != player.level() || !event.getPos().equals(soft)) return;
        drops++;
        // Deterministic stand-in for a global drop-effect listener; verifies the actual spawn path.
        event.getDrops().add(new ItemEntity(player.level(), soft.getX() + .5, soft.getY() + .5,
                soft.getZ() + .5, new ItemStack(Items.DIAMOND)));
    }

    private static void finish(boolean passed, String detail) {
        RESULTS.add(Map.of("case", CASES.get(scenario), "passed", passed, "detail", detail,
                "break_events", breaks, "drop_events", drops));
        if (reference != null) {
            StructureCaptureExtractions.release(player, reference);
            check(StructureTransactions.discard(player.level().getServer(), reference), "fixture body cleanup failed");
            reference = null;
        }
        player.level().setBlock(soft, Blocks.AIR.defaultBlockState(), 2);
        player.level().setBlock(seed, Blocks.AIR.defaultBlockState(), 2);
        started = false; staged = false;
        if (++scenario == CASES.size()) {
            if (Boolean.getBoolean("academy.sablePayloadCapabilityProbe")) {
                RESULTS.add(org.academy.internal.common.structure.sable.validation.SablePayloadCapabilityProbe
                        .run(player.level(), seed.offset(0, 0, 8)));
            }
            complete = true; write(true, "");
            AcademyCraft.LOGGER.info("STRUCTURE_SOFT_CAPTURE_PASSED backend={} cases={}", backend, RESULTS);
        }
    }

    private static void write(boolean passed, String failure) {
        try { Files.writeString(directory.resolve("structure-soft-capture-result.json"), new GsonBuilder()
                .setPrettyPrinting().create().toJson(Map.of("passed", passed, "failure", failure,
                        "backend", backend, "cases", RESULTS))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Cannot write soft capture result", error); }
    }

    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (complete || failed) { closing = true; minecraft.stop(); return; }
        if (minecraft.player == null || minecraft.level == null) return;
        directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) {
            throw new IllegalStateException("Soft capture smoke requires an isolated fixture directory");
        }
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());
        ready = true;
    }

    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
