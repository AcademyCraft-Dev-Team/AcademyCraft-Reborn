package org.academy.internal.common.structure.upgrade.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.util.*;
import java.util.function.Consumer;

/** Called by the shared teleport client fixture after its own checks, before its normal exit. */
public final class KineticFlightSmoke {
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static int scenario, phase, age, wallOffset;
    private static BlockPos seed;
    private static UUID capture;
    private static StructureRef source;
    private static Vec3 start;
    private static long launchedAt;
    private static long captureReadyAt = -1;
    private static int[] baseline = new int[3];
    private static boolean observed, oldDestruction;
    private static Consumer<BreakBlockEvent> protection;
    private static boolean callbackTeleportTried, callbackTeleportSucceeded, callbackVelocityChecked;
    private static net.minecraft.world.entity.animal.cow.Cow entityTarget;
    private static org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle nozzle;
    private static final UUID OFFLINE_NOZZLE_OWNER = UUID.nameUUIDFromBytes("kinetic-split-offline-nozzle".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private KineticFlightSmoke() {}

    public static boolean tick(ServerPlayer player) {
        if (scenario >= 7) return true;
        check(++age < 600, "kinetic fixture timeout " + scenario + "/" + phase);
        var host = player.level();
        if (phase == 0) {
            if (scenario == 0) { oldDestruction = DestroyBlocksSetting.isDestroyBlocksEnabled(player); DestroyBlocksSetting.setDestroyBlocksEnabled(player, true); }
            seed = player.blockPosition().offset(8, 0, 30 + scenario * 20).atY(host.getMaxY() - 80);
            wallOffset = scenario == 2 ? 12 : scenario == 6 ? 6 : 8;
            callbackTeleportTried = callbackTeleportSucceeded = callbackVelocityChecked = false;
            for (var pos : BlockPos.betweenClosed(seed.offset(-3, -12, -3), seed.offset(88, 5, 4))) {
                host.getChunkAt(pos); check(host.getBlockState(pos).isAir(), "kinetic arena occupied " + pos);
            }
            baseline = itemCounts(player);
            host.setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            var cells = new ArrayList<BlockPos>(); cells.add(seed);
            if (scenario == 2) {
                // Removing the low-order center separates the two surviving arms, forcing
                // family discovery instead of accepting a two-cell/no-split special case.
                for (var cell : List.of(seed.west(), seed.east(), seed.east(2), seed.east(3), seed.south(), seed.south(2))) {
                    host.setBlock(cell, Blocks.IRON_BLOCK.defaultBlockState(), 3); cells.add(cell);
                }
                host.setBlock(seed.south(3), Blocks.CHEST.defaultBlockState(), 3);
                ((ChestBlockEntity)host.getBlockEntity(seed.south(3))).setItem(0, new ItemStack(Items.DIAMOND, 3)); cells.add(seed.south(3));
            }
            if (scenario > 0 && scenario < 4) {
                for (var pos : BlockPos.betweenClosed(seed.offset(wallOffset, -5, -2), seed.offset(wallOffset, 3, 3)))
                    host.setBlock(pos, (scenario == 2 ? Blocks.BEDROCK : Blocks.STONE).defaultBlockState(), 3);
            }
            if (scenario == 3) {
                protection = event -> { if (event.getLevel() == host && event.getPos().getX() == seed.getX()+8) event.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(protection);
            }
            if (scenario == 6) {
                for (var pos : BlockPos.betweenClosed(seed.offset(6,-5,-2),seed.offset(7,3,3))) host.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
                for (var pos : BlockPos.betweenClosed(seed.offset(-2,-3,17),seed.offset(12,3,24))) {
                    host.getChunkAt(pos); check(host.getBlockState(pos).isAir(), "occupied callback teleport destination");
                }
                protection = event -> {
                    if (callbackTeleportTried || event.getLevel() != host || event.getPos().getX() != seed.getX()+6) return;
                    callbackTeleportTried = true;
                    var body = StructureSpaces.resolve(host.getServer(),source).orElseThrow();
                    var motion = StructureDynamics.motion(host,source).orElseThrow();
                    callbackTeleportSucceeded = StructureTeleports.teleport(host,player,new StructureTeleports.Request(source,
                            body.runtimeId(),motion.pose().localToWorldPoint(motion.localCenterOfMass()).add(0,0,20),null))
                            == StructureTeleports.Result.TELEPORTED;
                };
                NeoForge.EVENT_BUS.addListener(protection);
            }
            var request = new StructureCaptureRequest(cells, 8, 8, StructureCaptureRequest.ALLOW, true);
            // The hard-impact case also covers externally created/unmarked Sable structures.
            if (scenario != 2) request = request.withSkillCreation(player.getUUID(), AcademyCraft.academy("kinetic_throw"));
            var result = StructureTransactions.capture(host, request);
            check(result.pending() || result.succeeded(), "kinetic capture rejected " + result);
            capture = result.transactionId(); phase = 1; return false;
        }
        if (phase == 1) {
            var result = StructureTransactions.poll(host.getServer(), capture).orElseThrow();
            if (result.pending()) return false;
            check(result.succeeded(), "kinetic capture failed " + result);
            source = result.structure().orElseThrow();
            var motion = StructureDynamics.motion(host, source).orElse(null);
            if (motion == null) return false;
            if (captureReadyAt < 0) { captureReadyAt = host.getGameTime(); return false; }
            if (host.getGameTime() - captureReadyAt < 3) return false;
            start = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            if (scenario == 2) {
                var offset = new BlockPos(1,0,3);
                check(StructureSpaces.resolve(host.getServer(), source).orElseThrow().world().read(offset).state().is(Blocks.CHEST),
                        "fixture nozzle must be attached to the chest arm's real local cell");
                nozzle = new org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle(
                        org.academy.internal.common.world.entity.EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), host);
                nozzle.attachToStructure(OFFLINE_NOZZLE_OWNER, source, offset, net.minecraft.core.Direction.UP);
                nozzle.markTemporary(); check(host.addFreshEntity(nozzle), "split nozzle admission failed");
            }
            var token = UUID.randomUUID();
            check(StructureDirectControl.acquire(host, source, player, token) == StructureDirectControl.Status.ACTIVE,
                    "flight admission fixture failed to acquire owner control");
            check(StructureProjectileFlight.canObserveKinetic(player, source), "owner lease incorrectly blocks read-only flight admission");
            check(!StructureProjectileFlight.observeKinetic(player, source, 0), "observer took a body still under direct control");
            check(StructureDirectControl.validate(host, source, player, token) == StructureDirectControl.Status.ACTIVE,
                    "refused observer withdrew the original owner control");
            StructureDirectControl.release(source, token);
            check(StructureBackendRouter.launch(host, source, new Vec3(1,0,0), 75), "kinetic launch rejected");
            check(StructureProjectileFlight.observeKinetic(player, source, 0), "kinetic enhancement refused");
            launchedAt = host.getGameTime(); observed = StructureProjectileFlight.active(host.getServer(), source);
            if (scenario >= 4) {
                entityTarget = net.minecraft.world.entity.EntityTypes.COW.create(host, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                check(entityTarget != null, "entity collision target creation failed");
                entityTarget.snapTo(scenario == 4 ? start.x+8 : scenario == 6 ? start.x+3.75 : start.x,
                        start.y-.5, scenario == 4 ? start.z : start.z+10);
                host.getChunkAt(entityTarget.blockPosition());
                entityTarget.setNoGravity(true); entityTarget.setNoAi(true); host.addFreshEntity(entityTarget);
            }
            check(observed, "flight immediately disappeared");
            if (scenario == 5) {
                for (var pos : BlockPos.betweenClosed(seed.offset(-2,-3,17), seed.offset(7,3,24))) {
                    host.getChunkAt(pos); check(host.getBlockState(pos).isAir(), "occupied projectile teleport destination");
                }
                var before = StructureDynamics.motion(host, source).orElseThrow();
                var body = StructureSpaces.resolve(host.getServer(), source).orElseThrow();
                check(StructureTeleports.teleport(host, player, new StructureTeleports.Request(source, body.runtimeId(), start.add(0,0,20), null))
                        == StructureTeleports.Result.TELEPORTED, "in-flight relocation failed");
                check(!StructureProjectileFlight.active(host.getServer(), source), "in-flight relocation did not revoke enhancement");
                check(StructureDynamics.motion(host, source).orElseThrow().linearVelocity().distanceTo(before.linearVelocity()) < 1E-6,
                        "flight cancellation changed post-teleport velocity");
            }
            phase = 2; return false;
        }
        if (phase == 2) {
            if (scenario == 6 && callbackTeleportSucceeded && !callbackVelocityChecked) {
                check(!StructureProjectileFlight.active(host.getServer(),source), "callback teleport retained projectile enhancement");
                check(StructureDynamics.motion(host,source).orElseThrow().linearVelocity().x > 60,
                        "path rejection stopped the newly teleported projectile");
                callbackVelocityChecked = true;
            }
            if (host.getGameTime() - launchedAt < 35 || StructureProjectileFlight.active(host.getServer(), source)) return false;
            var handle = StructureSpaces.resolve(host.getServer(), source).orElse(null);
            int sourceCells = handle == null ? 0 : handle.world().summarize(4096).orElseThrow().counts().values().stream().mapToInt(Integer::intValue).sum();
            var counts = itemCounts(player);
            int iron = counts[0]-baseline[0], chest = counts[1]-baseline[1], diamonds = counts[2]-baseline[2];
            if (scenario <= 1) {
                check(sourceCells == 1 && iron == 0, "non-contact flight exploded or lost source");
                var motion = StructureDynamics.motion(host, source).orElseThrow();
                var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                check(center.x-start.x >= 64, "flight failed to pass 64 blocks " + center.subtract(start));
                check(motion.linearVelocity().x > 60, "natural range expiry braked inertia");
                if (scenario == 1) check(host.getBlockState(seed.offset(8,-1,0)).isAir(), "breakable wall blocked flight");
            } else if (scenario == 2) {
                check(Integer.getInteger("academy.structure.projectileSourceChanges",128) == 2, "strict split case requires sourceChanges=2");
                check(sourceCells == 0 && iron == 7 && chest == 1 && diamonds == 3,
                        "hard-contact content conservation failed cells="+sourceCells+" iron="+iron+" chest="+chest+" diamonds="+diamonds);
                check(host.getBlockState(seed.offset(wallOffset,0,0)).is(Blocks.BEDROCK), "hard obstacle was destroyed");
                check(org.academy.internal.common.structure.upgrade.damage.LiveStructureDamage.sourceDescendantWitnesses(host, source) > 0,
                        "T-shaped multi-slice source did not produce any verified child activation");
                int remaining = 0;
                for (var member : org.academy.internal.common.structure.upgrade.damage.LiveStructureDamage.sourceFamilyWitnesses(host, source)) {
                    var nativeId = org.academy.internal.common.sublevel.SubLevelStructureRefs.id(member);
                    if (nativeId.isPresent()) {
                        var runtime = org.academy.internal.common.sublevel.SubLevelServer.runtime(host).orElseThrow();
                        var body = runtime.get(nativeId.orElseThrow()).orElse(null);
                        if (body != null) remaining += org.academy.internal.common.sublevel.PlotView
                                .capture(runtime.storage(), body.plot(), body.localBounds()).cells().size();
                    } else {
                        var live = org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance.liveReference(host.getServer(), member);
                        if (live.isPresent()) remaining += StructureSpaces.resolve(host.getServer(), live.orElseThrow()).orElseThrow()
                                .world().summarize(4096).orElseThrow().counts().values().stream().mapToInt(Integer::intValue).sum();
                    }
                }
                check(remaining == 0, "source family retained orphan content after reported completion: " + remaining);
                check(nozzle != null && nozzle.structureMount().isPresent()
                                && !nozzle.structureMount().orElseThrow().reference().id().equals(source.id()),
                        "split arm nozzle retained the retired parent identity");
                check(OFFLINE_NOZZLE_OWNER.equals(nozzle.ownerUuid()) && nozzle.activeTicks() == 0 && nozzle.isTemporary(),
                        "split nozzle changed ownership, temporary status or activation");
            } else if (scenario == 3) {
                check(sourceCells == 1 && iron == 0, "protection denial exploded or consumed source");
                check(host.getBlockState(seed.offset(8,-1,0)).is(Blocks.STONE), "protection bypassed");
            } else if (scenario == 4) {
                check(sourceCells == 0 && iron == 1, "entity contact did not consume/drop source exactly once");
                check(entityTarget != null && entityTarget.getHealth() < entityTarget.getMaxHealth(), "entity contact did not hurt target");
            } else {
                check(sourceCells == 1 && iron == 0, "instant relocation exploded or consumed source");
                check(entityTarget != null && entityTarget.getHealth() == entityTarget.getMaxHealth(), "projectile attacked the nonexistent teleport path");
                if (scenario == 6) {
                    check(callbackTeleportTried && callbackTeleportSucceeded && callbackVelocityChecked, "synchronous break callback teleport was not exercised");
                    check(host.getBlockState(seed.offset(6,-1,0)).is(Blocks.STONE)
                                    && host.getBlockState(seed.offset(7,-1,0)).is(Blocks.STONE),
                            "sweep continued breaking original-path blocks after callback teleport");
                }
            }
            var details = new LinkedHashMap<String,Object>(); details.put("passed",true); details.put("source_cells",sourceCells);
            details.put("iron_drops",iron); details.put("chest_drops",chest); details.put("diamond_drops",diamonds);
            details.put("preexisting_item_counts", List.of(baseline[0], baseline[1], baseline[2]));
            if (scenario == 2) {
                details.put("actual_child_witnesses", org.academy.internal.common.structure.upgrade.damage.LiveStructureDamage.sourceDescendantWitnesses(host, source));
                details.put("split_nozzle_mount_rebound_without_activation",true);
            }
            RESULTS.put(List.of("no_collision_range_expiry", "breakable_wall_penetration", "hard_contact_explosion_conservation", "protected_wall_no_explosion", "actual_entity_contact_explosion", "instant_relocation_no_swept_attack", "break_callback_teleport_aborts_current_sweep").get(scenario),details);
            if (protection != null) { NeoForge.EVENT_BUS.unregister(protection); protection = null; }
            if (handle != null) check(StructureTransactions.discard(host.getServer(), source), "kinetic cleanup discard failed");
            if (entityTarget != null) { entityTarget.discard(); entityTarget = null; }
            if (nozzle != null) { nozzle.discard(); nozzle = null; }
            for (var pos : BlockPos.betweenClosed(seed.offset(wallOffset,-5,-2),seed.offset(wallOffset,3,3))) host.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            if (scenario == 6) for (var pos : BlockPos.betweenClosed(seed.offset(7,-5,-2),seed.offset(7,3,3))) host.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            scenario++; phase = 0; age = 0; captureReadyAt = -1;
            if (scenario == 7) DestroyBlocksSetting.setDestroyBlocksEnabled(player, oldDestruction);
        }
        return scenario >= 7;
    }

    public static Map<String,Object> results() { return Map.copyOf(RESULTS); }
    private static int[] itemCounts(ServerPlayer player) {
        // The r5 saved entities proved a chest at source z+5.817 after ordinary item drift;
        // the old +5 edge missed it. Cases are 20 blocks apart, so this 18-wide window
        // retains separate arenas; baseline subtraction also excludes existing same items.
        var arena = new AABB(Vec3.atLowerCornerOf(seed.offset(-4,-32,-8)), Vec3.atLowerCornerOf(seed.offset(22,6,10)));
        int[] counts = new int[3];
        for (var item : player.level().getEntitiesOfClass(ItemEntity.class, arena)) {
            if (item.getItem().is(Items.IRON_BLOCK)) counts[0] += item.getItem().getCount();
            if (item.getItem().is(Items.CHEST)) counts[1] += item.getItem().getCount();
            if (item.getItem().is(Items.DIAMOND)) counts[2] += item.getItem().getCount();
        }
        return counts;
    }
    public static void cleanup(ServerPlayer player) {
        if (protection != null) { NeoForge.EVENT_BUS.unregister(protection); protection = null; }
        if (nozzle != null) { nozzle.discard(); nozzle = null; }
        DestroyBlocksSetting.setDestroyBlocksEnabled(player, oldDestruction);
    }
    private static void check(boolean value, String detail) { if (!value) throw new IllegalStateException(detail); }
}
