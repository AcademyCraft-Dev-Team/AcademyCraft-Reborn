package org.academy.internal.common.ability.meltdowner.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.api.client.render.effect.ParticleEmitter;
import org.academy.internal.client.renderer.effect.EMFieldEffectWrapper;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
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

public class ChainFusion extends Skill {
    public ChainFusion() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL5)
                .cpCost(150)
                .iterationTicks(60)
                .maxStacks(1)
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(EMFieldEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_U)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.CHAIN_FUSION + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.sendPacket(ActivatePacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            EMFieldEffectWrapper.INSTANCE.ensureActive();
            var emitter = ParticleEffectWrapper.INSTANCE.createEmitter(
                    (float) p.getX(), (float) p.getEyeY(), (float) p.getZ());
            emitter.setColor(1.0f, 0.4f, 0.05f);
            emitter.setSpreadMode(ParticleEmitter.SpreadMode.CONE, 30f);
            emitter.setEmissionDirection((float) p.getLookAngle().x,
                    (float) p.getLookAngle().y, (float) p.getLookAngle().z);
            emitter.setEmissionRate(15);
            emitter.setLifetime(0.7f, 0.3f);
            emitter.setVelocity(1.0f, 0.3f);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ChainFusion.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(ActivatePacket p) {
            var player = p.getPacketListener().getPlayer();
            Skills.CHAIN_FUSION.get().executeActive(player, (ctx, c) ->
                    AbilitySystemServer.registerContext(new Context(player)));
        }
    }

    public static final class Context extends ServerContext {
        private static final int ORB_LIFETIME = 80;
        private static final int CHAIN_DELAY = 15;
        private static final int MAX_CHAIN_DEPTH = 5;
        private static final float CHAIN_RADIUS = 5.0f;
        private static final float CHAIN_DAMAGE = 10.0f;
        private static final float INITIAL_DAMAGE = 15.0f;

        private final LightOrb orb;
        private final Set<Integer> chainedEntities = new HashSet<>();
        private final Map<Integer, Integer> chainQueue = new HashMap<>();
        private int ticks;
        private int chainDepth;
        private boolean ended;

        private Context(ServerPlayer p) {
            super(p);
            var eye = p.getEyePosition();
            orb = new LightOrb(p.level(), ORB_LIFETIME, 0.8f, null);
            orb.setPos(eye.add(p.getLookAngle().scale(2)));
            orb.setDeltaMovement(p.getLookAngle().scale(0.5));
            p.level().addFreshEntity(orb);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre ev) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive() || ticks >= ORB_LIFETIME || orb.isRemoved()) {
                end();
                return;
            }

            if (!(level() instanceof ServerLevel sl)) return;

            if (ticks < 15) {
                return;
            }

            if (ticks % 10 == 0) {
                chainFrom(sl, orb.position(), INITIAL_DAMAGE);
            }

            searchChainTargets(sl);

            processChainQueue(sl);
        }

        private void searchChainTargets(ServerLevel sl) {
            if (chainDepth >= MAX_CHAIN_DEPTH) return;

            var targets = sl.getEntitiesOfClass(LivingEntity.class,
                    orb.getBoundingBox().inflate(CHAIN_RADIUS),
                    e -> e != player && e.isAlive() && !chainedEntities.contains(e.getId()));

            for (var target : targets) {
                chainedEntities.add(target.getId());
                chainQueue.put(target.getId(), CHAIN_DELAY);
                chainDepth++;
                spawnChainMarker(sl, target.position());

                if (chainDepth >= MAX_CHAIN_DEPTH) break;
            }
        }

        private void processChainQueue(ServerLevel sl) {
            var it = chainQueue.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                var remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    var entityId = entry.getKey();
                    var entity = level().getEntity(entityId);
                    if (entity instanceof LivingEntity target && target.isAlive()) {
                        chainFrom(sl, target.position(), CHAIN_DAMAGE);
                        target.hurtServer(sl, sl.damageSources().magic(), CHAIN_DAMAGE);
                    }
                    it.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }

        private void chainFrom(ServerLevel sl, Vec3 pos, float damage) {
            var nearby = sl.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(
                            pos.x - CHAIN_RADIUS, pos.y - CHAIN_RADIUS, pos.z - CHAIN_RADIUS,
                            pos.x + CHAIN_RADIUS, pos.y + CHAIN_RADIUS, pos.z + CHAIN_RADIUS),
                    e -> e != player && e.isAlive() && !chainedEntities.contains(e.getId()));

            for (var target : nearby) {
                chainedEntities.add(target.getId());
                chainQueue.put(target.getId(), CHAIN_DELAY);
                target.hurtServer(sl, sl.damageSources().magic(), damage);
                spawnChainMarker(sl, target.position());
                chainDepth++;
                if (chainDepth >= MAX_CHAIN_DEPTH) break;
            }
        }

        private void spawnChainMarker(ServerLevel sl, Vec3 pos) {
            var marker = new LightOrb(sl, 20, 0.4f, null);
            marker.setPos(pos.x, pos.y + 1.0, pos.z);
            marker.setColor(1.0f, 0.6f, 0.1f);
            sl.addFreshEntity(marker);
        }

        private void end() {
            if (ended) return;
            ended = true;
            if (!orb.isRemoved()) orb.discard();
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.CHAIN_FUSION_ACTIVATE.get();
        }
    }
}
