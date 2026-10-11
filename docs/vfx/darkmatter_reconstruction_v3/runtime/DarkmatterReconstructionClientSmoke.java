package org.academy.desktop.grapheditor.preview;

import com.google.gson.GsonBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.render.vfx.DarkmatterVfxClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterRepair;
import org.academy.internal.common.world.item.DarkmatterItemUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Opt-in integrated-server test. All photographed pulses originate in successful production skill calls. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DarkmatterReconstructionClientSmoke {
    private static boolean opened, configured, finished;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static int total, warmup, ticks, pendingScreenshots;
    private static int originalFov;
    private static LivingEntity target;
    private static final List<Cow> crowd = new ArrayList<>();
    private static final List<Map<String, Object>> captures = new ArrayList<>();
    private static final Set<ActiveEffect> continuousGraphs = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<String> observed = new java.util.HashSet<>();
    private static float minimumContinuousProgress = 1;
    private static boolean armoredRepairAttachmentObserved;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.reconstructionSmoke") || finished) return;
        if (failure != null) throw new IllegalStateException("Reconstruction v3 scenario failed", failure);
        if (++total > 2800) throw new IllegalStateException("Reconstruction v3 scenario timed out");
        var mc = Minecraft.getInstance();
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
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.yBodyRot = 0; mc.player.yBodyRotO = 0;
        mc.player.setXRot(0); mc.player.xRotO = 0;
        if (!configured) {
            configured = true; originalFov = mc.options.fov().get(); mc.options.fov().set(48);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setNoGravity(false); server(DarkmatterReconstructionClientSmoke::configure);
        }
        if (!ready || ++warmup < 200) return;
        ticks++;
        for (var effect : VfxGraphManager.INSTANCE.activeEffects()) if (!effect.isStopped()) observed.add(effect.assetKey());
        if (ticks == 10) capture("baseline");
        if (ticks == 20) server(p -> {
            refill(p); repairEnabled(p, true); p.setHealth(p.getMaxHealth() - .1f);
            require(DarkmatterRepair.Server.tryPulse(p), "single repair performs actual healing");
            require(p.getHealth() == p.getMaxHealth(), "single pulse reaches full health without follow-up work");
        });
        if (ticks >= 21 && ticks <= 33) capture("repair_single/frame_" + ticks);
        if (ticks == 42) require(find("darkmatter_repair") == null, "single repair cleans up after its lease");
        if (ticks >= 55 && ticks <= 95 && ticks % 5 == 0) server(p -> {
            refill(p); p.setHealth(1); require(DarkmatterRepair.Server.tryPulse(p), "continuous repair performs actual healing");
        });
        if (ticks >= 69 && ticks <= 95) {
            var active = find("darkmatter_repair");
            require(active != null, "continuous successful repair remains attached");
            continuousGraphs.add(active);
            minimumContinuousProgress = Math.min(minimumContinuousProgress, active.effect().floatParameter("progress", -1));
            if (ticks % 3 == 0) capture("repair_continuous/frame_" + ticks);
        }
        if (ticks == 97) server(p -> { repairEnabled(p, false); p.setHealth(p.getMaxHealth()); });
        if (ticks >= 98 && ticks <= 108) capture("repair_stop/frame_" + ticks);
        if (ticks == 112) {
            require(continuousGraphs.size() == 1, "continuous packets retain exactly one graph instance");
            require(minimumContinuousProgress >= .60f, "continuous packets retain the assembled shape without progress reset");
            require(find("darkmatter_repair") == null, "stopped repair releases the graph");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (ticks == 120) server(p -> {
            refill(p); p.setItemSlot(EquipmentSlot.MAINHAND, damagedTool()); repairEnabled(p, true);
            require(DarkmatterRepair.Server.tryPulse(p), "held native item integrity is repaired");
        });
        if (ticks >= 122 && ticks <= 131) capture("repair_first_person_hand/frame_" + ticks);
        if (ticks == 136) server(p -> {
            p.getInventory().clearContent(); p.getInventory().setItem(10, damagedTool()); refill(p);
        });
        if (ticks == 150) {
            require(find("darkmatter_repair") == null, "inventory-only repair produces no world patches"); capture("repair_inventory_only");
            server(p -> { repairEnabled(p, false); p.getInventory().clearContent(); });
        }
        if (ticks == 155) server(p -> {
            target.setPos(0, 81, 178); target.setDeltaMovement(Vec3.ZERO);
        });
        if (ticks == 160) server(p -> {
            target.setHealth(target.getMaxHealth()); target.invulnerableTime = 0; refill(p);
            require(DarkmatterDisassemble.Server.tryProgramAttack(p, target, 16, .01f, 0), "nonterminal disassembly deals actual damage");
            require(target.isAlive(), "surviving model must stay intact");
        });
        if (ticks >= 161 && ticks <= 176) capture("disassemble_living/frame_" + ticks);
        if (ticks == 183) server(p -> command(p, "time set midnight"));
        if (ticks == 187) server(p -> {
            refill(p); repairEnabled(p, true); p.setHealth(p.getMaxHealth() - .1f);
            require(DarkmatterRepair.Server.tryPulse(p), "night repair performs actual healing");
        });
        if (ticks >= 188 && ticks <= 196) capture("repair_night/frame_" + ticks);
        if (ticks == 194) server(p -> {
            target.invulnerableTime = 0; refill(p);
            require(DarkmatterDisassemble.Server.tryProgramAttack(p, target, 16, .01f, 0), "night hit succeeds");
        });
        if (ticks >= 196 && ticks <= 205) capture("disassemble_night/frame_" + ticks);
        if (ticks == 213) server(p -> { repairEnabled(p, false); command(p, "time set noon"); });
        if (ticks == 225) server(p -> {
            target.setHealth(1); target.invulnerableTime = 0; refill(p);
            require(DarkmatterDisassemble.Server.tryProgramAttack(p, target, 16, 2, 0), "terminal disassembly succeeds");
            require(!target.isAlive(), "terminal target really died");
        });
        if (ticks >= 227 && ticks <= 240) capture("disassemble_terminal/frame_" + ticks);
        if (ticks == 252) server(p -> {
            var position = new BlockPos(0, 82, 178); refill(p);
            p.level().setBlock(position, Blocks.STONE.defaultBlockState(), 3);
            require(DarkmatterDisassemble.Server.tryProgramDestroyBlock(p, position, 16, 0), "single block removal succeeds");
            require(p.level().getBlockState(position).isAir(), "white block residual follows actual removal");
        });
        if (ticks >= 254 && ticks <= 267) capture("disassemble_block/frame_" + ticks);
        if (ticks == 280) {
            mc.options.fov().set(70);
            server(p -> {
                for (int i = 0; i < 12; i++) {
                    var cow = cow(p, (i % 4 - 1.5) * 1.8, 178 + i / 4 * 2.0);
                    crowd.add(cow);
                }
            });
        }
        if (ticks == 303) server(p -> {
            refill(p);
            for (var cow : crowd) require(DarkmatterDisassemble.Server.tryProgramAttack(p, cow, 16, .01f, 0), "tracked crowd hit succeeds");
        });
        if (ticks >= 306 && ticks <= 314) {
            require(DarkmatterVfxClient.activeCount() <= 64, "multi-subject admission stays bounded");
            capture("tracked_subjects/frame_" + ticks);
        }
        if (ticks == 329) server(p -> {
            for (var cow : crowd) cow.discard(); crowd.clear(); refill(p);
            for (int x = -3; x <= 3; x++) for (int y = 81; y <= 83; y++) {
                var at = new BlockPos(x, y, 179); p.level().setBlock(at, Blocks.STONE.defaultBlockState(), 3);
                require(DarkmatterDisassemble.Server.tryProgramDestroyBlock(p, at, 16, 0), "batch block removal succeeds");
            }
        });
        if (ticks >= 332 && ticks <= 340) capture("batch_blocks/frame_" + ticks);
        if (ticks == 345) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.options.fov().set(48);
            server(p -> {
                repairEnabled(p, false); p.getInventory().clearContent(); p.setHealth(p.getMaxHealth());
                var armor = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
                armor.setDamageValue(40); p.setItemSlot(EquipmentSlot.CHEST, armor);
            });
        }
        if (ticks == 349) capture("repair_iron_chestplate/before");
        if (ticks == 350) server(p -> {
            refill(p); repairEnabled(p, true); p.setHealth(p.getMaxHealth() - .1f);
            require(DarkmatterRepair.Server.tryPulse(p), "armored BODY pulse performs actual healing");
            require(p.getHealth() == p.getMaxHealth(), "armored BODY pulse restores actual missing health");
            require(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 40,
                    "BODY healing leaves ordinary iron armor damage unchanged");
        });
        if (ticks >= 351 && ticks <= 363) capture("repair_iron_chestplate/frame_" + ticks);
        if (ticks == 354) {
            require(mc.player.getItemBySlot(EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.IRON_CHESTPLATE),
                    "client has the real iron chestplate during the repair attachment");
            require(find("darkmatter_repair") != null, "production repair graph exists on the armored player");
            armoredRepairAttachmentObserved = true;
        }
        if (ticks == 369) server(p -> {
            require(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 40,
                    "ordinary armor remains outside the native equipment repair scope");
            repairEnabled(p, false); p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); p.setHealth(p.getMaxHealth());
        });
        if (ticks == 385) {
            require(armoredRepairAttachmentObserved && mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                    "armored repair was observed and its fixture equipment was cleared");
            require(DarkmatterVfxClient.activeCount() == 0, "all reconstruction attachments release");
            require(find("darkmatter_repair") == null && find("darkmatter_disassemble") == null, "no reconstruction graph leaks");
            require(observed.stream().anyMatch(s -> s.endsWith("darkmatter_repair"))
                    && observed.stream().anyMatch(s -> s.endsWith("darkmatter_disassemble")), "both production graphs were observed");
            capture("cleared");
        }
        if (ticks >= 395 && pendingScreenshots == 0) {
            try {
                Files.writeString(output().resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "passed", true, "continuousGraphInstances", continuousGraphs.size(),
                        "minimumContinuousProgress", minimumContinuousProgress, "captures", captures,
                        "armoredRepairAttachmentObserved", armoredRepairAttachmentObserved,
                        "networkScope", "one real client plus integrated server; twelve tracked cows are not twelve clients")));
            } catch (Exception e) { throw new IllegalStateException(e); }
            mc.options.fov().set(originalFov); finished = true;
            System.out.println("[reconstruction-v3-client] PASSED"); mc.stop();
        }
    }

    private static void configure(ServerPlayer p) {
        for (int x = -12; x <= 12; x++) for (int z = 169; z <= 194; z++) {
            p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for (int y = 81; y <= 94; y++) p.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (var mob : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(100))) mob.discard();
        for (var item : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(100))) item.discard();
        p.setGameMode(GameType.CREATIVE); p.setNoGravity(false); p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
        p.getInventory().clearContent(); p.removeAllEffects(); p.setHealth(p.getMaxHealth());
        var system = AbilitySystemServer.getSystem(p); var id = p.getUUID();
        system.setPlayerAbilityCategory(id, Skills.DARKMATTER_DISASSEMBLE.get().getCategory()); system.setPlayerLevel(id, 5);
        for (var skill : List.of(Skills.DARKMATTER_GENERATION.get(), Skills.DARKMATTER_SHAPING.get(),
                Skills.DARKMATTER_PHASE_TUNING.get(), Skills.DARKMATTER_DISASSEMBLE.get(), Skills.DARKMATTER_RADIATION.get(), Skills.DARKMATTER_REPAIR.get())) {
            system.addPlayerSkill(p, skill.getKeyString()); if (!skill.isEnabled(p)) skill.toggle(p);
            system.setPlayerSkillProficiency(id, skill, 0);
        }
        repairEnabled(p, false);
        var data = system.getPlayerData(id); data.setAcademyMaxCp(2000); data.getCpData().setMaxCP(2000); data.getCpData().setAvailableCP(2000);
        refill(p); target = cow(p, 8, 181); command(p, "time set noon"); command(p, "weather clear"); ready = true;
    }
    private static Cow cow(ServerPlayer p, double x, double z) {
        var cow = new Cow(net.minecraft.world.entity.EntityTypes.COW, p.level());
        cow.setPos(x, 81, z); cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); cow.setHealth(1000); p.level().addFreshEntity(cow); return cow;
    }
    private static ItemStack damagedTool() {
        var stack = new ItemStack(org.academy.internal.common.world.item.Items.DARKMATTER_TOOL.get());
        DarkmatterItemUtil.setIntegrity(stack, 0); return stack;
    }
    private static void repairEnabled(ServerPlayer p, boolean enabled) {
        if (Skills.DARKMATTER_REPAIR.get().isEnabled(p) != enabled) Skills.DARKMATTER_REPAIR.get().toggle(p);
    }
    private static void refill(ServerPlayer p) {
        var manager = AbilitySystemServer.getSystem(p).getDarkmatterResourceManager(); manager.reconcile(p);
        require(manager.debugSetPools(p, 100, 0, 0, 0), "fixture matter initialized"); manager.setAlphaPoints(p, 180);
    }
    private static ActiveEffect find(String key) {
        return VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> !e.isStopped() && e.assetKey().endsWith(key)).findFirst().orElse(null);
    }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable e) { failure = e; }
        });
    }
    private static void command(ServerPlayer p, String text) {
        var server = p.level().getServer(); server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), text);
    }
    private static void require(boolean condition, String text) {
        if (!condition) throw new IllegalStateException(text);
        System.out.println("[reconstruction-v3-client] " + text);
    }
    private static Path output() { return Path.of(System.getProperty("academy.reconstructionOutput")); }
    private static void capture(String name) {
        var graphs = VfxGraphManager.INSTANCE.activeEffects().stream().filter(e -> !e.isStopped() &&
                (e.assetKey().endsWith("darkmatter_repair") || e.assetKey().endsWith("darkmatter_disassemble")))
                .map(e -> Map.of("asset", e.assetKey(), "time", e.effect().floatParameter("time", -1),
                        "progress", e.effect().floatParameter("progress", -1), "slots", e.effect().buffer().count())).toList();
        var chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
        var row = Map.<String, Object>of("file", name + ".png", "tick", ticks, "graphs", graphs,
                "chestItem", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(chest.getItem()).toString(),
                "chestDamage", chest.getDamageValue());
        pendingScreenshots++;
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                var path = output().resolve(name + ".png"); Files.createDirectories(path.getParent()); image.writeToFile(path);
                captures.add(row); pendingScreenshots--;
            } catch (Exception e) { failure = e; }
        });
    }
}
