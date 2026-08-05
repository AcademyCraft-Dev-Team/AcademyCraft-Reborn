package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
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

public final class VortexPull extends Skill {
    public VortexPull() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL3).energyCost(30_000)
                .cpCost(40).iterationTicks(60).maxStacks(1).dependsOn(Skills.TAILWIND_FIELD)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3)));
    }

    @Override public void initClient() {
        var key = getKey(); AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE); Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_B, InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.cast());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.VORTEX_PULL.get(), List.of(TailwindField.Client.SKILL_INFO), R.textures.vortex_pull_icon, 130, 104));
    }
    @Override public void initServer(MinecraftServerContext context) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }
    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO; public static final String KEY_NAME_CAST = SkillNames.VORTEX_PULL + "_cast"; public static Config CONFIG = new Config();
        private static void cast() { if (AbilitySystemClient.canUseSkill(Skills.VORTEX_PULL.get())) MisakaNetworkClient.send(CastPacket.INSTANCE); }
        public static final class Config extends KeyBindingConfig { public static final class Action implements TypeHandler<Config> { public static final TypeHandler<Config> INSTANCE = new Action(); private Action() { } @Override public Config getDefault() { return new Config(); } @Override public Class<Config> getTypeClass() { return Config.class; } } }
    }
    public static final class Server {
        @SubscribePacket public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.VORTEX_PULL.get();
            skill.executeActive(player, context -> skill.getCpCost(context.level())
                    * AeromanipConfig.cpMultiplier(player, SkillNames.VORTEX_PULL), (_, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var eye = player.getEyePosition();
                var look = player.getLookAngle().normalize();
                var blockSearchEnd = eye.add(look.scale(16.0));
                var fallbackCenter = eye.add(look.scale(12.0));
                var hit = level.clip(new ClipContext(
                        eye, blockSearchEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                var center = hit.getType() == HitResult.Type.MISS ? fallbackCenter : hit.getLocation();
                var radius = (9.0 + Math.max(0, Math.min(2, skill.getLevel(player))))
                        * AeromanipConfig.rangeMultiplier(player, SkillNames.VORTEX_PULL);
                var duration = Math.max(1, Math.round(80 * AeromanipConfig.durationMultiplier(player, SkillNames.VORTEX_PULL)));
                var field = new AirflowField(java.util.UUID.randomUUID(), player.getUUID(), level.dimension(), AirflowField.Type.VORTEX,
                        AirflowField.Shape.SPHERE, center, look, radius, 0, 1.0f, duration);
                AeromanipFieldManager.activate(player, skill, field, Server::tick);
            });
        }
        private static void tick(net.minecraft.server.level.ServerPlayer owner, AirflowField field, int age) {
            var box = field.bounds().inflate(1.0);
            for (var target : owner.level().getEntities(owner, box, Entity::isAlive)) {
                if (!field.contains(target.getBoundingBox().getCenter(), target.getBbWidth() * 0.5)) continue;
                if (!(target instanceof Projectile) && !AeromanipTargeting.canAffectNegatively(owner, target)) continue;
                var liftHeight = Math.min(4.0, field.radius() * 0.55);
                var delta = AeromanipTargeting.updraftDirection(
                        field.center(), target.getBoundingBox().getCenter(), liftHeight);
                if (delta.lengthSqr() <= 1.0e-8) continue;
                var multiplier = AeromanipTargeting.forceMultiplier(owner, target);
                if (multiplier <= 0.0) continue;
                var distance = Math.sqrt(delta.lengthSqr());
                var targetSpeed = Math.min(1.65, 0.9 + distance * 0.12) * multiplier;
                AeromanipTargeting.steerVelocity(target, delta, 0.44, targetSpeed);
                target.resetFallDistance();
            }
        }
    }
    @PacketTarget(ThreadType.SERVER) public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> { public static final CastPacket INSTANCE = new CastPacket(); public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE); private CastPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() { return PacketTypes.VORTEX_PULL_CAST.get(); } }
}
