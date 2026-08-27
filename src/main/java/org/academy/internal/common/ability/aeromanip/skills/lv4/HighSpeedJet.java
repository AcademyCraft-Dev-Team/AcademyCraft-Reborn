package org.academy.internal.common.ability.aeromanip.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

/** Places persistent block-face jet nozzles and remotely fires every loaded owned nozzle. */
public final class HighSpeedJet extends Skill {
    private static final double PLACEMENT_RANGE = 8.0;
    private static final double CONTROL_RANGE = 64.0;

    public HighSpeedJet() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .iterationTicks(15)
                .maxStacks(5)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4)));
    }

    static int maximumNozzles(int milestone) {
        return milestone >= 1 ? 12 : 8;
    }

    static int activationDuration(int milestone) {
        return milestone >= 2 ? 60 : 40;
    }

    static float activationCpCost(int nozzleCount) {
        return 8.0f + Math.max(0, nozzleCount) * 2.0f;
    }

    static float activationAirCost(int nozzleCount) {
        return Math.max(0, nozzleCount) * 8.0f;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_PLACE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_PLACE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_G, InputConstants.RELEASE,
                                InputConstants.MOD_ALT)),
                _ -> Client.place());
        InputSystem.addKeyBinding(
                Client.KEY_NAME_ACTIVATE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_ACTIVATE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.RELEASE,
                                InputConstants.MOD_ALT)),
                _ -> Client.activate());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilitySystemClient.addSkillInfo(
                        AbilityCategories.AEROMANIP.get(),
                        new AbilitySystemClient.SkillInfo(
                                Skills.HIGH_SPEED_JET.get(),
                                List.of(),
                                R.textures.high_speed_jet_icon,
                                40,
                                136));
        public static final String KEY_NAME_PLACE = SkillNames.HIGH_SPEED_JET + "_place";
        public static final String KEY_NAME_ACTIVATE = SkillNames.HIGH_SPEED_JET + "_activate";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void place() {
            if (AbilitySystemClient.canUseSkill(Skills.HIGH_SPEED_JET.get())) {
                MisakaNetworkClient.send(PlacePacket.INSTANCE);
            }
        }

        private static void activate() {
            if (AbilitySystemClient.canUseSkill(Skills.HIGH_SPEED_JET.get())) {
                MisakaNetworkClient.send(ActivatePacket.INSTANCE);
            }
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
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
        private Server() {
        }

        @SubscribePacket
        public static void handlePlace(PlacePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.HIGH_SPEED_JET.get();
            if (!(player.level() instanceof ServerLevel level) || !skill.isEnabled(player)) return;
            var eye = player.getEyePosition();
            var end = eye.add(player.getLookAngle().normalize().scale(PLACEMENT_RANGE));
            var hit = level.clip(new ClipContext(
                    eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) return;
            var supportPos = hit.getBlockPos();
            var face = hit.getDirection();
            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, face)) return;
            var loaded = ownedNozzles(level, player);
            var configuredMaximum = resolvedMaximumNozzles(player, skill);
            if (loaded.size() >= configuredMaximum) return;
            skill.executeActiveWithResource(
                    player,
                    _ -> 18.0f * AeromanipConfig.cpMultiplier(player, SkillNames.HIGH_SPEED_JET),
                    _ -> 12.0f,
                    (_, _) -> {
                        var nozzle = new HighSpeedJetNozzle(
                                EntityTypes.HIGH_SPEED_JET_NOZZLE.get(), level);
                        nozzle.attach(player.getUUID(), supportPos, face);
                        level.addFreshEntity(nozzle);
                    });
        }

        @SubscribePacket
        public static void handleActivate(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.HIGH_SPEED_JET.get();
            if (!(player.level() instanceof ServerLevel level) || !skill.isEnabled(player)) return;
            var nozzles = ownedNozzles(level, player);
            if (nozzles.isEmpty()) return;
            var maximum = resolvedMaximumNozzles(player, skill);
            if (nozzles.size() > maximum) nozzles = nozzles.subList(0, maximum);
            var resolvedNozzles = List.copyOf(nozzles);
            var count = resolvedNozzles.size();
            var duration = Math.max(1, Math.round(
                    activationDuration(skill.getEffectiveProficiencyMilestone(player))
                            * AeromanipConfig.durationMultiplier(
                            player, SkillNames.HIGH_SPEED_JET)));
            skill.executeActiveWithResource(
                    player,
                    _ -> activationCpCost(count)
                            * AeromanipConfig.cpMultiplier(player, SkillNames.HIGH_SPEED_JET),
                    _ -> activationAirCost(count),
                    (_, _) -> resolvedNozzles.forEach(nozzle -> nozzle.activate(duration)));
        }

        private static List<HighSpeedJetNozzle> ownedNozzles(
                ServerLevel level,
                ServerPlayer player
        ) {
            var range = CONTROL_RANGE
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.HIGH_SPEED_JET);
            return level.getEntitiesOfClass(
                    HighSpeedJetNozzle.class,
                    player.getBoundingBox().inflate(range),
                    nozzle -> nozzle.isOwnedBy(player));
        }

        private static int resolvedMaximumNozzles(ServerPlayer player, HighSpeedJet skill) {
            var configuredBaseMaximum = Math.round(AeromanipConfig.skillFloat(
                    player, SkillNames.HIGH_SPEED_JET, "maximumNozzles", 8.0f));
            return Math.max(1, Math.min(32,
                    configuredBaseMaximum
                            + (skill.getEffectiveProficiencyMilestone(player) >= 1 ? 4 : 0)));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PlacePacket
            extends Packet<ServerGamePacketListenerImpl, PlacePacket> {
        public static final PlacePacket INSTANCE = new PlacePacket();
        public static final StreamCodec<ByteBuf, PlacePacket> CODEC = StreamCodec.unit(INSTANCE);

        private PlacePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PlacePacket> getPacketType() {
            return PacketTypes.HIGH_SPEED_JET_PLACE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket
            extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.HIGH_SPEED_JET_ACTIVATE.get();
        }
    }
}
