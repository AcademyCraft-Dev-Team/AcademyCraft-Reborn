package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.config.SkillSettingsRegistry;
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
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import net.minecraft.util.Mth;

public class LightningStorm extends Skill {
    private static final int STRIKE_COUNT = 21;
    private static final float RADIUS = 8.0f;
    private static final float DAMAGE = 8.0f;

    public LightningStorm() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(60_000)
                .cpCost(80)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.BALL_LIGHTNING)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        Client.registerSettings();
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_ALT))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.LIGHTNING_STORM + "_use";
        public static Config CONFIG = new Config();
        private static boolean settingsRegistered;

        private static void registerSettings() {
            if (settingsRegistered) return;
            settingsRegistered = true;
            SkillSettingsRegistry.INSTANCE.register(
                    Skills.LIGHTNING_STORM.get(),
                    new SkillSettingsRegistry.Module(
                            "sky_strike_feedback",
                            "app.academy.skill_settings.advanced.sky_strike_feedback",
                            List.of(
                                    new SkillSettingsRegistry.FloatRange(
                                            "flash_intensity",
                                            "app.academy.skill_settings.advanced.sky_strike_flash",
                                            0.0f,
                                            1.0f,
                                            0.05f,
                                            CONFIG::getFlashIntensity,
                                            CONFIG::setFlashIntensity,
                                            Client::persistVisualSettings
                                    ),
                                    new SkillSettingsRegistry.FloatRange(
                                            "shake_intensity",
                                            "app.academy.skill_settings.advanced.sky_strike_shake",
                                            0.0f,
                                            1.0f,
                                            0.05f,
                                            CONFIG::getShakeIntensity,
                                            CONFIG::setShakeIntensity,
                                            Client::persistVisualSettings
                                    )
                            )
                    )
            );
        }

        private static void persistVisualSettings() {
            AcademyCraftClient.Config.INSTANCE.setConfig(Skills.LIGHTNING_STORM.get().getKey(), CONFIG);
            AcademyCraftClient.Config.INSTANCE.save();
        }

        public static void onUse() {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.LIGHTNING_STORM.get())) return;
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            private float flashIntensity = 1.0f;
            private float shakeIntensity = 1.0f;

            private static float sanitizeIntensity(float value) {
                return Float.isFinite(value) ? Mth.clamp(value, 0.0f, 1.0f) : 1.0f;
            }

            public float getFlashIntensity() {
                return sanitizeIntensity(flashIntensity);
            }

            public void setFlashIntensity(float flashIntensity) {
                this.flashIntensity = sanitizeIntensity(flashIntensity);
            }

            public float getShakeIntensity() {
                return sanitizeIntensity(shakeIntensity);
            }

            public void setShakeIntensity(float shakeIntensity) {
                this.shakeIntensity = sanitizeIntensity(shakeIntensity);
            }

            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public LightningStorm.Client.Config getDefault() {
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
            var center = player.pick(50.0, 1.0f, false).getLocation();
            if (!player.level().hasChunkAt(BlockPos.containing(center))) return;
            Skills.LIGHTNING_STORM.get().executeActive(player,
                    (_, _) -> AbilitySystemServer.registerContext(new Context(player, center)));
        }
    }

    public static final class Context extends ServerContext {
        private final Vec3 center;
        private int strikesLeft = STRIKE_COUNT;
        private int cooldown;
        private boolean ended;

        private Context(ServerPlayer player, Vec3 center) {
            super(player);
            this.center = center;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            if (player.hasDisconnected() || !player.isAlive() || strikesLeft <= 0) {
                end();
                return;
            }
            var skill = Skills.LIGHTNING_STORM.get();
            skill.reportActivity(player, false);

            cooldown--;
            if (cooldown > 0) return;
            cooldown = 3;

            strikesLeft--;
            var r = Mth.sqrt((float) (Math.random())) * RADIUS;
            var theta = Math.random() * Mth.TWO_PI;
            var strikeX = center.x + r * Mth.cos(theta);
            var strikeZ = center.z + r * Mth.sin(theta);

            if (level() instanceof ServerLevel serverLevel) {
                skill.reportActivity(player, true);
                var strikePos = new BlockPos((int) strikeX, (int) center.y, (int) strikeZ);
                var topPos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, strikePos);
                var impact = Vec3.atBottomCenterOf(topPos);
                ElectromasterArcEffects.spawnSkyStrike(serverLevel, impact, SkyStrikeProfile.LIGHTNING_STORM);

                var box = new AABB(topPos).inflate(3);
                var targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
                var system = AbilitySystemServer.getSystem(player);
                var damage = Server.calculateDamage(
                        system.getPlayerAbilityPowerMultiplier(player.getUUID()),
                        system.getPlayerDamageMultiplier(player.getUUID())
                );
                var source = SkillDamageSource.of(
                        player,
                        skill,
                        DamageTypes.ELECTRO_DAMAGE
                );
                for (var target : targets) {
                    target.hurtServer(serverLevel, source, damage);
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
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
            return PacketTypes.LIGHTNING_STORM_ACTIVATE.get();
        }
    }
}
