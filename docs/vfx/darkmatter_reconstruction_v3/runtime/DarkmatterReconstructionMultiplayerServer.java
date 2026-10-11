package org.academy.desktop.grapheditor.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterRepair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Two actual TCP players; server-side successful gameplay calls produce every visual event. */
@EventBusSubscriber(modid = "academy")
public final class DarkmatterReconstructionMultiplayerServer {
    private static int tick = -1;
    private static int waiting;
    private static boolean finished;
    private static Path output() { return Path.of(System.getProperty("academy.reconstructionOutput")); }
    private static void write(String name, String body) {
        try { Files.createDirectories(output()); Files.writeString(output().resolve(name), body); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static void phase(String phase) {
        write("phase.txt", phase); System.out.println("[reconstruction-v3-multiplayer] " + phase + " tick=" + tick);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.reconstructionMultiplayerServer") || finished) return;
        var server = event.getServer();
        try {
            var caster = server.getPlayerList().getPlayerByName("VfxCaster");
            var observer = server.getPlayerList().getPlayerByName("VfxObserver");
            if (tick < 0) {
                if (++waiting > 12000) throw new IllegalStateException("two-client readiness timed out");
                if (caster == null || observer == null || !Files.exists(output().resolve("caster.ready"))
                        || !Files.exists(output().resolve("observer.ready"))) return;
                tick = 0;
                for (int x = -8; x <= 8; x++) for (int z = 3; z <= 17; z++) {
                    server.overworld().setBlock(new BlockPos(x, 64, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    for (int y = 65; y <= 71; y++) server.overworld().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                configure(caster); configure(observer);
                caster.setGameMode(GameType.CREATIVE); observer.setGameMode(GameType.SURVIVAL);
                observer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); observer.setHealth(40);
                caster.teleportTo(server.overworld(), 0, 65, 12, Set.of(), 180, 0, false);
                observer.teleportTo(server.overworld(), 0, 65, 8, Set.of(), 0, 0, false);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
                phase("baseline");
            }
            if (tick >= 460) {
                if (Files.exists(output().resolve("caster.done")) && Files.exists(output().resolve("observer.done"))) {
                    write("server-result.txt", "PASSED\nTwo independent TCP clients received successful production repair and disassembly events.\n");
                    finished = true; server.halt(false); return;
                }
                if (tick > 800) throw new IllegalStateException("client final validation timed out");
                tick++; return;
            }
            if (caster == null || observer == null) throw new IllegalStateException("a validation client disconnected at " + tick);
            if (tick == 45) {
                phase("repair_single"); repairEnabled(caster, true); refill(caster); caster.setHealth(caster.getMaxHealth() - .1f);
                require(DarkmatterRepair.Server.tryPulse(caster), "single actual healing pulse");
            }
            if (tick == 100) phase("repair_continuous");
            if (tick >= 100 && tick <= 180 && tick % 5 == 0) {
                refill(caster); caster.setHealth(1); require(DarkmatterRepair.Server.tryPulse(caster), "continuous actual healing pulse");
            }
            if (tick == 181) { caster.setHealth(caster.getMaxHealth()); repairEnabled(caster, false); phase("repair_stop"); }
            if (tick == 220) {
                phase("disassemble_player"); refill(caster); observer.invulnerableTime = 0;
                float before = observer.getHealth();
                require(DarkmatterDisassemble.Server.tryProgramAttack(caster, observer, 16, .01f, 0), "actual player hit");
                require(observer.getHealth() < before, "player hit caused actual damage, not only a valid skill attempt");
                require(observer.isAlive(), "target player remains alive");
            }
            if (tick == 260) {
                phase("disassemble_block"); refill(caster);
                var pos = new BlockPos(1, 66, 9); server.overworld().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                require(DarkmatterDisassemble.Server.tryProgramDestroyBlock(caster, pos, 16, 0), "actual block removed");
            }
            if (tick == 300) phase("simultaneous");
            if (tick >= 300 && tick <= 380 && tick % 5 == 0) {
                for (var actor : List.of(caster, observer)) {
                    refill(actor); repairEnabled(actor, true); actor.setHealth(1);
                    require(DarkmatterRepair.Server.tryPulse(actor), "simultaneous actual healing pulse");
                }
                observer.setHealth(observer.getMaxHealth()); observer.invulnerableTime = 0;
                observer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                observer.teleportTo(server.overworld(), 0, 65, 8, Set.of(), 0, 0, false);
                float before = observer.getHealth();
                require(DarkmatterDisassemble.Server.tryProgramAttack(caster, observer, 16, .01f, 0), "simultaneous actual hit");
                require(observer.isAlive() && observer.getHealth() < before,
                        "simultaneous target survives actual damage with ordinary visible health rows");
            }
            if (tick == 381) {
                repairEnabled(caster, false); repairEnabled(observer, false); phase("cleanup");
                caster.setHealth(caster.getMaxHealth()); observer.setHealth(observer.getMaxHealth());
            }
            tick++;
            if (tick == 460) phase("done");
        } catch (Throwable failure) {
            write("failure.txt", failure.toString()); finished = true; server.halt(false);
            throw new IllegalStateException("Reconstruction multiplayer failed", failure);
        }
    }
    private static void configure(ServerPlayer player) {
        player.getInventory().clearContent(); player.removeAllEffects(); player.setNoGravity(false); player.setAbsorptionAmount(0);
        var system = AbilitySystemServer.getSystem(player); var id = player.getUUID();
        system.setPlayerAbilityCategory(id, Skills.DARKMATTER_DISASSEMBLE.get().getCategory()); system.setPlayerLevel(id, 5);
        for (var skill : List.of(Skills.DARKMATTER_GENERATION.get(), Skills.DARKMATTER_SHAPING.get(),
                Skills.DARKMATTER_PHASE_TUNING.get(), Skills.DARKMATTER_DISASSEMBLE.get(), Skills.DARKMATTER_RADIATION.get(), Skills.DARKMATTER_REPAIR.get())) {
            system.addPlayerSkill(player, skill.getKeyString()); if (!skill.isEnabled(player)) skill.toggle(player);
            system.setPlayerSkillProficiency(id, skill, 0);
        }
        repairEnabled(player, false); refill(player);
        var data = system.getPlayerData(id); data.setAcademyMaxCp(2000); data.getCpData().setMaxCP(2000); data.getCpData().setAvailableCP(2000);
    }
    private static void refill(ServerPlayer p) {
        var manager = AbilitySystemServer.getSystem(p).getDarkmatterResourceManager(); manager.reconcile(p);
        require(manager.debugSetPools(p, 100, 0, 0, 0), "matter initialized"); manager.setAlphaPoints(p, 180);
    }
    private static void repairEnabled(ServerPlayer p, boolean enabled) {
        if (Skills.DARKMATTER_REPAIR.get().isEnabled(p) != enabled) Skills.DARKMATTER_REPAIR.get().toggle(p);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
