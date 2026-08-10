package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;

public class SpacialExcision extends Skill {
    public SpacialExcision() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(0)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.AREA_TELEPORT_START)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_ALT))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.SPACIAL_EXCISION + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public SpacialExcision.Client.Config getDefault() {
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
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        @SubscribePacket
        public static void handle(ActivatePacket p) {
            var player = p.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player)) return;
            Skills.SPACIAL_EXCISION.get().executeActive(player,
                    ctx -> {
                        var maxCP = AbilitySystemServer.getSystem(player).getPlayerMaxCP(player.getUUID());
                        return maxCP * (ctx.milestone() >= 1 ? 0.9f : 1.0f);
                    },
                    (ctx, actualCost) -> {
                        var context = new Context(player, ctx.milestone());
                        ACTIVE.put(player, context);
                        AbilitySystemServer.registerContext(context);
                    });
        }
    }

    public static final class Context extends ServerContext {
        public static final float DAMAGE = 20.0f;
        private static final int MAX_TICKS = 200;
        private static final int CHARGE_TICKS = 40;
        private static final float BASE_RADIUS = 2.0f;
        private static final float RADIUS_GROWTH = 0.05f;
        private static final int EFFECT_INTERVAL = 10;

        private final int milestone;
        private int ticks;
        private boolean ended;
        private boolean chargeCancelled;

        private Context(ServerPlayer p, int milestone) {
            super(p);
            this.milestone = milestone;
        }

        private static boolean canBreak(ServerLevel level, ServerPlayer player,
                                        BlockPos pos, BlockState state) {
            var restricted = player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
                    || state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks();
            var event = new BreakBlockEvent(level, pos.immutable(), state, player);
            event.setCanceled(restricted);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre e) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive() || ticks >= MAX_TICKS) {
                end();
                return;
            }

            if (chargeCancelled) {
                end();
                return;
            }

            var skill = Skills.SPACIAL_EXCISION.get();
            skill.reportActivity(player, ticks >= CHARGE_TICKS);
            if (ticks < CHARGE_TICKS) return;

            var effectInterval = milestone >= 2 ? 8 : EFFECT_INTERVAL;
            if (ticks % effectInterval == 0 && level() instanceof ServerLevel sl) {
                var center = player.position();
                var radius = BASE_RADIUS + ticks * RADIUS_GROWTH * (milestone >= 2 ? 1.2f : 1.0f);
                var damage = DAMAGE * AbilitySystemServer.getSystem(player)
                        .getPlayerDamageMultiplier(player.getUUID());
                var source = SkillDamageSource.of(player, skill);

                var targets = sl.getEntitiesOfClass(LivingEntity.class,
                        new AABB(
                                center.x - radius, center.y - radius, center.z - radius,
                                center.x + radius, center.y + radius, center.z + radius),
                        target -> target != player && target.isAlive()
                                && target.position().distanceToSqr(center) <= radius * radius);

                for (var t : targets) {
                    t.hurtServer(sl, source, damage);
                }

                sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        center.x, center.y + 1.0, center.z, 80,
                        radius * 0.5, radius * 0.5, radius * 0.5, 0.12);
                sl.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER,
                        SoundSource.PLAYERS, 0.6f, 0.65f + ticks / (float) MAX_TICKS * 0.5f);

                if (DestroyBlocksSetting.canDestroyBlocks(player, Skills.SPACIAL_EXCISION.get())) {
                    var intRadius = Mth.ceil(radius);
                    var centerBlock = player.blockPosition();
                    var radiusSq = radius * radius;
                    for (var dx = -intRadius; dx <= intRadius; dx++) {
                        for (var dy = -intRadius; dy <= intRadius; dy++) {
                            for (var dz = -intRadius; dz <= intRadius; dz++) {
                                if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                                var pos = centerBlock.offset(dx, dy, dz);
                                if (!sl.hasChunkAt(pos)) continue;
                                var state = sl.getBlockState(pos);
                                if (!state.isAir() && state.getDestroySpeed(sl, pos) >= 0
                                        && canBreak(sl, player, pos, state)) {
                                    sl.removeBlock(pos, false);
                                }
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public void onPlayerHurt(LivingIncomingDamageEvent ev) {
            if (ended) return;
            if (ev.getEntity() != player) return;
            if (ticks < CHARGE_TICKS) {
                chargeCancelled = true;
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            if (milestone >= 3 && ticks >= CHARGE_TICKS && level() instanceof ServerLevel serverLevel) {
                var center = player.position();
                for (var delay = 10; delay <= 40; delay += 10) {
                    var scheduledDelay = delay;
                    org.academy.internal.common.ability.TimedSkillEffectRuntime.schedule(player, delay,
                            () -> emitBoundary(serverLevel, player, center, scheduledDelay));
                }
            }
            Server.ACTIVE.remove(player, this);
            unregister();
        }

        private static void emitBoundary(ServerLevel level, ServerPlayer owner, net.minecraft.world.phys.Vec3 center,
                                         int age) {
            var radius = BASE_RADIUS + MAX_TICKS * RADIUS_GROWTH * 1.2f;
            var inner = Math.max(0.0, radius - 1.25);
            var targets = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center.x - radius, center.y - radius, center.z - radius,
                            center.x + radius, center.y + radius, center.z + radius),
                    target -> target != owner && target.isAlive()
                            && target.distanceToSqr(center) <= radius * radius
                            && target.distanceToSqr(center) >= inner * inner);
            var source = SkillDamageSource.of(owner, Skills.SPACIAL_EXCISION.get());
            for (var target : targets) target.hurtServer(level, source, DAMAGE * 0.25f);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1.0, center.z,
                    32, radius * 0.5, 0.25, radius * 0.5, 0.05);
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
            return PacketTypes.SPACIAL_EXCISION_ACTIVATE.get();
        }
    }
}
