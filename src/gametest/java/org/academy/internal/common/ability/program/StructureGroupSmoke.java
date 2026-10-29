package org.academy.internal.common.ability.program;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.SkillTuning;
import org.academy.internal.common.ability.*;
import org.academy.internal.common.ability.accelerator.program.ServerAcceleratorProgramRuntime;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.program.registry.*;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;

import java.nio.file.*;
import java.util.*;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.*;

/** Real category runtimes, scheduler, two live bodies and observation-only node wrappers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureGroupSmoke {
    private static final boolean BASIC = Boolean.getBoolean("academy.structureGroupBasicSmoke");
    private static final boolean TRANSLATION = Boolean.getBoolean("academy.structureGroupMoveSmoke");
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureGroupSmoke");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<ProgramStructureReference> TARGETS = new ArrayList<>();
    private static final Map<StructureRef, Vec3> CENTERS = new HashMap<>();
    private static final List<HighSpeedJetNozzle> NOZZLES = new ArrayList<>();
    private static final Map<Integer, Long> STARTED = new HashMap<>();
    private static final List<Integer> COMPLETED = new ArrayList<>();
    private static final StructurePose ROTATION = OrientationTargets.axisAngle(new Vec3(0, 0, 1), -0.5);
    private static volatile boolean ready, finished, failed, closing;
    private static volatile AbilityProgram editorProgram;
    private static Path output;
    private static ServerPlayer player;
    private static Vec3 oldPosition;
    private static boolean oldGravity;
    private static GameType oldMode;
    private static BlockPos seed;
    private static int categoryIndex, age, spinSamples;
    private static UUID capture;
    private static Skill skill;
    private static Identifier category;
    private static ProgramStructureGroupRuntime runtime;
    private static ProgramExecutionFrame frame;
    private static ServerProgramScheduler.SessionKey key;
    private static ProgramSessionScheduler.Termination termination;
    private static Throwable settlementFailure;
    private static float charged;
    private static boolean startedGraph;

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || finished || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst(); oldPosition = player.position();
                oldGravity = player.isNoGravity(); oldMode = player.gameMode.getGameModeForPlayer();
                prepare(); return;
            }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            check(++age < 700, "group fixture timeout: " + category);
            if (!startedGraph) { awaitCaptures(); return; }
            if (!TRANSLATION) supportGravity();
            if (termination != null) finishCategory();
        } catch (Throwable failure) { fail(failure); }
    }

    private static void prepare() {
        var ability = switch (categoryIndex) { case 0 -> AbilityCategories.ACCELERATOR.get(); case 1 -> AbilityCategories.ELECTROMASTER.get(); default -> AbilityCategories.AEROMANIP.get(); };
        skill = switch (categoryIndex) { case 0 -> Skills.KINETIC_THROW.get(); case 1 -> Skills.MAGNET_MANIPULATION.get(); default -> Skills.HIGH_SPEED_JET.get(); };
        category = ability.getKey(); var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAbilityCategory(player.getUUID(), ability); system.setPlayerLevel(player.getUUID(), 5);
        system.setPlayerBaseMaxCP(player.getUUID(), 100_000); system.setPlayerAvailableCP(player.getUUID(), 100_000);
        system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString()); if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
        if (categoryIndex == 2) { system.getAeromanipResourceManager().onPlayerLogin(player); system.setPlayerCurrMP(player.getUUID(), system.getAeromanipResourceManager().getCapacity(player)); }
        seed = new BlockPos((int) Math.floor(oldPosition.x) + 6, player.level().getMaxY() - 48, (int) Math.floor(oldPosition.z));
        for (var pos : BlockPos.betweenClosed(seed.offset(-2, -2, -2), seed.offset(6, 3, 3))) {
            player.level().getChunkAt(pos); check(player.level().getBlockState(pos).isAir(), "group arena occupied " + pos);
        }
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.connection.teleport(seed.getX() - 5, seed.getY(), seed.getZ() + .5, -90, 0);
        for (int offset : List.of(0, 4)) {
            player.level().setBlock(seed.offset(offset, -1, 0), Blocks.STONE.defaultBlockState(), 3);
            player.level().setBlock(seed.offset(offset, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        }
        capture = null; age = 0; startedGraph = false; termination = null; settlementFailure = null; key = null;
        STARTED.clear(); COMPLETED.clear(); TARGETS.clear(); CENTERS.clear(); spinSamples = 0; charged = 0;
        runtime = newRuntime();
        AcademyCraft.LOGGER.info("STRUCTURE_GROUP_PREPARE category={}", category);
    }

    private static ProgramStructureGroupRuntime newRuntime() {
        return switch (categoryIndex) { case 0 -> new ServerAcceleratorProgramRuntime(player); case 1 -> new ServerElectromasterProgramRuntime(player); default -> new ServerAeromanipProgramRuntime(player); };
    }
    private static void awaitCaptures() {
        if (age < 15) return;
        if (TARGETS.size() < 2) {
            if (capture == null) {
                var position = seed.offset(TARGETS.size() * 4, 0, 0);
                var result = StructureTransactions.capture(player.level(), new StructureCaptureRequest(List.of(position), 1, 1, StructureCaptureRequest.ALLOW, true));
                check(result.pending() || result.succeeded(), "group capture rejected: " + result); capture = result.transactionId();
            }
            var result = StructureTransactions.poll(player.level().getServer(), capture).orElseThrow();
            if (result.pending()) return;
            check(result.succeeded(), "group capture failed: " + result);
            var reference = result.structure().orElseThrow();
            var handle = StructureSpaces.resolve(player.level().getServer(), reference).orElse(null);
            if (handle == null || StructureDynamics.motion(player.level(), reference).isEmpty()) return;
            TARGETS.add(new ProgramStructureReference(reference, handle.runtimeId())); capture = null; return;
        }
        if (TARGETS.stream().anyMatch(target -> StructureDynamics.motion(player.level(), target.reference()).isEmpty())) return;
        for (var target : TARGETS) {
            var motion = motion(target); CENTERS.put(target.reference(), motion.pose().localToWorldPoint(motion.localCenterOfMass()));
            if (categoryIndex == 2) {
                var nozzle = new HighSpeedJetNozzle(EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), player.level());
                // Captured local grids are backend-owned. Find the one non-air support cell near COM.
                var local = BlockPos.containing(motion.localCenterOfMass());
                nozzle.attachToStructure(player.getUUID(), target.reference(), local, Direction.DOWN);
                check(player.level().addFreshEntity(nozzle), "group nozzle spawn failed"); NOZZLES.add(nozzle);
            }
        }
        preflightMatrix();
        if (TRANSLATION) movementPreflight();
        for (int offset : List.of(0, 4)) player.level().setBlock(seed.offset(offset, -1, 0), Blocks.AIR.defaultBlockState(), 3);
        startGraph(); startedGraph = true;
    }

    private static SpinRequest spinRequest() { return new SpinRequest(new ProgramDirection(0, 0, 1), StructureOrientation.AxisFrame.WORLD, -.8, 50, StructureOrientation.StopMode.BRAKE); }
    private static float cp() { return AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID()); }
    private static void preflightMatrix() {
        var before = cp();
        var stale = new ProgramStructureReference(TARGETS.get(1).reference(), UUID.randomUUID());
        check(runtime.spinStructures(List.of(TARGETS.getFirst(), stale), spinRequest()).result().status() == ProgramStructureOperationResult.Status.REJECTED, "stale group accepted");
        check(cp() == before && !occupied(), "preflight charged or leaked a lease");
        var other = UUID.randomUUID(); var last = TARGETS.get(1);
        check(StructureDirectControl.acquire(player.level(), last.reference(), player, other) == StructureDirectControl.Status.ACTIVE, "busy fixture acquisition failed");
        check(runtime.spinStructures(TARGETS, spinRequest()).result().status() == ProgramStructureOperationResult.Status.REJECTED, "occupied group accepted");
        check(cp() == before && StructureDirectControl.matches(last.reference(), player.getUUID(), other, last.runtimeId()), "group stole existing actor session");
        check(!StructureDirectControl.occupied(TARGETS.getFirst().reference(), TARGETS.getFirst().runtimeId()), "partial lease leaked");
        StructureDirectControl.release(last.reference(), other);
        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAvailableCP(player.getUUID(), 0);
        try { runtime.spinStructures(TARGETS, spinRequest()); throw new IllegalStateException("unpaid group accepted"); }
        catch (IllegalStateException expected) { check(!expected.getMessage().equals("unpaid group accepted"), expected.getMessage()); }
        check(!occupied(), "CP rejection leaked group");
        system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL); system.setPlayerAvailableCP(player.getUUID(), before);
        // No physics tick occurs between these adversarial submission/cleanup checks.
        var started = runtime.spinStructures(TARGETS, spinRequest());
        check(started.result().status() == ProgramStructureOperationResult.Status.PENDING && ServerStructureGroupControl.members(player).size() == 2, "group did not acquire both members: " + started);
        check(runtime.structureGroupStatus(UUID.randomUUID()).result().status() == ProgramStructureOperationResult.Status.REJECTED && occupied(), "foreign receipt changed group");
        var crowded = new ArrayList<ProgramStructureReference>();
        for (int i = 0; i < 7; i++) crowded.add(new ProgramStructureReference(new StructureRef(UUID.randomUUID(), last.reference().dimension(), last.reference().backend(), 1), UUID.randomUUID()));
        var otherRuntime = newRuntime();
        check(otherRuntime.spinStructures(crowded, spinRequest()).result().detail().equals("actor_group_budget"), "cross-runtime actor budget bypassed");
        otherRuntime.closeStructureGroups();
        var replacement = UUID.randomUUID();
        check(StructureDirectControl.acquire(player.level(), last.reference(), player, replacement) == StructureDirectControl.Status.ACTIVE, "replacement fixture acquisition failed");
        check(runtime.structureGroupStatus(started.operationId()).result().status() == ProgramStructureOperationResult.Status.REJECTED, "lost member kept group running");
        check(StructureDirectControl.matches(last.reference(), player.getUUID(), replacement, last.runtimeId()), "old group released successor");
        StructureDirectControl.release(last.reference(), replacement); check(!occupied(), "lost lease cleanup leaked");
        started = runtime.spinStructures(TARGETS, spinRequest()); check(started.result().status() == ProgramStructureOperationResult.Status.PENDING, "cancel setup failed");
        var cleanup = new ProgramExecutionFrame(new ProgramActionTransaction(), runtime);
        ServerProgramExecution.closeOrientationCommands(cleanup); check(!occupied(), "program cancellation leaked group");
        runtime = newRuntime();
    }

    private static ProgramStructureMoveRequest moveRequest() {
        return new ProgramStructureMoveRequest(new ProgramVector(0, 0, 3), 20);
    }
    private static void movementPreflight() {
        float before = cp();
        var invalid = new ProgramStructureMoveRequest(new ProgramVector(0, 64, 0), 20);
        check(runtime.moveStructures(TARGETS, invalid).result().status() == ProgramStructureOperationResult.Status.REJECTED,
                "out-of-world group targets accepted");
        check(cp() == before && !occupied(), "invalid target charged or leaked");
        var pending = runtime.moveStructures(TARGETS, moveRequest());
        check(pending.result().status() == ProgramStructureOperationResult.Status.PENDING, "movement cancellation setup failed");
        var last = TARGETS.get(1); var successor = UUID.randomUUID();
        check(StructureDirectControl.acquire(player.level(), last.reference(), player, successor) == StructureDirectControl.Status.ACTIVE, "movement successor setup failed");
        if (!NOZZLES.isEmpty()) NOZZLES.getLast().activate(77);
        check(runtime.structureGroupStatus(pending.operationId()).result().status() == ProgramStructureOperationResult.Status.REJECTED, "lost movement lease ignored");
        check(StructureDirectControl.matches(last.reference(), player.getUUID(), successor, last.runtimeId()), "movement removed successor");
        if (!NOZZLES.isEmpty()) {
            check(NOZZLES.getFirst().activeTicks() == 0 && NOZZLES.getLast().activeTicks() == 77, "movement overwrote successor nozzle state");
            NOZZLES.getLast().restoreActiveTicks(0);
        }
        StructureDirectControl.release(last.reference(), successor);
        pending = runtime.moveStructures(TARGETS, moveRequest());
        check(pending.result().status() == ProgramStructureOperationResult.Status.PENDING, "movement program cancellation setup failed");
        ServerProgramExecution.closeOrientationCommands(new ProgramExecutionFrame(new ProgramActionTransaction(), runtime));
        check(!occupied(), "movement cancellation leaked leases");
        check(NOZZLES.stream().allMatch(nozzle -> nozzle.activeTicks() == 0), "movement cancellation leaked nozzle activation");
        runtime = newRuntime();
    }
    private static List<ProgramStructureReference> executingTargets() { return BASIC ? List.of(TARGETS.getFirst()) : TARGETS; }

    private static ProgramGraph movementGraph() {
        if (BASIC) return new ProgramGraph(List.of(
                node(0, BaseAbilityProgramDefinition.entryId(category), new JsonObject()),
                node(8, action("move_group"), ProgramStructureMovementNodes.defaults())),
                List.of(edge(0,"flow",8,"flow")));
        var center = CENTERS.get(TARGETS.getFirst().reference()); var point = new JsonObject();
        point.addProperty("dimension", player.level().dimension().identifier().toString());
        point.addProperty("x", center.x); point.addProperty("y", center.y); point.addProperty("z", center.z);
        var radius = new JsonObject(); radius.addProperty("value", 8);
        var variable = new JsonObject(); variable.addProperty("name", "group_targets"); variable.addProperty("type", ProgramValueTypes.STRUCTURE_SET.id().toString());
        var zero = new JsonObject(); zero.addProperty("value", 0);
        var offset = new JsonObject(); offset.addProperty("value", 3);
        var seconds = new JsonObject(); seconds.addProperty("value", 1);
        return new ProgramGraph(List.of(node(0, BaseAbilityProgramDefinition.entryId(category), new JsonObject()),
                node(1, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, point), node(2, CommonProgramNodeIds.FLOAT_CONSTANT, radius),
                node(3, CommonProgramNodeIds.STRUCTURES_AROUND, new JsonObject()), node(4, CommonProgramNodeIds.VARIABLE_SET, variable),
                node(5, CommonProgramNodeIds.VARIABLE_GET, variable), node(6, CommonProgramNodeIds.VECTOR_CONSTRUCT, new JsonObject()),
                node(7, CommonProgramNodeIds.FLOAT_CONSTANT, zero), node(9, CommonProgramNodeIds.FLOAT_CONSTANT, offset),
                node(8, action("move_group"), ProgramStructureMovementNodes.defaults()), node(10, CommonProgramNodeIds.FLOAT_CONSTANT, seconds)),
                List.of(edge(0,"flow",4,"flow"),edge(1,"position",3,"center"),edge(2,"value",3,"radius"),edge(3,"structures",4,"value"),
                        edge(4,"flow",8,"flow"),edge(5,"value",8,"structures"),edge(6,"vector",8,"displacement"),
                        edge(7,"value",6,"x"),edge(7,"value",6,"y"),edge(9,"value",6,"z"),edge(10,"value",8,"seconds")));
    }

    private static void startGraph() {
        if (BASIC) {
            var body = motion(TARGETS.getFirst());
            var center = body.pose().localToWorldPoint(body.localCenterOfMass());
            player.connection.teleport(center.x, center.y - player.getEyeHeight(), center.z - 5, 0, 0);
            var selected = ((ProgramTargetResolver) runtime).lookStructureTarget().orElseThrow();
            check(selected.equals(TARGETS.getFirst()), "basic graph did not look at the live whole structure");
        }
        var definition = AbilityProgramDefinitions.require(category); var graph = graph();
        var compiled = definition.compile(graph, Set.of(skill.getKey()));
        check(compiled.valid(), "group graph invalid: " + compiled.diagnostics());
        var layout = new HashMap<Integer, ProgramEditorLayout.NodePosition>();
        int[] order = TRANSLATION ? new int[]{0,1,2,3,4,5,7,9,6,10,8} : new int[]{0,1,2,3,4,5,6,7,11,8,9,10};
        for (int i = 0; i < order.length; i++) layout.put(order[i], new ProgramEditorLayout.NodePosition(i / 3 * 150, i % 3 * 90));
        editorProgram = new AbilityProgram(1, UUID.randomUUID(), (TRANSLATION ? "群组平移控制" : "群组姿态控制"), category, graph, new ProgramEditorLayout(layout));
        var transaction = ProgramActionTransaction.sequential(); frame = new ProgramExecutionFrame(transaction, runtime, null, player.level()::getGameTime);
        key = new ServerProgramScheduler.SessionKey(player.getUUID(), category, UUID.randomUUID(), 0);
        check(ServerProgramScheduler.start(player.level().getServer(), key, compiled.program(),
                id -> observe(id, definition.executors().find(id)), frame, 1000, 600, (_, result) -> {
                    termination = result;
                    try { if (result.kind() == ProgramSessionScheduler.TerminationKind.COMPLETED) { check(transaction.commit().successful(), "group transaction failed"); transaction.release(); } else transaction.abort(); }
                    catch (RuntimeException error) { settlementFailure = error; }
                    finally { ServerProgramExecution.closeOrientationCommands(frame); }
                }), "group scheduler rejected graph");
    }

    @SuppressWarnings("unchecked") private static ProgramNodeExecutor<Object> observe(Identifier id, ProgramNodeExecutor<?> actual) {
        if (actual == null) return null;
        return (context, config, inputs) -> {
            boolean groupAction = id.getPath().endsWith("_group");
            boolean first = groupAction && !STARTED.containsKey(context.nodeId());
            long now = player.level().getGameTime(); if (groupAction) STARTED.putIfAbsent(context.nodeId(), now);
            if (TRANSLATION && groupAction && !first && now - STARTED.get(context.nodeId()) > 5 && now - STARTED.get(context.nodeId()) < 18) {
                if (executingTargets().stream().allMatch(target -> motion(target).pose().localToWorldPoint(motion(target).localCenterOfMass()).z - CENTERS.get(target.reference()).z > .1)) spinSamples++;
            }
            if (context.nodeId() == 10 && !first && now - STARTED.get(10) > 15 && now - STARTED.get(10) < 45) {
                check(TARGETS.stream().allMatch(target -> motion(target).angularVelocity().z < -.25), "group members did not spin concurrently"); spinSamples++;
            }
            float before = cp();
            var step = ((ProgramNodeExecutor<Object>) actual).execute(context, config, inputs);
            if (first) {
                var expected = AbilitySystemServer.getSystem(player).quoteTimedOccupation(player, BASIC ? 15 : 30, skill) * SkillTuning.costMultiplier(player.level().getServer(), skill);
                check(Math.abs(before - cp() - expected) < .02, "group CP did not scale by two members: paid=" + (before - cp()) + " expected=" + expected);
                charged += before - cp();
            } else if (groupAction) check(Math.abs(before - cp()) < .001, "group polling charged twice");
            if (groupAction && step.directive() == ProgramNodeStep.Directive.CONTINUE) {
                COMPLETED.add(context.nodeId()); check(!occupied(), "completed group retained leases");
                check(step.outputs().containsKey("structures")
                        && new HashSet<>((List<?>) step.outputs().get("structures").value()).equals(new HashSet<>(executingTargets())),
                        "group did not pass its original selection to the next action");
                check(now > STARTED.get(context.nodeId()), "group completed without physics steps");
                for (var target : executingTargets()) {
                    var motion = motion(target);
                    if (TRANSLATION) {
                        check(now - STARTED.get(context.nodeId()) >= 20, "translation force window ended early");
                        check(motion.pose().localToWorldPoint(motion.localCenterOfMass()).z - CENTERS.get(target.reference()).z > .1, "member did not translate");
                        continue;
                    }
                    if (context.nodeId() == 8) check(angleError(motion.pose(), ROTATION) < .025, "member rotation did not reach target");
                    if (context.nodeId() == 9) check(motion.pose().localToWorldDirection(new Vec3(0,1,0)).distanceTo(new Vec3(0,1,0)) < .025, "member reset failed");
                    check(motion.angularVelocity().length() < .03, "group completed without braking");
                }
            }
            return step;
        };
    }

    private static void supportGravity() {
        for (var member : ServerStructureGroupControl.members(player)) {
            var state = motion(member.target()); var center = state.pose().localToWorldPoint(state.localCenterOfMass());
            Vec3 gravity = new Vec3(0, -9.81, 0);
            if (!SubLevelStructureRefs.isSubLevel(member.target().reference())) {
                var g = org.academy.internal.common.structure.sable.reflect.api.physics.config.dimension_physics.DimensionPhysicsData.getGravity(player.level()); gravity = new Vec3(g.x, g.y, g.z);
            }
            var force = CENTERS.get(member.target().reference()).subtract(center).scale(16).subtract(state.linearVelocity().scale(8)).subtract(gravity).scale(state.mass());
            check(StructureDirectControl.submit(player.level(), member.target().reference(), player, member.controlId(), new StructurePhysicsControl.Force(state.localCenterOfMass(), force)), "group gravity support rejected");
        }
    }

    private static ProgramGraph graph() {
        if (TRANSLATION) return movementGraph();
        var center = CENTERS.get(TARGETS.getFirst().reference()); var point = new JsonObject(); point.addProperty("dimension", player.level().dimension().identifier().toString());
        point.addProperty("x", center.x); point.addProperty("y", center.y); point.addProperty("z", center.z);
        var radius = new JsonObject(); radius.addProperty("value", 8);
        var variable = new JsonObject(); variable.addProperty("name", "group_targets"); variable.addProperty("type", ProgramValueTypes.STRUCTURE_SET.id().toString());
        var axis = new JsonObject(); axis.addProperty("x", 0); axis.addProperty("y", 0); axis.addProperty("z", 1);
        var angle = new JsonObject(); angle.addProperty("value", -.5);
        var spin = ProgramStructureOrientationNodes.spinDefaults(); spin.addProperty("duration_seconds", 2.5); spin.addProperty("angular_speed", -.8);
        return new ProgramGraph(List.of(node(0, BaseAbilityProgramDefinition.entryId(category), new JsonObject()),
                node(1, CommonProgramNodeIds.WORLD_POSITION_CONSTANT, point), node(2, CommonProgramNodeIds.FLOAT_CONSTANT, radius),
                node(3, CommonProgramNodeIds.STRUCTURES_AROUND, new JsonObject()), node(4, CommonProgramNodeIds.VARIABLE_SET, variable), node(5, CommonProgramNodeIds.VARIABLE_GET, variable),
                node(6, CommonProgramNodeIds.DIRECTION_CONSTANT, axis), node(7, CommonProgramNodeIds.FLOAT_CONSTANT, angle), node(11, CommonProgramNodeIds.STRUCTURE_POSE, new JsonObject()),
                node(8, action("rotate_group"), new JsonObject()), node(9, action("reset_group"), ProgramStructureOrientationNodes.resetDefaults()), node(10, action("spin_group"), spin)),
                List.of(edge(0,"flow",4,"flow"),edge(1,"position",3,"center"),edge(2,"value",3,"radius"),edge(3,"structures",4,"value"),
                        edge(4,"flow",8,"flow"),edge(5,"value",8,"structures"),edge(8,"structures",9,"structures"),edge(9,"structures",10,"structures"),
                        edge(6,"direction",11,"axis"),edge(7,"value",11,"angle_radians"),edge(11,"pose",8,"pose"),edge(6,"direction",10,"axis"),
                        edge(8,"flow",9,"flow"),edge(9,"flow",10,"flow")));
    }

    private static void finishCategory() {
        check(settlementFailure == null && termination.kind() == ProgramSessionScheduler.TerminationKind.COMPLETED, "group VM failed: " + termination + " " + settlementFailure);
        check(COMPLETED.equals(TRANSLATION ? List.of(8) : List.of(8,9,10)) && spinSamples >= (TRANSLATION ? 5 : 10), "missing concurrent group progress: " + COMPLETED + " samples=" + spinSamples);
        check(!occupied(), "completion leaked a lease");
        var displacements = TARGETS.stream().map(target -> {
            var state = motion(target);
            var delta = state.pose().localToWorldPoint(state.localCenterOfMass()).subtract(CENTERS.get(target.reference()));
            return List.of(delta.x, delta.y, delta.z);
        }).toList();
        if (TRANSLATION) check(NOZZLES.stream().allMatch(nozzle -> nozzle.activeTicks() == 0), "completed movement leaked nozzle activation");
        // Deletion during an active group must stop all remaining members without recreating anything.
        runtime = newRuntime(); var deletion = TRANSLATION ? runtime.moveStructures(TARGETS, moveRequest()) : runtime.spinStructures(TARGETS, spinRequest());
        check(deletion.result().status() == ProgramStructureOperationResult.Status.PENDING, "deletion setup rejected");
        check(StructureTransactions.discard(player.level().getServer(), TARGETS.get(1).reference()), "deletion fixture failed");
        check(runtime.structureGroupStatus(deletion.operationId()).result().status() == ProgramStructureOperationResult.Status.REJECTED && !occupied(), "deleted member retained group");
        var row = new LinkedHashMap<String,Object>(); row.put("category", category.toString()); row.put("backend", TARGETS.getFirst().reference().backend().toString());
        row.put("passed", true); row.put("members", BASIC ? 1 : 2); row.put("basic_two_node_graph_with_live_look_selection", BASIC); row.put("node_completion_order", List.copyOf(COMPLETED)); row.put(TRANSLATION ? "simultaneous_move_samples" : "simultaneous_spin_samples", spinSamples); row.put("graph_cp_paid", charged);
        row.put("preflight_cp_occupied_epoch_budget_checks", true); row.put("cancellation_successor_and_deletion_cleanup", true); RESULTS.add(row);
        if (TRANSLATION) { row.put("measured_displacements", displacements); row.put("movement_successor_nozzle_state_preserved", true); }
        AcademyCraft.LOGGER.info("STRUCTURE_GROUP_CASE_PASSED {}", row);
        cleanup();
        if (++categoryIndex == 3) { restore(); finished = true; write(true, ""); }
        else prepare();
    }

    private static boolean occupied() { return TARGETS.stream().anyMatch(target -> StructureDirectControl.occupied(target.reference(), target.runtimeId())); }
    private static StructureDynamics.State motion(ProgramStructureReference target) { return StructureDynamics.motion(player.level(), target.reference()).orElseThrow(); }
    private static double angleError(StructurePose a, StructurePose b) { return 2*Math.acos(Math.min(1,Math.abs(a.rotation().dot(b.rotation())))); }
    private static Identifier action(String suffix) { return Identifier.parse("academy:program/" + category.getPath() + "/action/structure/" + suffix); }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject config) { return new ProgramGraph.Node(id,type,1,config); }
    private static ProgramGraph.Edge edge(int from,String output,int to,String input) { return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from,output),new ProgramGraph.Endpoint(to,input)); }
    private static void cleanup() {
        if (runtime != null) runtime.closeStructureGroups();
        for (var nozzle : NOZZLES) nozzle.discard(); NOZZLES.clear();
        for (var target : TARGETS) if (StructureSpaces.resolve(player.level().getServer(), target.reference()).isPresent()) check(StructureTransactions.discard(player.level().getServer(),target.reference()), "group cleanup failed");
        for (int offset : List.of(0,4)) { player.level().setBlock(seed.offset(offset,-1,0),Blocks.AIR.defaultBlockState(),3); player.level().setBlock(seed.offset(offset,0,0),Blocks.AIR.defaultBlockState(),3); }
    }
    private static void restore() { player.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,0,0); player.setNoGravity(oldGravity); player.setGameMode(oldMode); }
    private static void write(boolean passed, String failure) {
        try { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",passed,"failure",failure,"cases",RESULTS,"actual_node_vm",true,"actual_save_execute_packets",false,"linear_gravity_support_only",!TRANSLATION,"movement_without_assistance",TRANSLATION))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Could not write group report",error); }
    }
    private static void fail(Throwable error) {
        if (failed) return; failed=true; AcademyCraft.LOGGER.error("STRUCTURE_GROUP_FAILED category={}",category,error);
        try { if (player!=null) { if (key!=null) ServerProgramScheduler.cancel(player.level().getServer(),key); cleanup(); restore(); } } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
        if (output!=null) write(false,error.toString());
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var client=Minecraft.getInstance();
        if (finished || failed) {
            if (!failed) try { if (!org.academy.internal.client.validation.StructureGroupEditorSmoke.tick(client,editorProgram)) return; }
            catch (RuntimeException error) { failed=true; write(false,error.toString()); }
            closing=true; client.stop(); return;
        }
        if (client.player==null || client.level==null) return;
        var directory=client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")) { fail(new IllegalStateException("Group fixture requires isolated run directory")); return; }
        output=directory.resolve(TRANSLATION ? "structure-group-move-result.json" : "structure-group-result.json");
        client.player.setNoGravity(true); client.player.setDeltaMovement(Vec3.ZERO); client.player.resetFallDistance();
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(client.getWindow().handle()); ready=true;
    }
    private static void check(boolean condition,String detail) { if (!condition) throw new IllegalStateException(detail); }
}
