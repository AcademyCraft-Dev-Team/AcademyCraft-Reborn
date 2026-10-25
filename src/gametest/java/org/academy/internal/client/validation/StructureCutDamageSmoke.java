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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramDirection;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.charge.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeCatalog;
import org.academy.internal.common.ability.aeromanip.program.ServerAeromanipProgramRuntime;
import org.academy.internal.common.ability.aeromanip.skills.lv3.LaminarCutter;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.academy.internal.common.structure.upgrade.damage.DamageDomain;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.nio.file.Files;
import java.util.*;

/** Real content, protected writes and the actual precision runtime in isolated client worlds. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureCutDamageSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureCutDamageSmoke");
    private static final List<String> CASES = List.of("instant", "half_cobweb", "half_stone", "full_stone",
            "full_iron", "full_bedrock", "fire", "cancel_fire", "rotated_stone", "rotated_partial_gap", "rotated_partial_hit", "hollow", "cancel", "replace",
            "loot", "reentry", "locked", "simulate", "no_drops", "mixed_width", "chest_preserved",
            "shared_cast", "program_vertical", "program_horizontal", "disabled", "change_budget", "entity_blocked", "entity_clear");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final BlockPos CELL = BlockPos.ZERO.above();
    private static volatile boolean ready, done, failed, closing;
    private static java.nio.file.Path directory;
    private static ServerPlayer player;
    private static BlockPos seed;
    private static UUID capture, lock;
    private static StructureRef reference;
    private static StructureHandle handle;
    private static DamageDomain domain;
    private static Vec3 center, start, end, right;
    private static int scenario, phase, ticks, age, breaks, loot;
    private static String name() { return CASES.get(scenario); }
    private static AeromanipChargeTier tier() {
        return name().equals("instant") || name().startsWith("entity") ? AeromanipChargeTier.INSTANT
                : name().startsWith("half") ? AeromanipChargeTier.HALF : AeromanipChargeTier.FULL;
    }
    private static StructureDamage.ActiveActor authority() {
        return new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(), player));
    }
    private static StructureCutDamage.Request request() {
        return new StructureCutDamage.Request(start, end, right, name().contains("gap") || name().equals("hollow") ? .15 : 4,
                name().contains("gap") ? .05 : .55,
                (state, view, pos) -> LaminarCutter.Server.canCutBlock(tier(), state, view, pos),
                name().equals("no_drops") ? StructureDamage.DropPolicy.NO_DROPS : StructureDamage.DropPolicy.EXISTING_SKILL_RULES,
                name().equals("simulate"));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || done || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 4000, "fixture deadline"); ticks++;
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                var system = AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.AEROMANIP.get());
                system.setPlayerLevel(player.getUUID(), 5);
                system.addPlayerSkill(player, Skills.LAMINAR_CUTTER.get().getKeyString());
                system.setPlayerSkillProficiency(player.getUUID(), Skills.LAMINAR_CUTTER.get(), 3000);
                if (!Skills.LAMINAR_CUTTER.get().isEnabled(player)) system.toggleSkill(player.getUUID(), Skills.LAMINAR_CUTTER.get().getKeyString());
                system.setPlayerBaseMaxCP(player.getUUID(), 100000);
                system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
                system.getAeromanipResourceManager().onPlayerLogin(player);
            }
            player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if (phase == 0) {
                seed = new BlockPos(140 + scenario * 24, 210, 220);
                player.connection.teleport(seed.getX() - 5, seed.getY(), seed.getZ(), 0, 0);
                for (var pos : BlockPos.betweenClosed(seed.offset(-8,-8,-8), seed.offset(8,8,8))) {
                    player.level().getChunkAt(pos); player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                phase = 1; ticks = 0; return;
            }
            if (phase == 1) {
                if (ticks < 25) return;
                player.level().setBlock(seed, Blocks.IRON_BLOCK.defaultBlockState(), 2);
                player.level().setBlock(seed.above(), Blocks.IRON_BLOCK.defaultBlockState(), 2);
                var cells = new ArrayList<>(List.of(seed, seed.above()));
                if (name().equals("hollow")) {
                    cells.add(seed.above().south(2));
                    player.level().setBlock(seed.above().south(2), Blocks.IRON_BLOCK.defaultBlockState(), 2);
                }
                var result = StructureTransactions.capture(player.level(), new StructureCaptureRequest(
                        cells, 16, 8, StructureCaptureRequest.ALLOW, true));
                check(result.succeeded() || result.pending(), "capture rejected: " + result);
                capture = result.transactionId(); phase = 2; ticks = 0; return;
            }
            var result = StructureTransactions.poll(event.getServer(), capture).orElseThrow();
            if (result.pending()) return;
            check(result.succeeded(), "capture failed: " + result);
            reference = result.structure().orElseThrow();
            handle = StructureSpaces.resolve(event.getServer(), reference).orElseThrow();
            center = handle.pose().localToWorldPoint(new Vec3(.5,1.5,.5));
            domain = StructureBackendRouter.damageDomains(player.level(), new AABB(center, center).inflate(5), 32).stream()
                    .filter(candidate -> candidate.handle().reference().equals(reference)).findFirst().orElseThrow();
            var state = name().equals("half_cobweb") ? Blocks.COBWEB.defaultBlockState()
                    : name().equals("full_iron") ? Blocks.IRON_BLOCK.defaultBlockState()
                    : name().equals("full_bedrock") ? Blocks.BEDROCK.defaultBlockState()
                    : name().contains("fire") ? Blocks.FIRE.defaultBlockState()
                    : name().contains("partial") ? Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 5)
                    : name().equals("chest_preserved") ? Blocks.CHEST.defaultBlockState() : Blocks.STONE.defaultBlockState();
            domain.storage().setBlock(domain.position(CELL), state, 2); domain.changed(); domain.flush();
            if (name().equals("hollow")) {
                domain.storage().setBlock(domain.position(CELL.south(2)), Blocks.STONE.defaultBlockState(), 2);
                domain.changed(); domain.flush();
            }
            if (name().contains("partial")) check(LaminarCutter.Server.canCutBlock(tier(), state,
                    domain.storage(), domain.position(CELL)), "partial-shape fixture must be cuttable");
            if (name().equals("chest_preserved")) ((net.minecraft.world.Container) domain.storage().getBlockEntity(domain.position(CELL)))
                    .setItem(0, new ItemStack(Items.DIAMOND, 19));
            if (name().startsWith("rotated")) rotate();
            center = handle.pose().localToWorldPoint(new Vec3(.5,1.5,.5));
            var a = new Vec3(-2, name().contains("gap") ? 1.85 : 1.5,
                    name().equals("hollow") ? 1.5 : name().equals("entity_clear") ? 2.5 : .5);
            var b = a.add(5,0,0);
            start = handle.pose().localToWorldPoint(a); end = handle.pose().localToWorldPoint(b);
            right = handle.pose().localToWorldDirection(new Vec3(0,0,1));
            breaks = loot = 0;
            if (name().equals("locked")) {
                lock = UUID.randomUUID(); PayloadIsolation.lock(domain.storage(), List.of(domain.position(CELL)), lock);
            }
            if (name().equals("mixed_width")) {
                player.level().setBlock(seed.offset(0,1,4), Blocks.STONE.defaultBlockState(), 2);
                player.level().setBlock(seed.offset(0,3,0), Blocks.STONE.defaultBlockState(), 2);
            }
            if (name().startsWith("program")) {
                player.level().setBlock(seed.offset(0,4,0), Blocks.STONE.defaultBlockState(), 2);
                player.level().setBlock(seed.offset(0,1,3), Blocks.STONE.defaultBlockState(), 2);
                right = name().endsWith("vertical") ? new Vec3(0,1,0) : new Vec3(0,0,1);
            }
            DestroyBlocksSetting.setDestroyBlocksEnabled(player, !name().equals("disabled"));
            if (name().equals("change_budget")) { checkBudget(); finish(); return; }
            if (name().startsWith("entity")) {
                var pig = net.minecraft.world.entity.EntityTypes.PIG.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                check(pig != null, "test target creation failed");
                pig.setNoAi(true); pig.setNoGravity(true);
                pig.setPos(end.x, end.y - pig.getBbHeight() * .5, end.z);
                check(player.level().addFreshEntity(pig), "test target spawn failed");
                float health = pig.getHealth();
                try { cast(); check((pig.getHealth() < health) == name().equals("entity_clear"), "structure entity occlusion mismatch"); }
                finally { pig.discard(); }
            } else if (name().startsWith("program") || Set.of("shared_cast", "disabled", "instant").contains(name())) cast();
            else {
                var trace = StructureDamages.service().cut(authority(), request());
                check(name().equals("locked") ? trace.status() == StructureOperation.Status.BLOCKED
                        : trace.status() == StructureOperation.Status.COMPLETED, "cut result: " + trace);
            }
            verify(); finish();
        } catch (Throwable failure) {
            failed = true; AcademyCraft.LOGGER.error("STRUCTURE_CUT_SMOKE_FAILED {}", name(), failure); write(failure.toString());
        }
    }

    private static void cast() throws Exception {
        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAvailableCP(player.getUUID(), 100000);
        system.setPlayerCurrMP(player.getUUID(), system.getAeromanipResourceManager().getCapacity(player));
        var direction = end.subtract(start).normalize();
        if (name().startsWith("program")) {
            var action = new ServerAeromanipProgramRuntime(player).laminarCut(
                    new ProgramWorldPosition(player.level().dimension().identifier(), start.x, start.y, start.z),
                    new ProgramDirection(direction.x, direction.y, direction.z), 1, tier(), 1,
                    new ProgramDirection(right.x, right.y, right.z), AeromanipProgramNodeCatalog.BladePlaneMode.DIRECTION);
            action.validate(); action.apply();
        } else check(LaminarCutter.Server.tryProgramCast(player, start, direction, 5, 1, 0, tier(), right), "shared skill rejected");
    }

    private static void verify() {
        boolean preserved = Set.of("instant", "half_stone", "full_iron", "full_bedrock", "rotated_partial_gap", "hollow",
                "cancel", "cancel_fire", "replace", "locked", "simulate", "chest_preserved", "disabled", "entity_blocked", "entity_clear").contains(name());
        check(domain.storage().getBlockState(domain.position(CELL)).isAir() != preserved, "unexpected target state");
        check(domain.storage().getBlockState(domain.position(BlockPos.ZERO)).is(Blocks.IRON_BLOCK), "hard support was cut");
        if (name().equals("replace")) check(domain.storage().getBlockState(domain.position(CELL)).is(Blocks.GOLD_BLOCK), "callback replacement destroyed");
        if (name().equals("hollow")) check(domain.storage().getBlockState(domain.position(CELL.south(2))).is(Blocks.STONE), "hollow far side cut");
        var items = player.level().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(8));
        if (preserved || name().equals("no_drops") || name().equals("fire")) check(items.isEmpty(), "unexpected drops");
        if (name().equals("loot")) check(items.stream().filter(e -> e.getItem().is(Items.EMERALD))
                .mapToInt(e -> e.getItem().getCount()).sum() == 2, "event output lost or duplicated");
        if (name().equals("full_stone") || name().equals("rotated_stone") || name().equals("shared_cast")) {
            check(items.stream().filter(e -> e.getItem().is(Items.COBBLESTONE)).mapToInt(e -> e.getItem().getCount()).sum() == 1, "stone drop conservation");
        }
        if (name().equals("simulate")) check(breaks == 0 && loot == 0, "simulation emitted events");
        if (name().equals("chest_preserved")) check(((net.minecraft.world.Container) domain.storage().getBlockEntity(domain.position(CELL)))
                .getItem(0).getCount() == 19, "uncut inventory changed");
        if (name().equals("mixed_width")) {
            check(player.level().getBlockState(seed.offset(0,1,4)).isAir(), "width edge missed");
            check(player.level().getBlockState(seed.offset(0,3,0)).is(Blocks.STONE), "thickness inflated");
        }
        if (name().startsWith("program")) {
            check(player.level().getBlockState(seed.offset(0,4,0)).isAir() == name().endsWith("vertical"), "program vertical plane mismatch");
            check(player.level().getBlockState(seed.offset(0,1,3)).isAir() == name().endsWith("horizontal"), "program horizontal plane mismatch");
        }
    }

    private static void checkBudget() {
        for (var pos : BlockPos.betweenClosed(seed.offset(-5,-5,-5), seed.offset(5,5,5))) {
            if (!pos.equals(seed) && !pos.equals(seed.above())) player.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        start = Vec3.atCenterOf(seed.west(6)); end = Vec3.atCenterOf(seed.east(6)); right = new Vec3(0,0,1);
        var wide = new StructureCutDamage.Request(start,end,right,5.5,5.5, request().materials(),StructureDamage.DropPolicy.NO_DROPS,false);
        var trace = StructureDamages.service().cut(authority(),wide);
        check(trace.status() == StructureOperation.Status.BLOCKED && trace.receipt().broken() == 1024, "cut mutation budget: " + trace);
        // A cut and a beam share the player's remaining 2,048-write tick allowance.
        var again = StructureDamages.service().cut(authority(),wide);
        check(again.receipt().broken() == 306, "remaining volume was lost: " + again);
        for (var pos : BlockPos.betweenClosed(seed.offset(-5,-5,-5), seed.offset(5,5,5))) {
            if (!pos.equals(seed) && !pos.equals(seed.above())) player.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        var beam = StructureDamages.service().path(authority(),new StructurePathDamage.Request(start,end,5.5f,3,
                StructureDamage.DropPolicy.NO_DROPS,false,false,false,false,false,null));
        check(beam.status() == StructureOperation.Status.BLOCKED && beam.receipt().broken() == 718, "cut/path tick budget bypass: " + beam);
    }

    private static void rotate() {
        var rotation = new org.joml.Quaterniond().rotateXYZ(.3,.7,.2);
        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) {
            check(org.academy.internal.common.sublevel.SubLevelServer.runtime(player.level()).orElseThrow()
                    .rotateTo(new org.academy.internal.common.sublevel.SubLevelId(reference.id()),new StructurePose(Vec3.ZERO,rotation)), "rotation failed");
        } else org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(player.level())
                .getSubLevel(reference.id()).logicalPose().orientation().set(rotation);
    }
    @SubscribeEvent public static void breaking(BreakBlockEvent event) {
        if (!ENABLED || phase != 2 || domain == null || StructureContexts.current().isEmpty()) return;
        breaks++;
        check(event.getLevel() == player.level() && Vec3.atCenterOf(event.getPos()).distanceTo(center) < 8, "private protection address");
        if (name().equals("cancel") || name().equals("cancel_fire")) event.setCanceled(true);
        if (name().equals("replace")) domain.storage().setBlock(domain.position(CELL), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        if (name().equals("reentry")) check(StructureDamages.service().cut(authority(),request()).status()
                == StructureOperation.Status.CONFLICT, "reentrant cut accepted");
    }
    @SubscribeEvent public static void drops(BlockDropsEvent event) {
        if (!ENABLED || phase != 2 || domain == null || StructureContexts.current().isEmpty()) return;
        loot++;
        if (name().equals("loot")) event.getDrops().getFirst().setItem(new ItemStack(Items.EMERALD,2));
    }
    private static void finish() {
        RESULTS.add(Map.of("case",name(),"passed",true,"backend",reference.backend().toString(),"break_events",breaks,"loot_events",loot));
        AcademyCraft.LOGGER.info("STRUCTURE_CUT_SMOKE_PASS {}",name());
        if (lock != null) PayloadIsolation.release(player.level().getServer(),lock);
        lock=null; domain=null; handle=null; reference=null; scenario++; phase=0; ticks=0;
        if (scenario == CASES.size()) { done=true; write(""); }
    }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("structure-cut-damage-result.json"),new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("passed",!failed,"failure",failure,"cases",RESULTS,"ticks",age))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Cut report failed",error); }
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft=Minecraft.getInstance();
        if (done || failed) { closing=true; minecraft.stop(); return; }
        if (minecraft.player==null || minecraft.level==null) return;
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory=minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated world required");
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle()); ready=true;
    }
    private static void check(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }
}
