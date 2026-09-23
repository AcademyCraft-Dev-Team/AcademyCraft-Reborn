package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.entitycontrol.MentalControlApi;
import org.academy.api.common.entitycontrol.MentalImmunity;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.control.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.skills.lv5.MindDestruction;
import org.misaka.MisakaNetworkClient;

import java.util.Set;
import java.util.function.Consumer;

/** Opt-in packet, server-tick and client health-sync smoke test in the isolated ability test world. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MindDestructionClientSmoke {
    private static final net.minecraft.resources.Identifier SOURCE = AcademyCraft.academy("mind_destruction_smoke");
    private static boolean opened, configured, finished;
    private static volatile boolean ready, passed;
    private static volatile Throwable failure;
    private static LivingEntity target;
    private static int ticks, total;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.mindDestructionSmoke") || finished) return;
        var mc = Minecraft.getInstance();
        if (failure != null) throw new IllegalStateException("Mind Destruction server smoke failed", failure);
        if (++total > 2400) throw new IllegalStateException("Mind Destruction smoke timed out");
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) {
                    if (widget instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getString().equals("Proceed to main menu")) {
                        button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                                new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                        break;
                    }
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        mc.player.setYRot(0); mc.player.yRotO = 0;
        mc.player.setXRot(8); mc.player.xRotO = 8;
        if (!configured) {
            configured = true;
            server(player -> {
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(player.level(), 0, 81, 178, Set.of(), 0, 8, false);
                for (int x = -2; x <= 2; x++) for (int z = 177; z <= 185; z++) {
                    player.level().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 81; y <= 84; y++) {
                        player.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                for (var old : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(12))) old.discard();
                var cow = new Cow(EntityTypes.COW, player.level());
                cow.setPos(0, 81, 183);
                cow.setNoAi(true);
                cow.setNoGravity(true);
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                cow.setHealth(20);
                player.level().addFreshEntity(cow);
                target = cow;
                MentalImmunity.set(cow, SOURCE, Component.literal("Smoke immunity"), true);
                var system = AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.MENTALOUT.get());
                system.setPlayerLevel(player.getUUID(), 5);
                var skill = Skills.MIND_DESTRUCTION.get();
                system.addPlayerSkill(player, skill.getKeyString());
                if (!skill.isEnabled(player)) skill.toggle(player);
                system.setPlayerBaseMaxCP(player.getUUID(), 1000);
                system.setPlayerAvailableCP(player.getUUID(), 1000);
                for (var kind : java.util.List.of(SyncTypes.SKILL_DATA, SyncTypes.ABILITY_CATEGORY, SyncTypes.CP_DATA)) {
                    system.schedulePlayerSync(player.getUUID(), kind);
                }
                ready = true;
            });
        }
        if (!ready) return;
        ticks++;
        if (ticks == 30) MisakaNetworkClient.send(new MindDestruction.UsePacket(MentaloutRequestGuard.nextClientSequence()));
        if (ticks == 56) server(_ -> {
            require(target.getHealth() < 20 && !MentalImmunity.isSuppressed(target), "First pulse damages without stripping");
            require(TemporalApi.get(target).effectiveScale(target, TemporalChannel.ENTITY) < 1, "First pulse starts slowdown");
        });
        if (ticks == 95) server(_ -> {
            require(target.isAlive() && target.getHealth() == 1, "Lethal pulse leaves one health");
            require(MentalControlApi.isMentalProtectionSuppressed(target), "Lethal pulse strips immunity");
            require(TemporalApi.get(target).effectiveScale(target, TemporalChannel.ENTITY) == 1, "Cast ending restores reaction speed");
            MentalImmunity.set(target, SOURCE, Component.empty(), true);
            require(!MentalImmunity.isImmune(target), "Passive immunity cannot register again");
        });
        if (ticks == 120) {
            var clientTarget = mc.level.getEntity(target.getId());
            require(clientTarget instanceof LivingEntity living && living.getHealth() == 1,
                    "Client receives the protected health value");
        }
        if (ticks == 245) server(_ -> {
            require(target.isAlive() && target.getHealth() == 1, "Terminated skill cannot deal later pulses");
            target.discard();
            require(!MentalImmunity.isSuppressed(target), "Leaving the world clears suppression");
            passed = true;
        });
        if (passed) {
            finished = true;
            System.out.println("[mind-destruction-smoke] PASSED: real cast packet, lethal protection, suppression, slowdown cleanup, health sync, lifecycle cleanup");
            mc.stop();
        }
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (Throwable error) { failure = error; }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
