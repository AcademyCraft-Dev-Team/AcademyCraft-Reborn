package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.transaction.StructureCellPayload;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.academy.internal.common.sublevel.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real plot ownership, inventories, body velocities and client projections in a fresh test copy. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureFractureSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureFractureSmoke");
    private static volatile boolean ready, done;
    private static volatile Path directory;
    private static int ticks, phase, clientSamples;
    private static ServerPlayer player;
    private static SubLevelRuntime runtime;
    private static SubLevelId parent;
    private static org.academy.api.common.structure.StructureRef originalParent;
    private static org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle nozzle;
    private static Set<SubLevelId> before;
    private static List<SubLevelId> children = List.of();
    private static Map<String, Object> result;

    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        directory = mc.gameDirectory.toPath();
        if (mc.level != null && mc.player != null) ready = true;
        if (!children.isEmpty() && mc.level != null
                && org.academy.internal.client.sublevel.SubLevelClientState.snapshots(mc.level).size() >= children.size()) clientSamples++;
        if (done) mc.stop();
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || done || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++ticks < 600, "fixture timeout");
            if (phase == 0) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setNoGravity(true);
                check(SubLevelServer.ensureForMaintenance(event.getServer()), "native maintenance unavailable");
                runtime = SubLevelServer.runtime(player.level()).orElseThrow();
                before = new HashSet<>(runtime.subLevels().stream().map(SubLevel::id).toList());
                var origin = player.blockPosition().offset(12, 0, 0).atY(player.level().getMaxY() - 30);
                player.level().getChunkAt(origin);
                player.connection.teleport(origin.getX() - 5, origin.getY() + 3, origin.getZ() - 5, 0, 0);
                for (int x = 0; x < 4; x++) {
                    check(player.level().getBlockState(origin.east(x)).isAir(), "occupied fixture site");
                    player.level().setBlock(origin.east(x), (x == 3 ? Blocks.CHEST : Blocks.IRON_BLOCK).defaultBlockState(), 3);
                }
                ((ChestBlockEntity) player.level().getBlockEntity(origin.east(3))).setItem(0, new ItemStack(Items.DIAMOND, 3));
                parent = SubLevelCaptureService.capture(player.level(), runtime,
                        List.of(origin, origin.east(), origin.east(2), origin.east(3))).id();
                var body = runtime.get(parent).orElseThrow();
                nozzle = new org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle(
                        org.academy.internal.common.world.entity.EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), player.level());
                originalParent = SubLevelStructureRefs.of(body, player.level().dimension().identifier(),
                        SubLevelActivationRegistry.find(parent).orElseThrow().generation());
                nozzle.attachToStructure(player.getUUID(), originalParent, new BlockPos(2, 0, 0), net.minecraft.core.Direction.UP);
                nozzle.markTemporary();
                check(player.level().addFreshEntity(nozzle), "fixture nozzle admission failed");
                check(LiveStructureProvenance.commitCapture(event.getServer(), SubLevelStructureRefs.of(body,
                        player.level().dimension().identifier(), 0), StructureProvenance.SkillCreation.resting(
                        player.getUUID(), AcademyCraft.academy("kinetic_throw")).capture(UUID.randomUUID(), player.level().getGameTime())), "mark failed");
                phase = 1;
            } else if (phase == 1 && ticks > 35) {
                var body = runtime.get(parent).orElseThrow();
                if (runtime.storage().loadedEntities(body.plot()).isEmpty()) return;
                runtime.universe().setVelocity(parent, new PhysicsVector(1, 0, 0), new PhysicsVector(0, 0.2, 0));
                var bridge = runtime.storage().worldPosition(body.plot(), new BlockPos(1, 0, 0));
                StructureCellPayload.write(runtime.storage().level(), bridge,
                        new StructureBlockData(Blocks.AIR.defaultBlockState(), null), SubLevelCaptureService.TRANSACTION_FLAGS);
                runtime.refreshGeometry(parent);
                if ("before_commit".equals(System.getProperty("academy.structureFractureFault"))) {
                    check(runtime.get(parent).isPresent() && !SubLevelFractures.pending(player.level(), parent), "precommit rollback lost source");
                    check(runtime.subLevels().stream().filter(it -> !before.contains(it.id()) && !it.id().equals(parent)).count() == 0,
                            "precommit rollback retained target owner");
                    phase = 2;
                } else phase = 3;
            } else if (phase == 2) {
                if (runtime.get(parent).orElseThrow().state() == SubLevelState.LOADING) return;
                runtime.refreshGeometry(parent);
                phase = 3;
            } else if (phase == 3) {
                check(runtime.get(parent).isEmpty(), "parent still owns copied cells");
                children = runtime.subLevels().stream().map(SubLevel::id).filter(id -> !before.contains(id)).toList();
                check(children.size() == 2, "expected two independent bodies");
                int cells = 0, diamonds = 0;
                var references = new ArrayList<String>();
                for (var id : children) {
                    check(!SubLevelFractures.pending(player.level(), id), "child still quarantined");
                    var child = runtime.get(id).orElseThrow();
                    check(runtime.universe().motion(id).isPresent(), "missing child physics");
                    var ref = SubLevelStructureRefs.of(child, player.level().dimension().identifier(), 0);
                    check(LiveStructureProvenance.metadata(event.getServer(), ref).orElseThrow().source()
                            == StructureProvenance.CreationKind.SKILL_FRAGMENT, "missing inherited provenance");
                    references.add(id.value().toString());
                    for (var cell : runtime.view(id).cells()) {
                        cells++;
                        var entity = runtime.storage().blockEntity(child.plot(), cell.position());
                        if (entity instanceof ChestBlockEntity chest) diamonds += chest.getItem(0).getCount();
                    }
                }
                check(cells == 3 && diamonds == 3, "inventory or block conservation failed");
                var mount = nozzle.structureMount().orElseThrow();
                check(!nozzle.isRemoved() && children.stream().anyMatch(id -> id.value().equals(mount.reference().id()))
                        && mount.offset().equals(new BlockPos(2, 0, 0)) && mount.face() == net.minecraft.core.Direction.UP
                        && player.getUUID().equals(nozzle.ownerUuid()) && nozzle.isTemporary() && nozzle.activeTicks() == 0,
                        "nozzle support did not follow its unique fragment without activation changes");
                nozzle.attachToStructure(player.getUUID(), originalParent, mount.offset(), mount.face());
                var oldMount = nozzle.structureMount().orElseThrow();
                nozzle.activate(7);
                check(!nozzle.rebindFragment(oldMount, UUID.randomUUID(), mount.reference())
                        && nozzle.structureMount().orElseThrow().equals(oldMount) && nozzle.activeTicks() == 7,
                        "wrong-owner remount changed the nozzle");
                check(nozzle.rebindFragment(oldMount, player.getUUID(), mount.reference()) && nozzle.activeTicks() == 7
                        && nozzle.isTemporary(), "valid remount changed activation or temporary status");
                nozzle.restoreActiveTicks(0);
                result = new LinkedHashMap<>(); result.put("passed", true); result.put("children", references);
                result.put("cells", cells); result.put("diamonds", diamonds);
                result.put("fault", System.getProperty("academy.structureFractureFault", "none"));
                result.put("actual_tcp_multiplayer", false); result.put("user_world_used", false);
                result.put("nozzle_fragment_rebind_and_owner_preserved", true);
                phase = 4;
            } else if (phase == 4 && clientSamples >= 8) {
                result.put("client_projection_samples", clientSamples);
                Files.writeString(directory.resolve("structure-fracture-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
                done = true;
            }
        } catch (Throwable failure) {
            AcademyCraft.LOGGER.error("Structure fracture smoke failed", failure);
            try { Files.writeString(directory.resolve("structure-fracture-result.json"),
                    new GsonBuilder().create().toJson(Map.of("passed", false, "error", failure.toString(), "phase", phase))); }
            catch (Exception ignored) {}
            done = true;
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
