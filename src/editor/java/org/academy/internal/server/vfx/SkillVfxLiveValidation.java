package org.academy.internal.server.vfx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.vfx.SkillVfxService;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.entity.skill.Plasma;

/** Opt-in local test scene. Only enabled by the dedicated validation Gradle runs. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SkillVfxLiveValidation {
    private static final boolean ENABLED = Boolean.getBoolean("academy.vfxValidation.server");
    private static final List<Entity> EFFECTS = new ArrayList<>();
    private static int tick = -1;
    private static Plasma plasma;
    private SkillVfxLiveValidation() {}

    private static Path output() { return Path.of(System.getProperty("academy.vfxValidation.output")); }
    private static void write(String file, String text) {
        try { Files.createDirectories(output()); Files.writeString(output().resolve(file), text); }
        catch (IOException e) { throw new IllegalStateException("Cannot write VFX validation state", e); }
    }
    private static void phase(String phase) {
        write("phase.txt", phase);
        AcademyCraft.getLogger().info("VFX_LIVE_PHASE {} tick={} {}", phase, tick, SkillVfxRuntime.statistics());
    }
    private static void clearEffects() { EFFECTS.forEach(Entity::discard); EFFECTS.clear(); }
    private static void pose(ServerPlayer caster, ServerPlayer observer, float pitch, boolean backwards) {
        caster.connection.teleport(0, 65, 12, backwards ? 0 : 180, pitch);
        observer.connection.teleport(18, 72, 18, backwards ? -45 : 145, pitch);
    }
    private static void beams(ServerLevel level, int count, double z) {
        for (int i = 0; i < count; i++) {
            var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
            beam.setPos((i - count / 2.0) * 4, 72, z);
            beam.setYRot(180);
            beam.setBeamLength(45);
            beam.setContinuous(true);
            level.addFreshEntity(beam); EFFECTS.add(beam);
        }
    }
    private static Plasma plasma(ServerLevel level, ServerPlayer owner, Vec3 position) {
        var p = new Plasma(EntityTypes.PLASMA.get(), level);
        p.setPos(position); p.setOwnerEntityId(owner.getId());
        level.addFreshEntity(p); EFFECTS.add(p); return p;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED) return;
        MinecraftServer server = event.getServer();
        var caster = server.getPlayerList().getPlayerByName("VfxCaster");
        var observer = server.getPlayerList().getPlayerByName("VfxObserver");
        if (tick < 0) {
            if (caster == null || observer == null || !Files.exists(output().resolve("caster.ready"))
                    || !Files.exists(output().resolve("observer.ready"))) return;
            tick = 0;
            caster.setGameMode(GameType.SPECTATOR); observer.setGameMode(GameType.SPECTATOR);
            var level = server.overworld();
            for (int x = -24; x <= 24; x++) for (int z = -40; z <= 24; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 64, z), ((x / 4 + z / 4) & 1) == 0
                        ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
            }
            pose(caster, observer, -10, false);
            phase("baseline");
        }
        if (caster == null || observer == null) {
            clearEffects(); write("failure.txt", "A validation client disconnected at tick " + tick);
            server.halt(false); return;
        }
        var level = server.overworld();
        if (tick == 160) phase("shockwave");
        if (tick >= 160 && tick < 260 && tick % 4 == 0)
            SkillVfxService.shockwave(level, new Vec3(0, 69, -6), new Vec3(0, 1, 0), 12, 5);
        if (tick == 260) { beams(level, 4, -8); phase("beam"); }
        if (tick == 380) {
            clearEffects(); pose(caster, observer, -48, false);
            plasma = plasma(level, caster, new Vec3(0, 96, -16)); phase("plasma_charge");
        }
        if (tick >= 380 && tick < 660) plasma.setGatherProgress(Math.min(1f, (tick - 380) / 240f));
        if (tick == 660) {
            pose(caster, observer, -24, false);
            plasma.launch(caster.getUUID(), new Vec3(0, 80, -100), 1, 0, 0, 0, false, 0);
            phase("plasma_flight");
        }
        if (tick == 780) {
            clearEffects(); pose(caster, observer, -10, false);
            SkillVfxService.plasmaImpact(level, new Vec3(0, 72, -18), 20); phase("plasma_impact");
        }
        if (tick == 860) {
            clearEffects(); pose(caster, observer, 0, false); beams(level, 4, -190);
            for (int i = 0; i < 4; i++) {
                var p = plasma(level, caster, new Vec3((i - 1.5) * 10, 72, -185));
                p.launch(caster.getUUID(), p.position().add(0, 0, -100), 0.05, 0, 0, 0, false, 0);
            }
            phase("load_visible");
        }
        if (tick == 1020) { pose(caster, observer, 0, true); phase("load_offscreen"); }
        if (tick == 1180) { clearEffects(); pose(caster, observer, -10, false); phase("smoke"); }
        if (tick >= 1180 && tick < 1420 && tick % 2 == 0) {
            for (int i = 0; i < 8; i++)
                SkillVfxService.smoke(level, new Vec3((i - 3.5) * 2, 69 + (tick % 6), -6), 1.5f, 80);
        }
        if (tick == 1300) { pose(caster, observer, 0, true); phase("smoke_offscreen"); }
        if (tick == 1420) { pose(caster, observer, -10, false); phase("slash"); }
        if (tick >= 1420 && tick < 1660 && tick % 2 == 0) {
            for (int i = 0; i < 4; i++)
                SkillVfxService.slash(level, new Vec3((i - 1.5) * 4, 69, -6), -25 + i * 15, 180,
                        1.8f, tick % 4 == 0 ? 1 : -1, 4);
        }
        if (tick == 1540) { pose(caster, observer, 0, true); phase("slash_offscreen"); }
        if (tick == 1660) { clearEffects(); phase("cleanup"); }
        if (tick == 1720) { phase("done"); write("server-result.txt", SkillVfxRuntime.statistics().toString()); }
        if (tick == 1840) server.halt(false);
        tick++;
    }
}
