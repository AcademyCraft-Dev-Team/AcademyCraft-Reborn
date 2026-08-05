package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class BreathingFilm extends Skill {
    private static final int REFRESH_INTERVAL_TICKS = 10;

    public BreathingFilm() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .passive()
                .maintenanceCost(20)
                .iterationTicks(40)
                .dependsOn(Skills.FLOW_SENSE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
        );
    }

    @Override
    public void initClient() {
        Client.initialize();
    }

    @Override
    public void initServer(org.academy.api.server.vanilla.MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BREATHING_FILM.get(),
                        List.of(FlowSense.Client.SKILL_INFO),
                        R.textures.breathing_film_icon,
                        75,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.BREATHING_FILM + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void initialize() {
            var skill = Skills.BREATHING_FILM.get();
            AcademyCraftConfig.registerTypeHandler(skill.getKey(), Config.Action.INSTANCE);
            CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(skill.getKey());
            InputSystem.addKeyBinding(KEY_NAME_CAST,
                    CONFIG.getKeyBinding(KEY_NAME_CAST,
                            InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_O,
                                    InputConstants.RELEASE, InputConstants.MOD_ALT)),
                    _ -> cast());
        }

        private static void cast() {
            if (AbilitySystemClient.canUseSkill(Skills.BREATHING_FILM.get())) MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements org.academy.api.common.gson.TypeHandler<Config> {
                public static final Action INSTANCE = new Action();
                private Action() { }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level().getGameTime() % REFRESH_INTERVAL_TICKS != 0) return;

            var skill = Skills.BREATHING_FILM.get();
            var system = AbilitySystemServer.getSystem(player);
            var runtimeData = skill.getRuntimeData(player);
            var available = runtimeData.isPresent() && LearningHelper.isSkillAvailableForCategory(
                    system.getPlayerAbilityCategory(player.getUUID()),
                    skill
            );
            if (!available || !player.isAlive()) {
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }
            var hazardous = player.isEyeInFluid(FluidTags.WATER)
                    || player.getAirSupply() < player.getMaxAirSupply();
            if (!hazardous) {
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                return;
            }
            if (!runtimeData.orElseThrow().isEnabled()) system.toggleSkill(player.getUUID(), skill.getKeyString());
            if (!system.ensurePermanentOccupation(
                    player.getUUID(),
                    skill.getMaintenanceCost(skill.getLevel(player))
                            * AeromanipConfig.cpMultiplier(player, SkillNames.BREATHING_FILM),
                    skill
            )) {
                return;
            }

            var maxAir = player.getMaxAirSupply();
            if (player.getAirSupply() < maxAir) {
                player.setAirSupply(maxAir);
            }
        }
    }

    public static final class Server {
        private static final Map<ServerPlayer, FilmContext> ACTIVE_FILMS = new WeakHashMap<>();

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.BREATHING_FILM.get();
            if (!skill.isEnabled(player) || skill.getLevel(player) < 3 || ACTIVE_FILMS.containsKey(player)) return;
            var target = player.level().getEntities(player,
                            new AABB(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(10))).inflate(1.0),
                            entity -> entity instanceof LivingEntity living
                                    && living.isAlive()
                                    && living != player
                                    && player.hasLineOfSight(living)
                                    && !AeromanipTargeting.canAffectNegatively(player, living))
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                    .map(LivingEntity.class::cast).orElse(null);
            if (target == null) return;
            if (!AbilitySystemServer.getSystem(player).tryTimedOccupation(player.getUUID(),
                    25.0f * AeromanipConfig.cpMultiplier(player, SkillNames.BREATHING_FILM), skill, 1)) return;
            var context = new FilmContext(player, target);
            ACTIVE_FILMS.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static final class FilmContext extends ServerContext {
            private final LivingEntity target;
            private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
            private int age;
            private boolean ended;

            private FilmContext(ServerPlayer player, LivingEntity target) {
                super(player);
                this.target = target;
                this.dimension = player.level().dimension();
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                if (ended || age++ >= 600 || !player.isAlive() || !target.isAlive()
                        || !player.level().dimension().equals(dimension)
                        || !Skills.BREATHING_FILM.get().isEnabled(player)) {
                    end();
                    return;
                }
                target.setAirSupply(target.getMaxAirSupply());
            }

            private void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @Override protected void onUnregistered() {
                ended = true;
                ACTIVE_FILMS.remove(player, this);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);
        private CastPacket() { }
        @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.BREATHING_FILM_CAST.get();
        }
    }
}
