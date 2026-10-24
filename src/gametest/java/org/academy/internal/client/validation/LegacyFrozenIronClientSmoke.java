package org.academy.internal.client.validation;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructurePresentationStatus;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.client.structure.NativeStructureMiningClient;
import org.academy.internal.client.structure.NativeStructureRaycast;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureMiningSessions;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.mixin.client.MixinMinecraftNativeMiningAccess;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Actual input/network mining of the saved one-cell iron fixture, only inside a disposable copy.
 * Enable academy.legacyFrozenIronSmoke; the game directory must start with legacy-frozen-iron-smoke-.
 * academy.legacyFrozenIronTarget optionally overrides the saved target UUID. No menu is opened.
 * academy.legacyFrozenIronMotionOnly saves the restored/moved target and exits before mining.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class LegacyFrozenIronClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.legacyFrozenIronSmoke");
    private static final boolean MOTION_ONLY = Boolean.getBoolean("academy.legacyFrozenIronMotionOnly");
    private static final UUID TARGET = UUID.fromString(System.getProperty("academy.legacyFrozenIronTarget",
            "860aa2e6-7a95-4f90-ac1a-98cdc3ba5b00"));
    private static final double SAVED_ORIGIN_Y = Double.parseDouble(System.getProperty(
            "academy.legacyFrozenIronInitialY", "65.21254"));
    private static final Component MARKER = Component.literal("Legacy frozen iron mining fixture");
    private enum Phase { WAIT_RECOVERY, FALL, IMPULSE, PREPARE, MINING, VERIFY, MOTION_FINISHED, FINISHED }
    private static volatile Phase phase = Phase.WAIT_RECOVERY;
    private static volatile MinecraftServer owner;
    private static volatile UUID playerId;
    private static volatile StructureRef reference;
    private static volatile BlockPos localTarget;
    private static volatile String failure;
    private static volatile int pickSlot;
    private static volatile boolean started;
    private static boolean finished, attackStarted;
    private static int clientTicks, stableTicks;
    // Only the integrated server thread accesses the fixture, progress and output counters.
    private static NativeStructureRuntime runtime;
    private static StructureHandle handle;
    private static ServerLevel storage;
    private static int serverTicks, phaseTick, emittedIron;
    private static float maximumProgress;
    private static boolean intermediateStage;
    private static StructurePhysicsRegion region;
    private static UUID runtimeId, regionId;
    private static StructurePose firstObservedPose, beforeImpulsePose;
    private static Vec3 previousCenter, impulseStart;
    private static int stationaryTicks;
    private static double maximumFall, maximumImpulseDistance;
    private static boolean playerParked;

    private LegacyFrozenIronClientSmoke() {}

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        require(failure == null, "Legacy iron server probe failed: " + failure);
        require(++clientTicks < 1800, "Legacy iron client timeout at " + phase);
        if (!started) {
            owner = mc.getSingleplayerServer();
            require(owner != null, "Legacy iron probe requires an integrated server");
            verifyCopy(mc, owner);
            playerId = mc.player.getUUID();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            started = true;
            AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_SMOKE_START target={} motion_only={}", TARGET, MOTION_ONLY);
        }
        var input = (MixinMinecraftNativeMiningAccess) mc;
        if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        if (phase != Phase.MINING) {
            mc.options.keyAttack.setDown(false);
            input.academy$continueNativeMiningAttack(false);
            if (phase == Phase.MOTION_FINISHED) {
                if (++stableTicks < 20) return;
                var view = projection(mc);
                require(view != null && view.view().cells().size() == 1
                                && view.view().cells().getFirst().state().is(Blocks.IRON_BLOCK),
                        "Motion-only run must retain the visible iron block for the next load");
                AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_MOTION_SMOKE_PASSED reference={} "
                                + "startup_recovery=true fall={} impulse_distance={} checkpoint_saved=true "
                                + "iron_preserved=true", reference, maximumFall, maximumImpulseDistance);
                finished = true;
                mc.stop();
            } else if (phase == Phase.FINISHED) {
                if (++stableTicks < 20) return;
                require(projection(mc) == null, "Mined one-cell legacy projection remained visible");
                AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_SMOKE_PASSED reference={} actual_input=true "
                        + "startup_recovery=true fall={} impulse_distance={} survival=true progress=true "
                        + "iron_units=1 tool_wear=1 empty_projection=true", reference, maximumFall, maximumImpulseDistance);
                finished = true;
                mc.stop();
            }
            return;
        }
        if (!attackStarted) {
            mc.player.getInventory().setSelectedSlot(pickSlot);
            var picked = NativeStructureRaycast.pick(mc.player, mc.player.blockInteractionRange(), 1).orElse(null);
            if (clientTicks % 100 == 0) {
                AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_CLIENT_WAIT target={} picked={} position={} "
                                + "screen={} mouse_grabbed={}", reference,
                        picked == null ? null : picked.reference(), mc.player.position(),
                        mc.gui.screen(), mc.mouseHandler.isMouseGrabbed());
            }
            if (mc.gui.screen() != null || !mc.mouseHandler.isMouseGrabbed()
                    || !MARKER.equals(mc.player.getMainHandItem().get(DataComponents.CUSTOM_NAME))
                    || picked == null || !picked.reference().equals(reference)
                    || !picked.localPosition().equals(localTarget)) {
                stableTicks = 0;
                return;
            }
            if (++stableTicks < 8) return;
            mc.options.keyAttack.setDown(true);
            input.academy$startNativeMiningAttack();
            attackStarted = true;
        } else {
            mc.options.keyAttack.setDown(true);
            input.academy$continueNativeMiningAttack(true);
            if (clientTicks % 100 == 0) {
                AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_CLIENT_MINING active={} target={}",
                        NativeStructureMiningClient.isActive(), reference);
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !started || owner == null || event.getServer() != owner || failure != null) return;
        guarded(() -> {
            require(++serverTicks < 1750, "Legacy iron server timeout at " + phase);
            var player = player();
            if (phase == Phase.WAIT_RECOVERY || phase == Phase.FALL || phase == Phase.IMPULSE) {
                verifyRecoveredMotion(player);
                return;
            }
            if (phase == Phase.PREPARE) {
                prepareMining(player);
                return;
            }
            if (phase == Phase.MINING) {
                var session = NativeStructureMiningSessions.snapshot(player).orElse(null);
                if (session != null) {
                    require(session.reference().equals(reference) && session.localPosition().equals(localTarget),
                            "Actual input started mining a different structure cell");
                    maximumProgress = Math.max(maximumProgress, session.progress());
                    intermediateStage |= session.stage() > 0 && session.stage() < 9;
                }
                if (!storage.getBlockState(localTarget).isAir()) return;
                require(maximumProgress >= 0.1F && intermediateStage,
                        "Legacy iron removal skipped authoritative mining progress");
                phaseTick = serverTicks;
                phase = Phase.VERIFY;
                AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_REMOVED reference={} progress={} emitted_units={}",
                        reference, maximumProgress, emittedIron);
            } else if (phase == Phase.VERIFY && serverTicks - phaseTick >= 20) {
                require(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                        "Fixture left survival mode during mining");
                var tool = player.getInventory().getItem(pickSlot);
                require(tool.is(Items.IRON_PICKAXE) && tool.getDamageValue() == 1,
                        "One iron break must consume exactly one iron pickaxe durability");
                require(emittedIron == 1 && countMarkedIron(player) == 1,
                        "One legacy iron cell must deliver exactly one iron block");
                require(runtime.presentationStatus(reference) == StructurePresentationStatus.EMPTY,
                        "Removing the last cell must retire its projection");
                require(runtime.resolve(reference).orElseThrow().lifecycle() == StructureLifecycle.ACTIVE,
                        "Mining must preserve the stored structure identity");
                require(StructurePhysics.savedRegions(player.level()).stream().allMatch(saved -> saved.resumeAllowed()),
                        "A neighboring legacy region failed while mining the recovered iron");
                phase = Phase.FINISHED;
            }
        });
    }

    private static void verifyRecoveredMotion(ServerPlayer player) {
        if (phase == Phase.WAIT_RECOVERY) {
            require(serverTicks <= 240, "Startup did not restore the saved legacy physics attachment");
            runtime = NativeStructureRuntime.backend(owner);
            reference = runtime.loadedReferences().stream().filter(ref -> ref.id().equals(TARGET)).findFirst().orElse(null);
            if (reference == null) return;
            handle = runtime.resolve(reference).orElse(null);
            if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) return;
            if (!playerParked) {
                parkPlayer(player);
                playerParked = true;
            }
            region = StructurePhysics.activeRegion(player.level(), reference).orElse(null);
            if (region == null) return;
            var motion = region.motion(reference).orElse(null);
            if (motion == null) return;
            runtimeId = handle.runtimeId();
            regionId = region.id();
            firstObservedPose = motion.pose();
            previousCenter = center(motion);
            stationaryTicks = 0;
            phaseTick = serverTicks;
            phase = Phase.FALL;
            AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_RECOVERED reference={} region={} saved_y={} observed_pose={}",
                    reference, regionId, SAVED_ORIGIN_Y, firstObservedPose);
        }
        var motion = verifiedMotion(player);
        var center = center(motion);
        double step = previousCenter.distanceTo(center);
        stationaryTicks = step < 0.03 && motion.linearVelocity().length() < 0.15
                && motion.angularVelocity().length() < 0.15 ? stationaryTicks + 1 : 0;
        previousCenter = center;
        if (phase == Phase.FALL) {
            maximumFall = Math.max(maximumFall, SAVED_ORIGIN_Y - motion.pose().worldOrigin().y);
            require(serverTicks - phaseTick <= 120,
                    "Restored iron did not naturally fall at least one block and settle within 120 ticks");
            if (maximumFall < 1 || stationaryTicks < 20) return;
            beforeImpulsePose = motion.pose();
            impulseStart = center;
            var impulse = new Vec3(motion.mass() * 2, 0, 0);
            region.applyImpulse(reference, impulse, center);
            require(!region.hasActiveControl(), "A one-shot impulse unexpectedly installed persistent control");
            phaseTick = serverTicks;
            stationaryTicks = 0;
            phase = Phase.IMPULSE;
            AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_FALL_PASSED reference={} fall={} settled_pose={} "
                            + "impulse={} center={} mass={}", reference, maximumFall, beforeImpulsePose,
                    impulse, center, motion.mass());
        } else if (phase == Phase.IMPULSE) {
            maximumImpulseDistance = Math.max(maximumImpulseDistance,
                    Math.hypot(center.x - impulseStart.x, center.z - impulseStart.z));
            require(serverTicks - phaseTick <= 200, "Recovered iron impulse did not move and settle within 200 ticks");
            if (maximumImpulseDistance <= 0.15 || stationaryTicks < 20) return;
            require(!motion.pose().equals(beforeImpulsePose), "Impulse left the published pose unchanged");
            AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_IMPULSE_PASSED reference={} region={} distance={} "
                    + "final_pose={}", reference, regionId, maximumImpulseDistance, motion.pose());
            if (MOTION_ONLY) {
                require(StructurePhysics.savedRegions(player.level()).stream().allMatch(saved -> saved.resumeAllowed()),
                        "A neighboring legacy region failed again after startup recovery");
                require(runtime.save(reference), "Motion-only fixture could not save its restored physics checkpoint");
                AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_CHECKPOINT_SAVED reference={} region={} pose={}",
                        reference, regionId, handle.pose());
                phase = Phase.MOTION_FINISHED;
            } else {
                phase = Phase.PREPARE;
            }
        }
    }

    private static StructureMotionState verifiedMotion(ServerPlayer player) {
        require(StructurePhysics.activeRegion(player.level(), reference).orElse(null) == region
                        && region.status() == StructurePhysicsRegion.Status.READY && region.id().equals(regionId),
                "Recovered target lost its ready physics region");
        require(!region.hasActiveControl(), "Saved control unexpectedly survived startup recovery");
        var current = runtime.resolve(reference).orElseThrow();
        require(current == handle && current.runtimeId().equals(runtimeId)
                        && current.reference().equals(reference) && current.lifecycle() == StructureLifecycle.ACTIVE,
                "Recovered target identity changed during motion");
        require(runtime.presentedCellCount(reference) == 1, "Motion changed the saved one-cell content");
        var motion = region.motion(reference).orElseThrow(() -> new IllegalStateException("Recovered body lost its motion"));
        require(motion.pose().equals(handle.pose()), "Published structure and physics poses disagree");
        return motion;
    }

    private static Vec3 center(StructureMotionState motion) {
        return motion.pose().localToWorldPoint(motion.localCenterOfMass());
    }

    private static void parkPlayer(ServerPlayer player) {
        var origin = handle.pose().worldOrigin();
        for (var offset : List.of(new Vec3(5, 4, 5), new Vec3(-5, 4, 5),
                new Vec3(5, 4, -5), new Vec3(-5, 4, -5), new Vec3(0, 8, 0))) {
            var feet = origin.add(offset);
            if (!player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) continue;
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(feet.x, feet.y, feet.z, 0, 0);
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        throw new IllegalStateException("No empty player position outside the saved iron fall path");
    }

    /** Mining setup can run after a separate recovery/movement phase in the same disposable copy. */
    private static void prepareMining(ServerPlayer player) {
        runtime = NativeStructureRuntime.backend(owner);
        if (reference == null) {
            reference = runtime.loadedReferences().stream().filter(ref -> ref.id().equals(TARGET)).findFirst().orElse(null);
        }
        if (reference == null) return;
        handle = runtime.resolve(reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) return;
        NativeStructureProjectionEntity view = null;
        for (var entity : player.level().getAllEntities()) {
            if (entity instanceof NativeStructureProjectionEntity projection
                    && !projection.isRemoved() && reference.equals(projection.viewState().reference())) {
                view = projection;
                break;
            }
        }
        if (view == null || view.view().isEmpty()) return;
        require(view.view().cells().size() == 1 && view.view().cells().getFirst().state().is(Blocks.IRON_BLOCK),
                "The selected saved fixture must contain exactly one iron block");
        localTarget = view.view().cells().getFirst().position();
        storage = runtime.storageLevel(reference).orElseThrow();
        require(storage.getBlockState(localTarget).is(Blocks.IRON_BLOCK) && storage.getBlockEntity(localTarget) == null,
                "The saved target must be actual ordinary iron in native storage");
        require(player.getOffhandItem().isEmpty(), "The disposable fixture requires an empty offhand");
        pickSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                pickSlot = slot;
                break;
            }
        }
        if (pickSlot < 0) pickSlot = freeSelectedHotbarSlot(player);
        var pick = new ItemStack(Items.IRON_PICKAXE);
        pick.set(DataComponents.CUSTOM_NAME, MARKER);
        player.getInventory().setItem(pickSlot, pick);
        player.getInventory().setSelectedSlot(pickSlot);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        require(aimAtSavedIron(player), "No visible unoccupied mining viewpoint for the saved iron");
        player.connection.send(player.getInventory().createInventoryUpdatePacket(pickSlot));
        player.inventoryMenu.broadcastChanges();
        require(countMarkedIron(player) == 0, "Disposable fixture already contains marked test output");
        AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_MINING_READY reference={} local={} pose={} eye={}",
                reference, localTarget, handle.pose(), player.getEyePosition());
        phase = Phase.MINING;
    }

    private static int freeSelectedHotbarSlot(ServerPlayer player) {
        var inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        // Only main-inventory slots are candidates; never replace equipment or the offhand.
        for (int destination = 9; destination < Math.min(36, inventory.getContainerSize()); destination++) {
            if (!inventory.getItem(destination).isEmpty()) continue;
            var displaced = inventory.getItem(selected);
            var before = displaced.copy();
            inventory.setItem(destination, displaced);
            inventory.setItem(selected, ItemStack.EMPTY);
            require(inventory.getItem(selected).isEmpty() && ItemStack.matches(before, inventory.getItem(destination)),
                    "Moving the selected item into the backpack changed its count or components");
            player.connection.send(inventory.createInventoryUpdatePacket(destination));
            AcademyCraft.LOGGER.info("LEGACY_FROZEN_IRON_HOTBAR_PRESERVED source={} destination={} item={} count={}",
                    selected, destination, before.getItem(), before.getCount());
            return selected;
        }
        throw new IllegalStateException("Legacy iron fixture needs a free backpack slot to preserve the full hotbar");
    }

    private static boolean aimAtSavedIron(ServerPlayer player) {
        var center = handle.pose().localToWorldPoint(Vec3.atCenterOf(localTarget));
        for (var offset : List.of(new Vec3(0, 0.7, 2.6), new Vec3(0, 0.7, -2.6),
                new Vec3(2.6, 0.7, 0), new Vec3(-2.6, 0.7, 0), new Vec3(0, 2.8, 0))) {
            var eye = center.add(offset);
            var feet = eye.add(0, -player.getEyeHeight(), 0);
            if (!player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) continue;
            var direction = center.subtract(eye);
            var end = eye.add(direction.normalize().scale(player.blockInteractionRange()));
            var hit = StructureQueries.raycast(player.level(), eye, end).orElse(null);
            if (hit == null || !hit.block().structure().equals(reference)
                    || !hit.block().localPosition().equals(localTarget)
                    || !StructureQueries.validate(player.level(), eye, end, hit).succeeded()) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
            player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
            player.setYHeadRot(yaw);
            player.setDeltaMovement(Vec3.ZERO);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void output(EntityJoinLevelEvent event) {
        if (!ENABLED || owner == null || !owner.isSameThread() || reference == null || failure != null) return;
        var context = StructureContexts.current().orElse(null);
        if (context == null || !context.reference().equals(reference)
                || !(event.getEntity() instanceof ItemEntity item) || !item.getItem().is(Items.IRON_BLOCK)) return;
        require(event.getLevel() == player().level(), "Legacy iron output escaped into the storage dimension");
        emittedIron += item.getItem().getCount();
        item.getItem().set(DataComponents.CUSTOM_NAME, MARKER);
    }

    private static int countMarkedIron(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(Items.IRON_BLOCK) && MARKER.equals(stack.get(DataComponents.CUSTOM_NAME))) count += stack.getCount();
        }
        for (var level : List.of(player.level(), storage)) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity item && !item.isRemoved() && item.getItem().is(Items.IRON_BLOCK)
                        && MARKER.equals(item.getItem().get(DataComponents.CUSTOM_NAME))) count += item.getItem().getCount();
            }
        }
        return count;
    }

    private static NativeStructureProjectionEntity projection(Minecraft mc) {
        if (reference == null) return null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity view && reference.equals(view.viewState().reference())) return view;
        }
        return null;
    }

    private static ServerPlayer player() {
        var player = owner.getPlayerList().getPlayer(playerId);
        require(player != null, "Legacy iron fixture player disappeared");
        return player;
    }

    private static void verifyCopy(Minecraft mc, MinecraftServer server) {
        try {
            var directory = mc.gameDirectory.toPath().toRealPath();
            var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(directory.getFileName().toString().startsWith("legacy-frozen-iron-smoke-")
                            && world.startsWith(directory.resolve("saves").toRealPath()),
                    "Legacy iron probe only accepts a world copied into its dedicated disposable game directory");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify the disposable legacy iron world", error);
        }
    }

    private static void guarded(Runnable action) {
        if (failure != null) return;
        try {
            action.run();
        } catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.LOGGER.error("LEGACY_FROZEN_IRON_SMOKE_FAILED", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
