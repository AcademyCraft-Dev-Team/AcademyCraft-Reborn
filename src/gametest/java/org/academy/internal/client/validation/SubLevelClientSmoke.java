package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.academy.api.common.ability.program.ProgramBlockPosition;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.ability.program.ProgramStructurePose;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramStrength;
import org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.aeromanip.movement.HighSpeedJetStructureService;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.common.network.SubLevelInteractPacket;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.sublevel.SubLevelStructureHandle;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureWriteResult;
import net.minecraft.nbt.CompoundTag;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelSkillSupport;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Real integrated-client visual probe for a captured SubLevel. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelClientSmoke {
    /** Physics assertions advance with server time, even while client rendering is slow. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void controlMatrixTick(ServerTickEvent.Post event) {
        if (!ENABLED || failed || !controlMatrixRequested || matrixComplete()) return;
        try {
            if (!SubLevelControlSmoke.complete()) {
                SubLevelControlSmoke.tick(event.getServer());
                return;
            }
            if (!contentProbed) {
                SubLevelContentSmoke.run(event.getServer());
                SubLevelTractionSmoke.run(event.getServer());
                contentProbed = true;
            }
            if (!SubLevelDirectSkillSmoke.complete()) {
                SubLevelDirectSkillSmoke.tick(event.getServer());
                return;
            }
            if (Boolean.getBoolean("academy.sublevel.defaultPathProbe")) {
                if (!multiHostProbed) {
                    SubLevelTerrainMutationSmoke.run(event.getServer());
                    SubLevelCaptureTransactionSmoke.run(event.getServer());
                    SubLevelMultiHostSmoke.run(event.getServer());
                    multiHostProbed = true;
                }
                SubLevelProgramGraphSmoke.start(event.getServer());
                SubLevelProgramGraphSmoke.tick(event.getServer());
            }
        } catch (Throwable error) {
            AcademyCraft.LOGGER.error("SubLevel control/content/direct matrix failed", error);
            fail("control/content/direct matrix: " + error);
        }
    }

    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelClientSmoke");
    private static volatile boolean requested;
    private static volatile boolean failed;
    private static volatile boolean screenshotSaved;
    private static volatile boolean screenshotRequested;
    private static volatile boolean shutdownRequested;
    private static volatile boolean shutdownCompleted;
    private static int shutdownWait;
    private static volatile boolean collisionProbed;
    private static volatile boolean breakSent;
    private static volatile boolean breakSeen;
    private static volatile boolean placeSent;
    private static volatile boolean placeSeen;
    private static volatile boolean leverRequested;
    private static volatile boolean useSent;
    private static volatile boolean useSeen;
    private static volatile boolean chestRequested;
    private static volatile boolean chestSent;
    private static volatile boolean chestSeen;
    private static volatile boolean trappedRequested;
    private static volatile boolean trappedPrepared;
    private static volatile boolean trappedSent;
    private static volatile boolean trappedSeen;
    private static volatile boolean hopperRequested;
    private static volatile boolean hopperPrepared;
    private static volatile boolean hopperSent;
    private static volatile boolean hopperSeen;
    private static volatile boolean dispenserRequested;
    private static volatile boolean dispenserPrepared;
    private static volatile boolean dispenserSent;
    private static volatile boolean dispenserSeen;
    private static volatile boolean dropperRequested;
    private static volatile boolean dropperPrepared;
    private static volatile boolean dropperSent;
    private static volatile boolean dropperSeen;
    private static volatile boolean furnaceRequested;
    private static volatile boolean furnacePrepared;
    private static volatile boolean furnaceSent;
    private static volatile boolean furnaceSeen;
    private static volatile boolean doorRequested;
    private static volatile boolean doorPrepared;
    private static volatile boolean doorSent;
    private static volatile boolean doorSeen;
    private static volatile boolean barrelRequested;
    private static volatile boolean barrelPrepared;
    private static volatile boolean barrelSent;
    private static volatile boolean barrelSeen;
    private static volatile boolean doubleChestRequested;
    private static volatile boolean doubleChestPrepared;
    private static int carryPhase;
    private static int carryWait;
    private static int rotatePhase;
    private static double carryBeforeY;
    private static java.util.UUID armorStandId;
    private static java.util.UUID vehicleId;
    private static java.util.UUID vehicleRiderId;
    private static volatile boolean skillPermissionProbed;
    private static volatile boolean doubleChestSent;
    private static volatile boolean doubleChestSeen;
    private static double initialPoseY = Double.NaN;
    private static boolean launchRequested;
    private static int launchWait;
    private static double launchInitialY = Double.NaN;
    private static boolean launchProbed;
    private static boolean programChainProbed;
    private static volatile boolean programChainCompleted;
    private static volatile boolean controlMatrixRequested;
    private static boolean contentProbed;
    private static boolean multiHostProbed;
    private static boolean visualFramingRequested;
    private static volatile Vec3 visualCenter;
    private static volatile boolean restoreRequested;
    private static volatile boolean restoreCompleted;
    private static boolean motionProbed;
    private static boolean motionStarted;
    private static int motionWait;
    private static double motionInitialY;
    private static int holdTicks;
    private static volatile SubLevelCaptureService.Result result;
    private static int ticks;
    private static int stableFrames;
    private static int observedProjectionId;
    private static Path screenshotPath;

    private SubLevelClientSmoke() {
    }

    private static boolean matrixComplete() {
        return SubLevelDirectSkillSmoke.complete() && (!Boolean.getBoolean("academy.sublevel.defaultPathProbe")
                || SubLevelProgramGraphSmoke.complete());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || failed) return;
        var minecraft = Minecraft.getInstance();
        if (screenshotSaved) {
            if (!Boolean.getBoolean("academy.sublevel.controlMatrixProbe")) return;
            if (!shutdownRequested) {
                shutdownRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) { fail("missing integrated server during shutdown probe"); return; }
                server.execute(() -> {
                    try {
                        SubLevelTractionSmoke.shutdown(server);
                        shutdownCompleted = true;
                    } catch (Throwable error) { fail("shutdown probe: " + error); }
                });
            }
            if (shutdownCompleted && SubLevelTractionSmoke.clientSnapshotsCleared()) {
                AcademyCraft.LOGGER.info("SUBLEVEL_SHUTDOWN_CLIENT_PASSED snapshots_cleared=true");
                AcademyCraft.LOGGER.info("SUBLEVEL_CLIENT_SMOKE_PASSED id={} cells={} screenshot={}",
                        result.id().value(), result.blocks(), screenshotPath);
                minecraft.stop();
            } else if (++shutdownWait > 100) fail("shutdown left client snapshots");
            return;
        }
        if (minecraft.level == null || minecraft.player == null) return;
        if (Boolean.getBoolean("academy.sublevel.restoreProbe")) {
            if (!restoreRequested) {
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                restoreRequested = true;
                server.execute(() -> {
                    try {
                        SubLevelMultiHostSmoke.verifyRestored(server);
                        restoreCompleted = true;
                    } catch (Throwable error) { fail("default restore probe: " + error); }
                });
            }
            if (restoreCompleted) {
                AcademyCraft.LOGGER.info("SUBLEVEL_DEFAULT_RESTORE_PASSED");
                minecraft.stop();
            }
            return;
        }
        if (++ticks > (Boolean.getBoolean("academy.sublevel.defaultPathProbe") ? 3000 : 600)) {
            fail("timeout");
            return;
        }
        if (!requested) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return;
            if (!skillPermissionProbed) {
                skillPermissionProbed = true;
                server.execute(() -> probeSkillPermission(server));
                return;
            }
            requested = true;
            server.execute(() -> capture(server, minecraft));
            return;
        }
        if (result == null) return;
        // Capture binds this plot lease to result.id(). Client-only projections retain the
        // unassigned legacy reference, but accept() gives the plot snapshot and its projection
        // the same immutable view object. Match that identity, never an arbitrary cell count.
        var snapshot = org.academy.internal.client.sublevel.SubLevelClientState.snapshots(minecraft.level)
                .stream().filter(value -> value.plot().equals(result.plot())).findFirst().orElse(null);
        if (snapshot == null || snapshot.view().isEmpty()) return;
        NativeStructureProjectionEntity projection = null;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity candidate && candidate.isClientOnly()
                    && candidate.view() == snapshot.view()) {
                projection = candidate;
                break;
            }
        }
        if (projection == null) return;
        if (observedProjectionId != projection.getId()) {
            observedProjectionId = projection.getId();
            AcademyCraft.LOGGER.info("SUBLEVEL_CLIENT_SMOKE_PROJECTION id={} plot={} entity_id={} cells={}",
                    result.id().value(), result.plot(), observedProjectionId, projection.view().cells().size());
        }
        if (!controlMatrixRequested || matrixComplete()) aimAt(minecraft, projection);
        if (Boolean.getBoolean("academy.sublevel.clientBreakProbe") && !breakSeen) {
            if (!breakSent) {
                if (!org.academy.internal.client.sublevel.SubLevelClientInteraction.attack(minecraft)) {
                    fail("client SubLevel attack raycast missed");
                    return;
                }
                breakSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BREAK_SENT");
                return;
            }
            if (projection.view().cells().size() == result.blocks() - 1) {
                breakSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BREAK_CONFIRMED cells={}", projection.view().cells().size());
            } else {
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.clientPlaceProbe") && breakSeen && !placeSeen) {
            if (!placeSent) {
                var eye = minecraft.player.getEyePosition();
                var view = minecraft.player.getViewVector(1.0F);
                var hit = org.academy.internal.client.sublevel.SubLevelClientState.raycast(
                        minecraft.level, eye, eye.add(view.scale(minecraft.player.blockInteractionRange() + 0.5)));
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PLACE_RAY eye={} view={} yaw={} pitch={} hit={}",
                        eye, view, minecraft.player.getYRot(), minecraft.player.getXRot(), hit);
                if (!org.academy.internal.client.sublevel.SubLevelClientInteraction.use(minecraft)) {
                    fail("client SubLevel place raycast missed");
                    return;
                }
                placeSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PLACE_SENT");
                return;
            }
            if (projection.view().cells().size() == result.blocks()) {
                placeSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PLACE_CONFIRMED cells={}", projection.view().cells().size());
            } else {
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.clientUseProbe") && placeSeen && !useSeen) {
            if (!leverRequested) {
                leverRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareLever(server, result.id()));
                return;
            }
            var lever = findLever(projection);
            if (lever == null) return;
            aimAtCell(minecraft, projection, lever.position());
            if (!useSent) {
                var center = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        lever.position().getX() + 0.5, lever.position().getY() + 0.5, lever.position().getZ() + 0.5));
                var toward = minecraft.player.getEyePosition().subtract(center);
                var face = Direction.getNearest((int) Math.signum(toward.x),
                        (int) Math.signum(toward.y), (int) Math.signum(toward.z), Direction.UP);
                var point = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), lever.position(),
                        face, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                useSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_USE_SENT local={}", lever.position());
                return;
            }
            if (lever.state().getValue(LeverBlock.POWERED)) {
                useSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_USE_CONFIRMED powered=true");
            } else {
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.clientChestProbe") && useSeen && !chestSeen) {
            if (!chestRequested) {
                chestRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareChest(server, result.id()));
                return;
            }
            var chest = findChest(projection);
            if (chest == null) return;
            if (!chestSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        chest.position().getX() + 0.5, chest.position().getY() + 0.5, chest.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), chest.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                chestSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CHEST_SENT local={}", chest.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
                chestSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CHEST_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientDoubleChestProbe") && chestSeen && !doubleChestSeen) {
            if (!doubleChestRequested) {
                doubleChestRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareDoubleChest(server, result.id()));
                return;
            }
            if (!doubleChestPrepared) return;
            var chest = findChest(projection);
            if (chest == null) return;
            if (!doubleChestSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        chest.position().getX() + 0.5, chest.position().getY() + 0.5, chest.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), chest.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                doubleChestSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DOUBLE_CHEST_SENT local={}", chest.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 90) {
                doubleChestSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DOUBLE_CHEST_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientTrappedChestProbe") && chestSeen && !trappedSeen) {
            if (!trappedRequested) {
                trappedRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareTrappedChest(server, result.id()));
                return;
            }
            if (!trappedPrepared) return;
            var chest = findBlock(projection, Blocks.TRAPPED_CHEST);
            if (chest == null) return;
            if (!trappedSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        chest.position().getX() + 0.5, chest.position().getY() + 0.5, chest.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), chest.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                trappedSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_TRAPPED_CHEST_SENT local={}", chest.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 63) {
                trappedSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_TRAPPED_CHEST_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientHopperProbe") && chestSeen && !hopperSeen) {
            if (!hopperRequested) {
                hopperRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareHopper(server, result.id()));
                return;
            }
            if (!hopperPrepared) return;
            var hopper = findBlock(projection, Blocks.HOPPER);
            if (hopper == null) return;
            if (!hopperSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        hopper.position().getX() + 0.5, hopper.position().getY() + 0.5, hopper.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), hopper.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                hopperSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_HOPPER_SENT local={}", hopper.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 41) {
                hopperSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_HOPPER_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientDispenserProbe") && hopperSeen && !dispenserSeen) {
            if (!dispenserRequested) {
                dispenserRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareDispenser(server, result.id()));
                return;
            }
            if (!dispenserPrepared) return;
            var dispenser = findBlock(projection, Blocks.DISPENSER);
            if (dispenser == null) return;
            if (!dispenserSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        dispenser.position().getX() + 0.5, dispenser.position().getY() + 0.5, dispenser.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), dispenser.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                dispenserSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DISPENSER_SENT local={}", dispenser.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 45) {
                dispenserSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DISPENSER_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientDropperProbe") && dispenserSeen && !dropperSeen) {
            if (!dropperRequested) {
                dropperRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareDropper(server, result.id()));
                return;
            }
            if (!dropperPrepared) return;
            var dropper = findBlock(projection, Blocks.DROPPER);
            if (dropper == null) return;
            if (!dropperSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        dropper.position().getX() + 0.5, dropper.position().getY() + 0.5, dropper.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), dropper.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                dropperSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DROPPER_SENT local={}", dropper.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 45) {
                dropperSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DROPPER_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientFurnaceProbe") && dispenserSeen && !furnaceSeen) {
            if (!furnaceRequested) {
                furnaceRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareFurnace(server, result.id()));
                return;
            }
            if (!furnacePrepared) return;
            var furnace = findBlock(projection, Blocks.FURNACE);
            if (furnace == null) return;
            if (!furnaceSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        furnace.position().getX() + 0.5, furnace.position().getY() + 0.5, furnace.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), furnace.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                furnaceSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_FURNACE_SENT local={}", furnace.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 39) {
                furnaceSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_FURNACE_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientDoorProbe") && furnaceSeen && !doorSeen) {
            if (!doorRequested) {
                doorRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareDoor(server, result.id()));
                return;
            }
            if (!doorPrepared) return;
            var door = findBlock(projection, Blocks.OAK_DOOR);
            if (door == null) return;
            if (!doorSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        door.position().getX() + 0.5, door.position().getY() + 0.5, door.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), door.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                doorSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DOOR_SENT local={}", door.position());
                return;
            }
            for (var cell : projection.view().cells()) {
                if (cell.state().is(Blocks.OAK_DOOR) && cell.state().getValue(DoorBlock.OPEN)) {
                    doorSeen = true;
                    AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DOOR_OPENED local={}", cell.position());
                    break;
                }
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.clientBarrelProbe") && doorSeen && !barrelSeen) {
            if (!barrelRequested) {
                barrelRequested = true;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> prepareBarrel(server, result.id()));
                return;
            }
            if (!barrelPrepared) return;
            var barrel = findBlock(projection, Blocks.BARREL);
            if (barrel == null) return;
            if (!barrelSent) {
                var point = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                        barrel.position().getX() + 0.5, barrel.position().getY() + 0.5, barrel.position().getZ() + 0.5));
                SubLevelInteractPacket.send(new SubLevelInteractPacket(result.id(), barrel.position(),
                        Direction.UP, point, InteractionHand.MAIN_HAND, SubLevelInteractPacket.Action.USE));
                barrelSent = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BARREL_SENT local={}", barrel.position());
                return;
            }
            if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen
                    && screen.getMenu().slots.size() >= 63) {
                barrelSeen = true;
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BARREL_OPENED screen={} slots={} item0={}",
                        screen.getClass().getSimpleName(), screen.getMenu().slots.size(),
                        screen.getMenu().getSlot(0).getItem());
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
            }
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.carryProbe") && chestSeen && carryPhase < 3) {
            if (carryPhase == 0) {
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> placeOnTop(server, result.id()));
                carryPhase = 1;
                return;
            }
            if (carryPhase == 1) {
                if (++carryWait < 5) return;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                carryBeforeY = minecraft.player.getY();
                server.execute(() -> {
                    var shifted = SubLevelServer.shiftFirst(server, 0.0, 2.0, 0.0);
                    var stand = armorStandId == null ? null : server.overworld().getEntity(armorStandId);
                    AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CARRY_SHIFT {} stand_y={}",
                            shifted, stand == null ? "missing" : stand.getY());
                });
                carryPhase = 2;
                return;
            }
            if (++carryWait < 35) return;
            var afterY = minecraft.player.getY();
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CARRY before_y={} after_y={} delta={}",
                    carryBeforeY, afterY, afterY - carryBeforeY);
            carryPhase = 3;
            var server = minecraft.getSingleplayerServer();
            if (server != null && armorStandId != null) {
                server.execute(() -> {
                    var stand = server.overworld().getEntity(armorStandId);
                    AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CARRY_ENTITY y={}",
                            stand == null ? "missing" : stand.getY());
                    var vehicle = vehicleId == null ? null : server.overworld().getEntity(vehicleId);
                    var rider = vehicleRiderId == null ? null : server.overworld().getEntity(vehicleRiderId);
                    AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CARRY_VEHICLE vehicle_y={} rider_y={} riding={}",
                            vehicle == null ? "missing" : vehicle.getY(),
                            rider == null ? "missing" : rider.getY(),
                            rider != null && rider.getVehicle() == vehicle);
                });
            }
            minecraft.getSingleplayerServer().execute(() ->
                    minecraft.getSingleplayerServer().getPlayerList().getPlayers().getFirst().setNoGravity(false));
            if (afterY - carryBeforeY < 1.0) {
                fail("player was not carried with the SubLevel");
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.carryRotateProbe") && carryPhase == 3 && rotatePhase < 2) {
            if (rotatePhase == 0) {
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_ROTATE_SHIFT {}",
                        SubLevelServer.rotateFirst(server, 90.0)));
                rotatePhase = 1;
                return;
            }
            if (++carryWait < 20) return;
            var server = minecraft.getSingleplayerServer();
            if (server != null && armorStandId != null) {
                server.execute(() -> {
                    var stand = server.overworld().getEntity(armorStandId);
                    AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_ROTATE_ENTITY x={} z={}",
                            stand == null ? "missing" : stand.getX(),
                            stand == null ? "missing" : stand.getZ());
                });
            }
            rotatePhase = 2;
        }
        if (Boolean.getBoolean("academy.sublevel.clientCollision") && !collisionProbed) {
            collisionProbed = true;
            var center = projection.renderPose(1.0F).localToWorldPoint(new Vec3(1, 1, 1));
            var query = new net.minecraft.world.phys.AABB(center.x - 0.5, center.y - 0.5, center.z - 0.5,
                    center.x + 0.5, center.y + 0.5, center.z + 0.5);
            int collisions = 0;
            for (var shape : minecraft.level.getBlockCollisions(minecraft.player, query)) {
                if (!shape.isEmpty()) collisions++;
            }
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_COLLISION_PROBE inside={}", collisions);
            if (collisions == 0) {
                fail("client collision index produced no shapes");
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.fallProbe")) {
            if (Double.isNaN(initialPoseY)) initialPoseY = projection.viewState().pose().worldOrigin().y;
            if (holdTicks++ < 100) return;
            if (holdTicks == 101) {
                AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_FALL_PROBE y={} initial_y={} delta={}",
                        projection.viewState().pose().worldOrigin().y, initialPoseY,
                        projection.viewState().pose().worldOrigin().y - initialPoseY);
            }
        }
        if (Boolean.getBoolean("academy.sublevel.launchProbe") && !launchProbed) {
            if (!launchRequested) {
                launchRequested = true;
                launchInitialY = projection.viewState().pose().worldOrigin().y;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                server.execute(() -> AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_LAUNCH {}",
                        SubLevelServer.launchFirst(server, new Vec3(0.0, 1.0, 0.0), 5.0)));
                return;
            }
            if (++launchWait < 20) return;
            var y = projection.viewState().pose().worldOrigin().y;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_LAUNCH_PROBE y={} initial_y={} delta={}",
                    y, launchInitialY, y - launchInitialY);
            launchProbed = true;
            if (y - launchInitialY < 0.05) {
                fail("launch impulse did not move the SubLevel");
                return;
            }
        }
        if (Boolean.getBoolean("academy.sublevel.programChainProbe") && !programChainProbed) {
            programChainProbed = true;
            var server = minecraft.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> probeProgramChain(server));
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.programChainProbe") && !programChainCompleted) return;
        if (Boolean.getBoolean("academy.sublevel.controlMatrixProbe") && !matrixComplete()) {
            controlMatrixRequested = true;
            return;
        }
        if (Boolean.getBoolean("academy.sublevel.defaultPathProbe")) {
            if (!visualFramingRequested) {
                visualFramingRequested = true;
                minecraft.getSingleplayerServer().execute(() -> prepareVisualFrame(minecraft.getSingleplayerServer()));
                return;
            }
            var center = visualCenter;
            if (center == null) return;
            minecraft.player.setNoGravity(true);
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.resetFallDistance();
            if (projection.renderPose(1.0F).localToWorldPoint(projection.view().bounds().getCenter())
                    .distanceTo(center) > 2.0) return;
        }
        if (Boolean.getBoolean("academy.sublevel.motionProbe") && !motionProbed) {
            if (!motionStarted) {
                motionStarted = true;
                motionInitialY = projection.viewState().pose().worldOrigin().y;
                var server = minecraft.getSingleplayerServer();
                if (server == null) return;
                var center = projection.viewState().pose().localToWorldPoint(
                        projection.view().bounds().getCenter());
                server.execute(() -> AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_MOTION_START {}",
                        SubLevelSkillSupport.moveTo(server, result.id(), center.add(0, 3, 0), 1.0)));
                return;
            }
            if (++motionWait < 20) return;
            var y = projection.viewState().pose().worldOrigin().y;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_MOTION_PROBE y={} initial_y={} delta={}",
                    y, motionInitialY, y - motionInitialY);
            motionProbed = true;
        }
        var frames = NativeStructureProjectionRenderer.submittedFrames(projection.getId());
        if (frames <= 0) return;
        if (++stableFrames < 20) return;
        if (screenshotRequested) return;
        if (!minecraft.player.isAlive() || minecraft.gui.screen() != null) {
            fail("visual verification requires a living player and an unobstructed world view");
            return;
        }
        screenshotRequested = true;
        var name = "sublevel-client-smoke-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
        screenshotPath = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                if (!Files.isRegularFile(screenshotPath) || Files.size(screenshotPath) <= 0) {
                    fail("screenshot missing: " + message.getString());
                    return;
                }
                screenshotSaved = true;
                if (!Boolean.getBoolean("academy.sublevel.controlMatrixProbe")) {
                    AcademyCraft.LOGGER.info("SUBLEVEL_CLIENT_SMOKE_PASSED id={} cells={} frames={} screenshot={}",
                            result.id().value(), result.blocks(), frames, screenshotPath);
                    minecraft.stop();
                }
            } catch (Throwable error) {
                fail(error.toString());
            }
        });
    }

    /** Frame the existing tested body in clear air so terrain cannot hide the final render check. */
    private static void prepareVisualFrame(MinecraftServer server) {
        try {
            var player = server.getPlayerList().getPlayers().getFirst();
            var host = player.level();
            var runtime = SubLevelServer.runtime(host).orElseThrow();
            var body = runtime.get(result.id()).orElseThrow();
            var center = new Vec3(player.getX(), host.getMaxY() - 40, player.getZ());
            for (var pos : BlockPos.betweenClosed(BlockPos.containing(center.add(-3, -3, -3)),
                    BlockPos.containing(center.add(10, 8, 10)))) {
                host.getChunkAt(pos);
                if (!host.getBlockState(pos).isAir()) throw new IllegalStateException("visual frame is obstructed");
            }
            if (!runtime.shift(result.id(), center.subtract(body.worldCenter()))
                    || !SubLevelSkillSupport.moveTo(server, result.id(), center, 10)) {
                throw new IllegalStateException("could not frame captured body");
            }
            player.resetFallDistance();
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(center.x + 7, center.y + 3, center.z + 7, 135, 20);
            player.resetFallDistance();
            visualCenter = center;
            AcademyCraft.LOGGER.info("SUBLEVEL_VISUAL_FRAME center={} cells={} clear=true", center, result.blocks());
        } catch (Throwable error) {
            fail("visual frame: " + error);
        }
    }

    private static void prepareLever(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) {
                    nearest = distance;
                    local = cell.position();
                }
            }
            var lever = Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, Direction.NORTH)
                    .setValue(LeverBlock.POWERED, false);
            if (!runtime.storage().setBlock(subLevel.plot(), local, lever, 3)) {
                throw new IllegalStateException("plot setBlock failed at " + local);
            }
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_USE_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void placeOnTop(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var bounds = subLevel.localBounds();
            var local = new Vec3((bounds.minX + bounds.maxX) / 2.0, bounds.maxY + 0.1,
                    (bounds.minZ + bounds.maxZ) / 2.0);
            var world = subLevel.pose().localToWorldPoint(local);
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setNoGravity(true);
            player.teleportTo(world.x, world.y, world.z);
            var stand = new ArmorStand(server.overworld(), world.x + 0.5, world.y, world.z + 0.5);
            stand.setNoGravity(true);
            server.overworld().addFreshEntity(stand);
            armorStandId = stand.getUUID();
            var vehicle = new ArmorStand(server.overworld(), world.x - 0.5, world.y, world.z - 0.5);
            vehicle.setNoGravity(true);
            server.overworld().addFreshEntity(vehicle);
            var vehicleRider = new ArmorStand(server.overworld(), world.x - 0.5, world.y, world.z - 0.5);
            vehicleRider.setNoGravity(true);
            server.overworld().addFreshEntity(vehicleRider);
            vehicleRider.startRiding(vehicle, true, true);
            vehicleId = vehicle.getUUID();
            vehicleRiderId = vehicleRider.getUUID();
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CARRY_PLACED x={} z={} y={} stand_y={} vehicle_y={} rider_y={} riding={}",
                    world.x, world.z, world.y, stand.getY(), vehicle.getY(), vehicleRider.getY(),
                    vehicleRider.getVehicle() == vehicle);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareHopper(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.HOPPER.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot hopper setBlock failed at " + local);
            }
            var hopper = new HopperBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            hopper.setItem(0, new ItemStack(Items.DIAMOND, 3));
            runtime.storage().level().setBlockEntity(hopper);
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            hopperPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_HOPPER_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareBarrel(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.BARREL.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot barrel setBlock failed at " + local);
            }
            var barrel = new BarrelBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            barrel.setItem(0, new ItemStack(Items.DIAMOND, 3));
            runtime.storage().level().setBlockEntity(barrel);
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            barrelPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_BARREL_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareDoor(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            org.academy.internal.common.structure.space.NativeStructureView.Cell lower = null;
            org.academy.internal.common.structure.space.NativeStructureView.Cell upper = null;
            for (var a : cells) {
                for (var b : cells) {
                    if (a.position().getX() == b.position().getX()
                            && a.position().getZ() == b.position().getZ()
                            && b.position().getY() == a.position().getY() + 1) {
                        lower = a; upper = b; break;
                    }
                }
                if (lower != null) break;
            }
            if (lower == null) throw new IllegalStateException("no vertical door cells");
            var baseDoor = Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.NORTH)
                    .setValue(DoorBlock.OPEN, false)
                    .setValue(DoorBlock.POWERED, false)
                    .setValue(DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT);
            var lowerState = baseDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            var upperState = baseDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            if (!runtime.storage().setBlock(subLevel.plot(), lower.position(), lowerState, 3)
                    || !runtime.storage().setBlock(subLevel.plot(), upper.position(), upperState, 3)) {
                throw new IllegalStateException("door setBlock failed");
            }
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            doorPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DOOR_PREPARED lower={} upper={}", lower.position(), upper.position());
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareFurnace(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.FURNACE.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot furnace setBlock failed at " + local);
            }
            var furnace = new FurnaceBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            furnace.setItem(0, new ItemStack(Items.IRON_ORE, 3));
            runtime.storage().level().setBlockEntity(furnace);
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            furnacePrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_FURNACE_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareDropper(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.DROPPER.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot dropper setBlock failed at " + local);
            }
            var dropper = new DropperBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            dropper.setItem(0, new ItemStack(Items.DIAMOND, 3));
            runtime.storage().level().setBlockEntity(dropper);
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            dropperPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DROPPER_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareDispenser(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.DISPENSER.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot dispenser setBlock failed at " + local);
            }
            var dispenser = new DispenserBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            dispenser.setItem(0, new ItemStack(Items.DIAMOND, 3));
            runtime.storage().level().setBlockEntity(dispenser);
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            dispenserPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DISPENSER_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static org.academy.internal.common.structure.space.NativeStructureView.Cell findBlock(
            NativeStructureProjectionEntity projection, net.minecraft.world.level.block.Block block) {
        for (var cell : projection.view().cells()) {
            if (cell.state().is(block)) return cell;
        }
        return null;
    }

    private static void prepareChest(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.CHEST.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot chest setBlock failed at " + local);
            }
            var chest = new ChestBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            runtime.storage().level().setBlockEntity(chest);
            runtime.refreshGeometry(id);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CHEST_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void prepareTrappedChest(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            if (cells.isEmpty()) throw new IllegalStateException("empty view");
            var player = server.getPlayerList().getPlayers().getFirst();
            var eye = player.getEyePosition();
            var local = cells.getFirst().position();
            double nearest = Double.POSITIVE_INFINITY;
            for (var cell : cells) {
                var worldCenter = subLevel.pose().localToWorldPoint(new Vec3(
                        cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
                double distance = eye.distanceToSqr(worldCenter);
                if (distance < nearest) { nearest = distance; local = cell.position(); }
            }
            var state = Blocks.TRAPPED_CHEST.defaultBlockState();
            if (!runtime.storage().setBlock(subLevel.plot(), local, state, 3)) {
                throw new IllegalStateException("plot trapped chest setBlock failed at " + local);
            }
            var chest = new TrappedChestBlockEntity(runtime.storage().worldPosition(subLevel.plot(), local), state);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            runtime.storage().level().setBlockEntity(chest);
            runtime.refreshGeometry(id);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            trappedPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_TRAPPED_CHEST_PREPARED local={}", local);
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static org.academy.internal.common.structure.space.NativeStructureView.Cell findChest(
            NativeStructureProjectionEntity projection) {
        for (var cell : projection.view().cells()) {
            if (cell.state().is(Blocks.CHEST)) return cell;
        }
        return null;
    }

    private static void prepareDoubleChest(MinecraftServer server, SubLevelId id) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var subLevel = runtime.get(id).orElseThrow();
            var cells = runtime.view(id).cells();
            org.academy.internal.common.structure.space.NativeStructureView.Cell left = null;
            org.academy.internal.common.structure.space.NativeStructureView.Cell right = null;
            for (var a : cells) {
                for (var b : cells) {
                    if (a.position().getY() == b.position().getY()
                            && a.position().getZ() == b.position().getZ()
                            && b.position().getX() == a.position().getX() + 1) {
                        left = a; right = b; break;
                    }
                }
                if (left != null) break;
            }
            if (left == null) throw new IllegalStateException("no adjacent chest cells");
            var leftState = Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.NORTH)
                    .setValue(ChestBlock.TYPE, ChestType.LEFT);
            var rightState = Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.NORTH)
                    .setValue(ChestBlock.TYPE, ChestType.RIGHT);
            if (!runtime.storage().setBlock(subLevel.plot(), left.position(), leftState, 3)
                    || !runtime.storage().setBlock(subLevel.plot(), right.position(), rightState, 3)) {
                throw new IllegalStateException("double chest setBlock failed");
            }
            var leftChest = new ChestBlockEntity(runtime.storage().worldPosition(subLevel.plot(), left.position()), leftState);
            leftChest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            var rightChest = new ChestBlockEntity(runtime.storage().worldPosition(subLevel.plot(), right.position()), rightState);
            rightChest.setItem(0, new ItemStack(Items.EMERALD, 2));
            runtime.storage().level().setBlockEntity(leftChest);
            runtime.storage().level().setBlockEntity(rightChest);
            runtime.refreshGeometry(id);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            doubleChestPrepared = true;
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DOUBLE_CHEST_PREPARED left={} right={}", left.position(), right.position());
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static org.academy.internal.common.structure.space.NativeStructureView.Cell findLever(
            NativeStructureProjectionEntity projection) {
        for (var cell : projection.view().cells()) {
            if (cell.state().is(Blocks.LEVER)) return cell;
        }
        return null;
    }

    private static void aimAtCell(Minecraft minecraft, NativeStructureProjectionEntity projection, BlockPos local) {
        var target = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5));
        var eye = minecraft.player.getEyePosition();
        var delta = target.subtract(eye);
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        minecraft.player.setXRot((float) Math.toDegrees(-Math.atan2(delta.y, horizontal)));
    }

    private static void aimAt(Minecraft minecraft, NativeStructureProjectionEntity projection) {
        var cells = projection.view().cells();
        if (cells.isEmpty()) return;
        var cell = cells.get(cells.size() - 1);
        var target = projection.renderPose(1.0F).localToWorldPoint(new Vec3(
                cell.position().getX() + 0.5, cell.position().getY() + 0.5, cell.position().getZ() + 0.5));
        var eye = minecraft.player.getEyePosition();
        var delta = target.subtract(eye);
        var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        minecraft.player.setXRot((float) Math.toDegrees(-Math.atan2(delta.y, horizontal)));
    }

    private static void probeSkillPermission(MinecraftServer server) {
        try {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            var position = player.blockPosition().below();
            var before = level.getBlockState(position);
            var denied = SubLevelSkillSupport.capture(level, java.util.List.of(position),
                    (host, candidate) -> false);
            if (denied.isPresent()) throw new IllegalStateException("denied capture returned a SubLevel");
            if (!level.getBlockState(position).equals(before)) {
                throw new IllegalStateException("denied capture modified the source block");
            }
            AcademyCraft.LOGGER.info("SUBLEVEL_SKILL_PERMISSION_DENIED position={} state={}", position, before);
        } catch (Throwable error) {
            fail("skill permission probe: " + error);
        }
    }

    private static void capture(MinecraftServer server, Minecraft minecraft) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var player = server.getPlayerList().getPlayers().getFirst();
            var look = player.getLookAngle();
            var horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() < 1.0e-4) horizontal = Vec3.atLowerCornerOf(player.getDirection().getUnitVec3i());
            var center = BlockPos.containing(player.getEyePosition().add(horizontal.normalize().scale(3)));
            var level = player.level();
            var groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
            var base = center.above(Math.max(2, groundY + 2 - center.getY())).offset(-1, 0, -1);
            var positions = new ArrayList<BlockPos>(8);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        var position = base.offset(x, y, z);
                        level.setBlock(position, Blocks.GLOWSTONE.defaultBlockState(), 3);
                        positions.add(position);
                    }
                }
            }
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_GROUND base_y={} terrain_y={}", base.getY(), groundY);
            result = SubLevelCaptureService.capture(level, runtime, positions);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOWSTONE, 64));
            AcademyCraft.LOGGER.info("SUBLEVEL_CLIENT_SMOKE_CAPTURED id={} blocks={} boxes={}",
                    result.id().value(), result.blocks(), result.boxes());
            var subLevel = runtime.get(result.id()).orElseThrow();
            var handle = new SubLevelStructureHandle(subLevel, runtime.storage(), level.dimension().identifier());
            var cell = runtime.view(result.id()).cells().getFirst();
            var expected = handle.world().read(cell.position());
            var invalidBe = new CompoundTag();
            invalidBe.putString("id", "minecraft:chest");
            var rejected = handle.world().compareAndSet(cell.position(), expected,
                    new StructureBlockData(expected.state(), invalidBe));
            var after = handle.world().read(cell.position());
            if (rejected != StructureWriteResult.REJECTED
                    || after.state() != expected.state() || after.blockEntityData() != null) {
                throw new IllegalStateException("CAS failure rollback was not applied: " + rejected + " / " + after.state());
            }
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_CAS_ROLLBACK result={} state={}", rejected, after.state());
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void probeProgramChain(MinecraftServer server) {
        try {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = (net.minecraft.server.level.ServerLevel) player.level();
            var system = AbilitySystemServer.getSystem(player);
            var id = player.getUUID();
            system.setPlayerAbilityCategory(id, AbilityCategories.AEROMANIP.get());
            system.setPlayerLevel(id, 5);
            system.setPlayerBaseMaxCP(id, 100_000);
            system.setPlayerAvailableCP(id, 100_000);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            system.setPlayerStatus(id, org.academy.api.common.data.AbilityData.Status.NORMAL);
            var skill = Skills.HIGH_SPEED_JET.get();
            system.addPlayerSkill(player, skill.getKeyString());
            if (!skill.isEnabled(player)) system.toggleSkill(id, skill.getKeyString());
            system.getAeromanipResourceManager().onPlayerLogin(player);
            system.setPlayerCurrMP(id, system.getAeromanipResourceManager().getCapacity(player));

            var seed = player.blockPosition().offset(8, 5, 0);
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        if (!level.setBlock(seed.offset(dx, dy, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3)) {
                            throw new IllegalStateException("cannot place program source");
                        }
                    }
                }
            }
            var programNozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), level);
            programNozzle.attach(player.getUUID(), seed, Direction.DOWN);
            if (!level.addFreshEntity(programNozzle)) throw new IllegalStateException("cannot place program nozzle");
            var runtime = new ServerAeromanipProgramRuntime(player);
            var extract = runtime.extractStructure(new ProgramBlockPosition(
                    level.dimension().identifier(), seed.getX(), seed.getY(), seed.getZ()), 2, 16);
            if (extract.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || extract.structure() == null) {
                throw new IllegalStateException("program extract failed: " + extract.result());
            }
            var control = runtime.controlStructure(extract.structure());
            if (control.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || control.control() == null) {
                throw new IllegalStateException("program control failed: " + control.result());
            }
            var capturedId = SubLevelStructureRefs.id(extract.structure().reference()).orElseThrow();
            var subLevelRuntime = SubLevelServer.runtime(level.getServer()).orElseThrow();
            var capturedSubLevel = subLevelRuntime.get(capturedId).orElseThrow();
            var plotLevel = subLevelRuntime.storage().level();
            var plotOrigin = subLevelRuntime.storage().worldPosition(capturedSubLevel.plot(), new BlockPos(0, 1, 0));
            var item = new ItemEntity(plotLevel,
                    plotOrigin.getX() + 0.5, plotOrigin.getY() + 0.5, plotOrigin.getZ() + 0.5,
                    new ItemStack(Items.GLOWSTONE));
            item.setNoGravity(true);
            if (!plotLevel.addFreshEntity(item)) throw new IllegalStateException("cannot add migration fixture entity");
            var wrongDimension = runtime.staticizeStructure(extract.structure(), new ProgramBlockPosition(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "the_nether"),
                    seed.getX(), seed.getY(), seed.getZ()));
            var farTarget = runtime.staticizeStructure(extract.structure(), new ProgramBlockPosition(
                    level.dimension().identifier(), seed.getX() + 1_000, seed.getY(), seed.getZ()));
            if (wrongDimension.result().status() != ProgramStructureOperationResult.Status.REJECTED
                    || farTarget.result().status() != ProgramStructureOperationResult.Status.REJECTED) {
                throw new IllegalStateException("staticize validation accepted an invalid target: "
                        + wrongDimension.result() + " / " + farTarget.result());
            }
            var move = runtime.moveStructure(control.control(),
                    new ProgramWorldPosition(level.dimension().identifier(), seed.getX() + 0.5,
                            seed.getY() + 3.5, seed.getZ() + 0.5), null, 1.0);
            var rotate = runtime.rotateStructure(control.control(),
                    new ProgramStructurePose(new ProgramDirection(0.0, 1.0, 0.0), Math.toRadians(20.0)));
            var launch = runtime.launchStructure(control.control(), new ProgramDirection(0.0, 1.0, 0.0), 1.0f, 1.0);
            var release = runtime.releaseStructure(control.control());
            var staticize = runtime.staticizeStructure(extract.structure(), new ProgramBlockPosition(
                    level.dimension().identifier(), seed.getX(), seed.getY(), seed.getZ()));
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_PROGRAM_CHAIN extract={} control={} move={} rotate={} launch={} release={} staticize={} detail={} ref={}",
                    extract.result().status(), control.result().status(), move.result().status(),
                    rotate.result().status(), launch.result().status(), release.result().status(),
                    staticize.result().status(), staticize.result().detail(), extract.structure().reference());
            var migrated = level.getEntitiesOfClass(ItemEntity.class, new AABB(seed).inflate(4.0),
                    entity -> !entity.isRemoved());
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_ENTITY_MIGRATION count={} item={}", migrated.size(),
                    migrated.stream().findFirst().map(ItemEntity::position).orElse(null));
            if (migrated.isEmpty()) throw new IllegalStateException("staticize did not migrate plot entity");
            if (move.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || rotate.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || launch.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || release.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || staticize.result().status() != ProgramStructureOperationResult.Status.COMPLETED) {
                throw new IllegalStateException("program move/rotate/launch/release/staticize failed");
            }
            var directSeed = seed.offset(0, 0, 8);
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        level.setBlock(directSeed.offset(dx, dy, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
            var nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), level);
            nozzle.attach(player.getUUID(), directSeed, Direction.DOWN);
            level.addFreshEntity(nozzle);
            var direct = HighSpeedJetStructureService.launch(player,
                    new HighSpeedJetStructureService.LaunchRequest(directSeed, null,
                            new Vec3(0.0, 1.0, 0.0), 2.0, 1.0f, 200, true), 1.0f);
            if (direct.isEmpty()) throw new IllegalStateException("direct jet launch failed");
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_DIRECT_JET launch_id={}", direct.get().launchId());
            var directId = SubLevelStructureRefs.id(nozzle.structureMount().orElseThrow().reference()).orElseThrow();
            direct.get().close();
            if (subLevelRuntime.get(directId).isEmpty()) throw new IllegalStateException("direct close deleted structure");
            var directRestore = SubLevelSkillSupport.staticize(server, directId, directSeed, player, 64);
            if (!directRestore.succeeded()) throw new IllegalStateException("explicit direct restore failed: " + directRestore);

            system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get());
            var kinetic = Skills.KINETIC_THROW.get();
            system.addPlayerSkill(player, kinetic.getKeyString());
            if (!kinetic.isEnabled(player)) system.toggleSkill(id, kinetic.getKeyString());
            var kineticSeed = seed.offset(0, 0, 16);
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        level.setBlock(kineticSeed.offset(dx, dy, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
            var kineticRuntime = new ServerAcceleratorProgramRuntime(player);
            var kineticExtract = kineticRuntime.extractStructure(new ProgramBlockPosition(
                    level.dimension().identifier(), kineticSeed.getX(), kineticSeed.getY(), kineticSeed.getZ()), 1, 16);
            var kineticControl = kineticExtract.structure() == null ? null
                    : kineticRuntime.controlStructure(kineticExtract.structure());
            var kineticMove = kineticControl == null || kineticControl.control() == null ? null
                    : kineticRuntime.moveStructure(kineticControl.control(),
                    new ProgramWorldPosition(level.dimension().identifier(), kineticSeed.getX() + 0.5,
                            kineticSeed.getY() + 2.5, kineticSeed.getZ() + 0.5), null, 1.0);
            var kineticRotate = kineticControl == null || kineticControl.control() == null ? null
                    : kineticRuntime.rotateStructure(kineticControl.control(),
                    new ProgramStructurePose(new ProgramDirection(0.0, 1.0, 0.0), Math.toRadians(10.0)));
            var kineticLaunch = kineticControl == null || kineticControl.control() == null ? null
                    : kineticRuntime.launchStructure(kineticControl.control(),
                    new ProgramDirection(0.0, 1.0, 0.0), AcceleratorProgramStrength.STANDARD);
            var kineticRelease = kineticControl == null || kineticControl.control() == null ? null
                    : kineticRuntime.releaseStructure(kineticControl.control());
            var kineticRestoreTarget = player.blockPosition().offset(3, 8, 4);
            var kineticStaticize = kineticExtract.structure() == null ? null
                    : kineticRuntime.staticizeStructure(kineticExtract.structure(),
                    new ProgramBlockPosition(level.dimension().identifier(), kineticRestoreTarget.getX(),
                            kineticRestoreTarget.getY(), kineticRestoreTarget.getZ()));
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_ACCELERATOR_CHAIN extract={} control={} move={} rotate={} launch={} release={} staticize={}",
                    kineticExtract.result().status(), kineticControl == null ? "missing" : kineticControl.result().status(),
                    kineticMove == null ? "missing" : kineticMove.result().status(),
                    kineticRotate == null ? "missing" : kineticRotate.result().status(),
                    kineticLaunch == null ? "missing" : kineticLaunch.result().status(),
                    kineticRelease == null ? "missing" : kineticRelease.result().status(),
                    kineticStaticize == null ? "missing" : kineticStaticize.result().status());
            if (kineticControl == null || kineticMove == null || kineticRotate == null || kineticLaunch == null
                    || kineticRelease == null || kineticStaticize == null
                    || kineticExtract.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || kineticControl.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || kineticMove.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || kineticRotate.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || kineticLaunch.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || kineticRelease.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || kineticStaticize.result().status() != ProgramStructureOperationResult.Status.COMPLETED) {
                throw new IllegalStateException("accelerator SubLevel chain failed: "
                        + (kineticStaticize == null ? "missing staticize" : kineticStaticize.result()));
            }

            system.setPlayerAbilityCategory(id, AbilityCategories.ELECTROMASTER.get());
            var magnet = Skills.MAGNET_MANIPULATION.get();
            system.addPlayerSkill(player, magnet.getKeyString());
            if (!magnet.isEnabled(player)) system.toggleSkill(id, magnet.getKeyString());
            var magnetSeed = seed.offset(0, 0, 24);
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        level.setBlock(magnetSeed.offset(dx, dy, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    }
                }
            }
            var magnetRuntime = new ServerElectromasterProgramRuntime(player);
            var magnetExtract = magnetRuntime.extractStructure(new ProgramBlockPosition(
                    level.dimension().identifier(), magnetSeed.getX(), magnetSeed.getY(), magnetSeed.getZ()), 16, false);
            var magnetControl = magnetExtract.structure() == null ? null
                    : magnetRuntime.controlStructure(magnetExtract.structure());
            var magnetMove = magnetControl == null || magnetControl.control() == null ? null
                    : magnetRuntime.moveStructure(magnetControl.control(),
                    new ProgramWorldPosition(level.dimension().identifier(), magnetSeed.getX() + 0.5,
                            magnetSeed.getY() + 2.5, magnetSeed.getZ() + 0.5), null, 1.0);
            var magnetRotate = magnetControl == null || magnetControl.control() == null ? null
                    : magnetRuntime.rotateStructure(magnetControl.control(),
                    new ProgramStructurePose(new ProgramDirection(0.0, 1.0, 0.0), Math.toRadians(10.0)));
            var magnetLaunch = magnetControl == null || magnetControl.control() == null ? null
                    : magnetRuntime.launchStructure(magnetControl.control(),
                    new ProgramDirection(0.0, 1.0, 0.0), 1.0f);
            var magnetRelease = magnetControl == null || magnetControl.control() == null ? null
                    : magnetRuntime.releaseStructure(magnetControl.control());
            var magnetStaticize = magnetExtract.structure() == null ? null
                    : magnetRuntime.staticizeStructure(magnetExtract.structure(),
                    new ProgramBlockPosition(level.dimension().identifier(), magnetSeed.getX(),
                            magnetSeed.getY() + 8, magnetSeed.getZ()));
            AcademyCraft.LOGGER.info("CLIENT_SUBLEVEL_ELECTROMASTER_CHAIN extract={} control={} move={} rotate={} launch={} release={} staticize={}",
                    magnetExtract.result().status(), magnetControl == null ? "missing" : magnetControl.result().status(),
                    magnetMove == null ? "missing" : magnetMove.result().status(),
                    magnetRotate == null ? "missing" : magnetRotate.result().status(),
                    magnetLaunch == null ? "missing" : magnetLaunch.result().status(),
                    magnetRelease == null ? "missing" : magnetRelease.result().status(),
                    magnetStaticize == null ? "missing" : magnetStaticize.result().status());
            if (magnetControl == null || magnetMove == null || magnetRotate == null || magnetLaunch == null
                    || magnetRelease == null || magnetStaticize == null
                    || magnetExtract.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || magnetControl.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || magnetMove.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || magnetRotate.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || magnetLaunch.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || magnetRelease.result().status() != ProgramStructureOperationResult.Status.COMPLETED
                    || magnetStaticize.result().status() != ProgramStructureOperationResult.Status.COMPLETED) {
                throw new IllegalStateException("electromaster SubLevel chain failed");
            }
            programChainCompleted = true;
        } catch (Throwable error) {
            fail("program chain probe: " + error);
        }
    }

    private static void fail(String message) {
        failed = true;
        AcademyCraft.LOGGER.error("SUBLEVEL_CLIENT_SMOKE_FAILED {}", message);
        Minecraft.getInstance().stop();
    }
}
