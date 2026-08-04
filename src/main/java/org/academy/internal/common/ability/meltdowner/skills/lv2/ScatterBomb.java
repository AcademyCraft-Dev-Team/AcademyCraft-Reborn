package org.academy.internal.common.ability.meltdowner.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
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
import org.academy.internal.common.ability.meltdowner.skills.SingleHighSpeedElectronBeam;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScatterBomb extends Skill {
    static final int MIN_CHARGE_TICKS = 20;
    static final int MAX_CHARGE_TICKS = 80;
    static final int BEAM_COUNT = 7;
    static final float BASE_DAMAGE = 20.0f;
    static final float MAX_HEALTH_DAMAGE_RATIO = 0.01f;
    static final float BEAM_LENGTH = 50.0f;

    public ScatterBomb() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(40)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Single High-Speed Electron Beam",
                        "academy:single_high_speed_electron_beam"
                ))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Handler.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_START,
                InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.PRESS,
                        InputConstants.MOD_ALT
                )
        ), Client::start);
        InputSystem.addKeyBinding(Client.KEY_NAME_END, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_END,
                InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.RELEASE,
                        InputConstants.MOD_ALT
                )
        ), _ -> Client.end());
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.MELTDOWNER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.SCATTER_BOMB.get(),
                        List.of(SingleHighSpeedElectronBeam.Client.SKILL_INFO),
                        R.textures.scatter_bomb_icon,
                        70,
                        50
                )
        );
        public static final String KEY_NAME_START = SkillNames.SCATTER_BOMB + "_start";
        public static final String KEY_NAME_END = SkillNames.SCATTER_BOMB + "_end";
        public static Config CONFIG = new Config();
        private static boolean charging;
        private static int chargeTicks;
        private static InputSystem.InputType heldInputType;
        private static int heldInput = -1;

        private Client() {
        }

        private static void start(InputSystem.BindingContext context) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (charging
                    || player == null
                    || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.SCATTER_BOMB.get())) {
                return;
            }
            charging = true;
            chargeTicks = 0;
            heldInputType = context.type();
            heldInput = context.input();
            MisakaNetworkClient.send(new ShootPacket(-1));
            player.level().playLocalSound(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.SCATTER_BOMB_CHARGE.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f,
                    false
            );
        }

        private static void end() {
            if (!charging) return;
            charging = false;
            heldInputType = null;
            heldInput = -1;
            MisakaNetworkClient.send(new ShootPacket(chargeTicks));
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            if (!charging) return;
            var player = Minecraft.getInstance().player;
            if (player == null || !AbilitySystemClient.canUseSkill(Skills.SCATTER_BOMB.get())) {
                charging = false;
                chargeTicks = 0;
                heldInputType = null;
                heldInput = -1;
                return;
            }
            chargeTicks = Math.min(chargeTicks + 1, MAX_CHARGE_TICKS);
            if (heldInputType != null && heldInput >= 0
                    && !InputSystem.isDown(heldInputType, heldInput)) {
                end();
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Handler implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Handler();

                private Handler() {
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
        private static final int SPAWN_INTERVAL_TICKS = 3;
        private static final Map<UUID, ChargeState> CHARGING = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(ShootPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (packet.chargeTicks() < 0) {
                start(player);
                return;
            }
            release(player);
        }

        private static void start(ServerPlayer player) {
            if (!Skills.SCATTER_BOMB.get().isEnabled(player) || CHARGING.containsKey(player.getUUID())) return;
            CHARGING.put(player.getUUID(), new ChargeState(player.level(), player.level().getGameTime()));
        }

        private static void release(ServerPlayer player) {
            var state = CHARGING.remove(player.getUUID());
            if (state == null) return;
            var chargeTicks = Math.clamp(
                    (int) (player.level().getGameTime() - state.startTick), 0, MAX_CHARGE_TICKS);
            if (chargeTicks < MIN_CHARGE_TICKS || state.level != player.level()) {
                state.cleanup();
                return;
            }

            var skill = Skills.SCATTER_BOMB.get();
            skill.executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                level.playSound(null, player, SoundEvents.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                var damageMultiplier = ctx.system().getPlayerDamageMultiplier(player.getUUID());
                var radiationEnabled = Skills.RADIATION_INTENSIFY.get().isEnabled(player);
                state.ensureAllBeams(player);
                for (var beam : state.beams) {
                    beam.configure(
                            player,
                            skill,
                            BASE_DAMAGE,
                            MAX_HEALTH_DAMAGE_RATIO,
                            damageMultiplier,
                            radiationEnabled,
                            DestroyBlocksSetting.canDestroyBlocks(player)
                    );
                    beam.currentChargerTicks = beam.getAttackDelayTicks();
                    beam.setHeldCharge(false);
                    beam.setBetaTrailOnFire(true);
                }
            });
            if (state.beams.stream().allMatch(HighSpeedElectronBeam::isHeldCharge)) state.cleanup();
        }

        private static void tick(ServerPlayer player) {
            var state = CHARGING.get(player.getUUID());
            if (state == null) return;
            if (!player.isAlive() || player.hasDisconnected()
                    || !Skills.SCATTER_BOMB.get().isEnabled(player)
                    || state.level != player.level()) {
                CHARGING.remove(player.getUUID(), state);
                state.cleanup();
                return;
            }
            var elapsed = Math.clamp(
                    (int) (player.level().getGameTime() - state.startTick), 0, MAX_CHARGE_TICKS);
            var desired = Math.min(BEAM_COUNT, 1 + elapsed / SPAWN_INTERVAL_TICKS);
            state.ensureBeamCount(player, desired);
            state.follow(player);
        }

        private static final class ChargeState {
            private final ServerLevel level;
            private final long startTick;
            private final List<HighSpeedElectronBeam> beams = new ArrayList<>();

            private ChargeState(ServerLevel level, long startTick) {
                this.level = level;
                this.startTick = startTick;
            }

            private void ensureAllBeams(ServerPlayer player) {
                ensureBeamCount(player, BEAM_COUNT);
                follow(player);
            }

            private void ensureBeamCount(ServerPlayer player, int count) {
                while (beams.size() < count) {
                    var beam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
                    beam.configure(player, Skills.SCATTER_BOMB.get(), 0, 0, 0, false, false);
                    beam.setNoGravity(true);
                    beam.setAttackDelayTicks(
                            SingleHighSpeedElectronBeam.getConfiguredAttackDelayTicks(player));
                    beam.setHeldCharge(true);
                    beam.setBeamLength(BEAM_LENGTH);
                    beams.add(beam);
                    position(player, beam, beams.size() - 1);
                    level.addFreshEntity(beam);
                }
            }

            private void follow(ServerPlayer player) {
                for (var i = 0; i < beams.size(); i++) {
                    var beam = beams.get(i);
                    if (!beam.isRemoved()) position(player, beam, i);
                }
            }

            private static void position(ServerPlayer player, HighSpeedElectronBeam beam, int index) {
                var eyePos = player.getEyePosition().add(0.0, -0.5, 0.0);
                var forward = player.getLookAngle().normalize();
                var right = forward.cross(new Vec3(0.0, 1.0, 0.0));
                if (right.lengthSqr() < 1.0e-6) right = new Vec3(1.0, 0.0, 0.0);
                right = right.normalize();
                var up = right.cross(forward).normalize();
                var angle = index * Math.PI * 2.0 / BEAM_COUNT;
                var offset = right.scale(Math.cos(angle) * 0.9)
                        .add(up.scale(Math.sin(angle) * 0.405));
                beam.setPos(eyePos.add(forward.scale(1.75)).add(offset));
                beam.setYRot(player.getYRot());
                beam.setXRot(player.getXRot());
            }

            private void cleanup() {
                for (var beam : beams) {
                    if (!beam.isRemoved()) beam.discard();
                }
                beams.clear();
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends Packet<ServerGamePacketListenerImpl, ShootPacket> {
        public static final StreamCodec<ByteBuf, ShootPacket> CODEC = ByteBufCodecs.VAR_INT.map(
                ShootPacket::new,
                ShootPacket::chargeTicks
        );
        private final int chargeTicks;

        public ShootPacket(int chargeTicks) {
            this.chargeTicks = chargeTicks;
        }

        public int chargeTicks() {
            return chargeTicks;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ShootPacket> getPacketType() {
            return PacketTypes.SCATTER_BOMB_SHOOT.get();
        }
    }
}
