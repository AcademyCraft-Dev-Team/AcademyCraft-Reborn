package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.UUID;

/** Disposable integrated-client regression through real player movement and network handling. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureSupportClientSmoke {
    private static final String WORLD = "Structure Support Smoke";
    private static boolean started, finished, completing;
    private static int waiting, ticks, grounded, idleSamples, liftSamples;
    private static double idleGap, liftGap, jumpHeight, walked;
    private static Vec3 walkStart;
    private static ClientInput originalInput;
    private static boolean originalPauseOnLostFocus;
    private static final FixtureInput fixtureInput = new FixtureInput();
    private static volatile UUID structureId;
    private static volatile int entityId = -1;
    private static volatile String failure;
    private static volatile boolean serverReady;
    private static BlockStructureEntity serverStructure;
    private static BlockStructureKineticHandle control;
    private static Path directory;
    private static final StringBuilder samples = new StringBuilder("tick,phase,client_y,platform_y,gap,grounded,velocity_x,velocity_y,velocity_z\n");

    private StructureSupportClientSmoke() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.structureSupportSmoke") || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            if (!started) prepare(mc);
            require(failure == null, failure);
            require(++waiting < 1000, "Support fixture timeout");
            if (!serverReady || !(mc.level.getEntity(entityId) instanceof BlockStructureEntity body)
                    || !body.getUUID().equals(structureId) || body.snapshot().blockCount() != 81) return;
            if (completing) return;
            if (++ticks <= 30) return; // Initial spawn and the one setup teleport must settle.
            int age = ticks - 30;
            var player = mc.player;
            double gap = player.getY() - body.getY() - 1;
            var velocity = player.getDeltaMovement();
            var phase = age <= 60 ? "IDLE" : age <= 80 ? "WALK" : age <= 110 ? "BRAKE"
                    : age <= 150 ? "JUMP" : "LIFT";
            samples.append(age).append(',').append(phase).append(',').append(player.getY()).append(',')
                    .append(body.getY()).append(',').append(gap).append(',').append(player.onGround()).append(',')
                    .append(velocity.x).append(',').append(velocity.y).append(',').append(velocity.z).append('\n');
            fixtureInput.forward = age >= 60 && age < 80;
            fixtureInput.jump = age == 110;
            if (age <= 60) {
                idleSamples++; if (player.onGround()) grounded++;
                idleGap = Math.max(idleGap, Math.abs(gap));
            }
            if (age == 60) walkStart = player.position();
            if (age == 90) walked = player.position().subtract(walkStart).horizontalDistance();
            if (age > 110 && age <= 150) jumpHeight = Math.max(jumpHeight, gap);
            if (age == 150) {
                var id = player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var actor = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                        require(actor != null, "Support actor left");
                        control.close();
                        control = BlockStructureKinetics.holdAt(serverStructure, actor,
                                serverStructure.position().add(0, 3, 0),
                                new BlockStructureKineticOptions(400, 0.12, 10, false, true), _ -> {});
                    } catch (Throwable error) { failure = error.toString(); }
                });
            }
            if (age > 170) { liftSamples++; liftGap = Math.max(liftGap, Math.abs(gap)); }
            if (age == 310) complete(mc);
        } catch (Throwable error) { finish(mc, error.toString()); }
    }

    private static void prepare(Minecraft mc) throws Exception {
        directory = mc.gameDirectory.toPath().toRealPath();
        var root = Path.of(System.getProperty("academy.structureSupportRoot")).toRealPath();
        require(directory.startsWith(root) && root.endsWith(Path.of("run", "structure-support")), "Unsafe support game directory");
        var server = mc.getSingleplayerServer();
        require(server != null && server.getWorldPath(LevelResource.ROOT).toRealPath()
                .equals(directory.resolve("saves").resolve(WORLD)), "Support fixture refuses unrelated world");
        require(!Files.exists(directory.resolve("structure-support-result.txt")), "Support result already exists");
        started = true; originalInput = mc.player.input; mc.player.input = fixtureInput;
        originalPauseOnLostFocus = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false;
        var id = mc.player.getUUID();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(id);
                require(player != null, "Missing real integrated player");
                var host = player.level();
                var cells = new ArrayList<BlockPos>();
                for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) {
                    var pos = new BlockPos(x, 200, z);
                    require(host.getBlockState(pos).isAir(), "Support fixture source is occupied");
                    cells.add(pos);
                }
                for (var pos : cells) host.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                var result = BlockStructureApi.capture(host, cells, BlockStructureCaptureOptions.defaults().withGravity(false));
                require(result.succeeded(), "Platform capture failed: " + result.status());
                serverStructure = (BlockStructureEntity) result.structure().orElseThrow();
                control = BlockStructureKinetics.holdAt(serverStructure, player, serverStructure.position(),
                        new BlockStructureKineticOptions(900, 0.12, 10, false, true), _ -> {});
                structureId = serverStructure.getUUID(); entityId = serverStructure.getId();
                player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = false; player.onUpdateAbilities();
                player.connection.teleport(8.5, 201, 7.0, 0, 10);
                serverReady = true;
            } catch (Throwable error) { failure = error.toString(); }
        });
    }

    private static void complete(Minecraft mc) {
        completing = true; fixtureInput.forward = false; fixtureInput.jump = false;
        String problem = idleGap > 0.08 || grounded < idleSamples * 0.9 ? "Unstable standing" :
                walked < 1.0 ? "Walking suppressed" : jumpHeight < 0.7 ? "Jump suppressed" :
                liftGap > 0.3 || liftSamples < 100 ? "Unstable moving support" : null;
        var name = "structure-support-" + ProcessHandle.current().pid() + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                _ -> mc.execute(() -> finish(mc, problem)));
    }

    private static void finish(Minecraft mc, String error) {
        if (finished) return;
        finished = true;
        if (mc.player != null && originalInput != null) mc.player.input = originalInput;
        if (started) mc.options.pauseOnLostFocus = originalPauseOnLostFocus;
        try {
            if (directory != null) {
                Files.writeString(directory.resolve("structure-support-samples.csv"), samples, StandardOpenOption.CREATE_NEW);
                Files.writeString(directory.resolve("structure-support-result.txt"), (error == null ? "PASSED\n" : "FAILED\nfailure=" + error + "\n")
                        + "real_integrated_player=true\ninput=synthetic_ClientInput_fixture\nactual_keyboard_actions=false\n"
                        + "blocks=81\nidle_samples=" + idleSamples + "\ngrounded=" + grounded + "\nidle_max_gap=" + idleGap
                        + "\nwalk_distance=" + walked + "\njump_height=" + jumpHeight + "\nlift_max_gap=" + liftGap
                        + "\nlift_samples=" + liftSamples + "\n", StandardOpenOption.CREATE_NEW);
            }
        } catch (Exception writing) { AcademyCraft.LOGGER.error("Cannot write support result", writing); }
        AcademyCraft.LOGGER.info("STRUCTURE_SUPPORT_FINISHED failure={} idle_gap={} walk={} jump={} lift_gap={}", error, idleGap, walked, jumpHeight, liftGap);
        mc.stop();
    }

    private static final class FixtureInput extends ClientInput {
        private boolean forward, jump;
        @Override public void tick() {
            keyPresses = new Input(forward, false, false, false, jump, false, false);
            moveVector = forward ? new Vec2(0, 1) : Vec2.ZERO;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
