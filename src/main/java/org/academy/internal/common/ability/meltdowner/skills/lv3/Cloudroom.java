package org.academy.internal.common.ability.meltdowner.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
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
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.Map;

public class Cloudroom extends Skill {
    private static final float RADIUS = 16.0f;
    private static final int TRAIL_LIFETIME = 30;
    private static final int TRAIL_INTERVAL_TICKS = 5;
    private static final int MAX_TRAILS_PER_TICK = 6;
    private static final int MAX_GLOBAL_TRAILS_PER_TICK = 16;
    private static final double MIN_TRAIL_DISTANCE_SQR = 0.05 * 0.05;

    public Cloudroom() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .dependsOn(Skills.LIGHT_SHIELD)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_U, InputConstants.PRESS, InputConstants.MOD_ALT))
                , ctx -> Client.onToggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.CLOUDROOM + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.CLOUDROOM.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Cloudroom.Client.Config getDefault() {
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
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();
        private static long trailBudgetTick = Long.MIN_VALUE;
        private static int trailsSpawnedThisTick;

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.CLOUDROOM.get();
            skill.toggle(player);
            if (!skill.isEnabled(player)) {
                var ctx = CONTEXT_MAP.remove(player);
                if (ctx != null) ctx.end();
                return;
            }
            if (CONTEXT_MAP.containsKey(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static boolean tryClaimTrailSlot(long gameTime) {
            if (trailBudgetTick != gameTime) {
                trailBudgetTick = gameTime;
                trailsSpawnedThisTick = 0;
            }
            if (trailsSpawnedThisTick >= MAX_GLOBAL_TRAILS_PER_TICK) return false;
            trailsSpawnedThisTick++;
            return true;
        }
    }

    public static final class Context extends ServerContext {
        private final Map<LivingEntity, Vec3> lastPositions = new HashMap<>();
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.CLOUDROOM.get();
            if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) {
                end();
                return;
            }
            if (!AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                    player.getUUID(), skill.getMaintenanceCost(skill.getLevel(player)), skill)) {
                if (skill.isEnabled(player)) skill.toggle(player);
                end();
                return;
            }

            var entities = level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(RADIUS),
                    e -> e != player && e.isAlive() && !e.isSpectator());

            var spawnedTrails = 0;
            for (var entity : entities) {
                var currentPos = entity.position();
                var lastPos = lastPositions.get(entity);
                if (lastPos != null
                        && lastPos.distanceToSqr(currentPos) >= MIN_TRAIL_DISTANCE_SQR
                        && Math.floorMod(entity.tickCount + entity.getId(), TRAIL_INTERVAL_TICKS) == 0
                        && spawnedTrails < MAX_TRAILS_PER_TICK
                        && Server.tryClaimTrailSlot(level().getGameTime())) {
                    var smoke = new Smoke(EntityTypes.SMOKE.get(), level());
                    smoke.setPos(currentPos);
                    smoke.size = 0.5f;
                    smoke.setLifetimeTicks(TRAIL_LIFETIME);
                    level().addFreshEntity(smoke);
                    spawnedTrails++;
                }
                lastPositions.put(entity, currentPos);
            }
            lastPositions.keySet().retainAll(entities);
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player);
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.CLOUDROOM_TOGGLE.get();
        }
    }
}
