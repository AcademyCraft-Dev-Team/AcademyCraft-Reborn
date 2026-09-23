package org.academy.internal.common.ability.electromaster.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ElectromasterGraphEffects;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LightningNova extends Skill {
    private static final int MAX_RADIUS = 16;
    private static final float DAMAGE = 4.0f;
    private static final int PULSE_DURATION = 200;
    private static final int VISUAL_INTERVAL = 10;

    public LightningNova() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .damage()
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(15)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.THUNDER_LANCE)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBindingMigratingDefaults(
                        Client.KEY_NAME_USE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                                InputConstants.PRESS, InputConstants.MOD_SHIFT),
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_SHIFT))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.LIGHTNING_NOVA + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            var minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.LIGHTNING_NOVA.get())) return;
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public LightningNova.Client.Config getDefault() {
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
        public static float calculateDamage(float abilityPower, float playerMultiplier) {
            return DAMAGE * Math.max(0.0f, abilityPower) * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            tryActivate(player);
        }

        public static boolean tryActivate(ServerPlayer player) {
            return Skills.LIGHTNING_NOVA.get().executeActive(player,
                    (ctx, actualCost) -> AbilitySystemServer.registerContext(new Context(player, ctx.milestone())));
        }
    }

    public static final class Context extends ServerContext {
        private static final java.util.concurrent.atomic.AtomicInteger NEXT_VISUAL_ID = new java.util.concurrent.atomic.AtomicInteger();
        private final int visualId = NEXT_VISUAL_ID.getAndIncrement() & 0xFFFFFF;
        private int ticks;
        private int phaseTicks;
        private final int milestone;
        private final int maximumRadius;
        private final int outwardDuration;
        private boolean echo;
        private final Set<UUID> outwardHits = new HashSet<>();
        private final Set<UUID> echoHits = new HashSet<>();
        private boolean ended;
        private int nextVisualTick = 1;
        private final long visualSeed;
        private final ServerLevel castLevel;
        private final SkillDamageSource outwardSource;
        private final SkillDamageSource echoSource;

        private Context(ServerPlayer player, int milestone) {
            super(player);
            this.milestone = milestone;
            castLevel = player.level();
            visualSeed = castLevel.getRandom().nextLong();
            outwardSource = SkillDamageSource.of(player, Skills.LIGHTNING_NOVA.get(),
                    DamageTypes.ELECTRO_DAMAGE).withElectricalChargePoints(2);
            echoSource = SkillDamageSource.of(player, Skills.LIGHTNING_NOVA.get(),
                    DamageTypes.ELECTRO_DAMAGE).withElectricalChargePoints(1);
            maximumRadius = Math.round(Skills.LIGHTNING_NOVA.get().scaledRange(player,
                    milestone >= 2 ? Math.round(MAX_RADIUS * 1.25f) : MAX_RADIUS));
            outwardDuration = milestone >= 2
                    ? Math.max(1, Math.round(PULSE_DURATION * 1.25f / 1.15f))
                    : PULSE_DURATION;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks++;
            if (ended) return;
            if (player.hasDisconnected() || !player.isAlive() || level() != castLevel) {
                end();
                return;
            }
            Skills.LIGHTNING_NOVA.get().reportActivity(player, false);

            phaseTicks++;
            if (!echo && phaseTicks >= outwardDuration) {
                if (milestone < 3) {
                    end();
                    return;
                }
                echo = true;
                phaseTicks = 0;
                nextVisualTick = ticks;
            } else if (echo && phaseTicks >= outwardDuration) {
                end();
                return;
            }
            var progress = Math.clamp((float) phaseTicks / outwardDuration, 0.0f, 1.0f);
            var currentRadius = echo
                    ? Math.max(0.0f, maximumRadius * (1.0f - progress))
                    : 1.0f + progress * maximumRadius;
            var innerRadius = Math.max(0, currentRadius - 1.5f);
            var outerRadius = echo ? currentRadius + 1.5f : currentRadius;

            if (ticks >= nextVisualTick) {
                int visualTicks = Math.min(VISUAL_INTERVAL, outwardDuration - phaseTicks);
                ElectromasterGraphEffects.spawnNovaRing(castLevel, player.position(), player.getId(), 1,
                        currentRadius, (echo ? -1 : 1) * maximumRadius * 20f / outwardDuration,
                        visualTicks / 20f, ticks / 20f, visualSeed, visualId);
                nextVisualTick = ticks + visualTicks;
            }
            var phaseHits = echo ? echoHits : outwardHits;
            double innerSquared = innerRadius * innerRadius;
            double outerSquared = outerRadius * outerRadius;
            var targets = castLevel.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(outerRadius),
                    target -> target != player && target.isAlive() && !phaseHits.contains(target.getUUID())
                            && withinWavefront(target.distanceToSqr(player), innerSquared, outerSquared));
            if (!targets.isEmpty()) {
                var system = AbilitySystemServer.getSystem(player);
                var damage = Server.calculateDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID())
                );
                var source = echo ? echoSource : outwardSource;
                for (var target : targets) {
                    phaseHits.add(target.getUUID());
                    target.hurtServer(castLevel, source, damage * (echo ? 0.5f : 1.0f));
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            outwardHits.clear();
            echoHits.clear();
        }
    }

    static boolean withinWavefront(double distanceSquared, double innerSquared, double outerSquared) {
        return distanceSquared >= innerSquared && distanceSquared <= outerSquared;
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.LIGHTNING_NOVA_ACTIVATE_P4.get();
        }
    }
}
