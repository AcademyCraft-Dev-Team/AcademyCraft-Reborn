package org.academy.internal.common.ability.meltdowner.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class HomingBlast extends Skill {
    private static final int BEAM_COUNT = 24;
    private static final float DAMAGE = 4.0f;
    private static final float RANGE = 8.0f;

    public HomingBlast() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(200)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.SPREADING_BLAST)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_H)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.HOMING_BLAST + "_use";
        public static Config CONFIG = new Config();
        public static void onUse() { MisakaNetworkClient.sendPacket(ActivatePacket.INSTANCE); }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public HomingBlast.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.HOMING_BLAST.get().executeActive(player, (ctx, actualCost) -> AbilitySystemServer.registerContext(new Context(player)));
        }
    }

    public static final class Context extends ServerContext {
        private final List<HomingOrb> orbs = new ArrayList<>();
        private int ticks;
        private boolean ended;
        private static final int MAX_TICKS = 100;

        private Context(ServerPlayer player) {
            super(player);
            for (var i = 0; i < BEAM_COUNT; i++) {
                var orb = new LightOrb(player.level(), MAX_TICKS, 0.15f, null);
                orb.setPos(player.getEyePosition());
                var angle = 2 * Math.PI * i / BEAM_COUNT;
                var dir = new Vec3(Math.cos(angle) * 0.3, Math.sin(angle) * 0.3, 0);
                orb.setDeltaMovement(dir);
                player.level().addFreshEntity(orb);
                orbs.add(new HomingOrb(orb, player.level()));
            }
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive() || ticks >= MAX_TICKS) { end(); return; }

            if (ticks > 10 && ticks % 2 == 0) {
                for (var h : orbs) {
                    if (h.orb.isRemoved()) continue;
                    var orbPos = h.orb.position();
                    if (h.hasHomed) continue;

                    if (level() instanceof ServerLevel serverLevel) {
                        var box = new net.minecraft.world.phys.AABB(orbPos.add(-32, -32, -32), orbPos.add(32, 32, 32));
                        var targets = level().getEntitiesOfClass(LivingEntity.class,
                                box, e -> e != player && e.isAlive());
                        if (!targets.isEmpty()) {
                            var target = targets.getFirst();
                            var dir = target.position().subtract(orbPos).normalize().scale(0.5);
                            h.orb.setDeltaMovement(dir);
                            h.hasHomed = true;
                        } else {
                            var dir = h.orb.getDeltaMovement().normalize().scale(0.5);
                            h.orb.setDeltaMovement(dir);
                        }
                    }
                }

                for (var h : orbs) {
                    if (h.orb.isRemoved()) continue;
                    var orbPos = h.orb.position();
                    if (level() instanceof ServerLevel serverLevel) {
                        var hitTargets = level().getEntitiesOfClass(LivingEntity.class,
                                h.orb.getBoundingBox().inflate(0.5),
                                e -> e != player && e.isAlive());
                        for (var target : hitTargets) {
                            target.hurtServer(serverLevel, player.damageSources().magic(), DAMAGE);
                            h.orb.discard();
                        }
                    }
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            for (var h : orbs) { if (!h.orb.isRemoved()) h.orb.discard(); }
            unregister();
        }

        private static class HomingOrb {
            final LightOrb orb;
            final Level level;
            boolean hasHomed;
            HomingOrb(LightOrb orb, Level level) { this.orb = orb; this.level = level; }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);
        private ActivatePacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.HOMING_BLAST_ACTIVATE.get();
        }
    }
}
