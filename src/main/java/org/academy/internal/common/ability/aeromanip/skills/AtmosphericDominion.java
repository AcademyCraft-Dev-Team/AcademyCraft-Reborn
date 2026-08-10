package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.hud.ability.ToggleStatusHud;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AirflowField;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class AtmosphericDominion extends Skill {
    public AtmosphericDominion() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL5).energyCost(100_000)
                .cpCost(160).iterationTicks(160).maxStacks(1).dependsOn(Skills.ATMOSPHERE_BLAST_GUN, Skills.VORTEX_PULL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5)));
    }

    @Override public void initClient() {
        var key = getKey(); AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE); Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y, InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.cast());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.ATMOSPHERIC_DOMINION.get(), List.of(AtmosphereBlastGun.Client.SKILL_INFO, VortexPull.Client.SKILL_INFO), R.textures.atmospheric_dominion_icon, 150, 168));
        ToggleStatusHud.registerStateProvider(Skills.ATMOSPHERIC_DOMINION.get(), () -> {
            var player = Minecraft.getInstance().player;
            return player != null && AeromanipFieldSyncPacket.Client.snapshot().values().stream()
                    .anyMatch(field -> field.ownerId().equals(player.getUUID())
                            && field.type() == AirflowField.Type.ATMOSPHERIC_DOMINION);
        });
    }
    @Override public void initServer(MinecraftServerContext context) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }
    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO; public static final String KEY_NAME_CAST = SkillNames.ATMOSPHERIC_DOMINION + "_cast"; public static Config CONFIG = new Config();
        private static void cast() { if (AbilitySystemClient.canUseSkill(Skills.ATMOSPHERIC_DOMINION.get())) MisakaNetworkClient.send(CastPacket.INSTANCE); }
        public static final class Config extends KeyBindingConfig { public static final class Action implements TypeHandler<Config> { public static final TypeHandler<Config> INSTANCE = new Action(); private Action() { } @Override public Config getDefault() { return new Config(); } @Override public Class<Config> getTypeClass() { return Config.class; } } }
    }
    public static final class Server {
        @SubscribePacket public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.ATMOSPHERIC_DOMINION.get();
            skill.executeActive(player, context -> skill.getCpCost(context.level())
                    * AeromanipConfig.cpMultiplier(player, SkillNames.ATMOSPHERIC_DOMINION), (context, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var range = AeromanipConfig.rangeMultiplier(player, SkillNames.ATMOSPHERIC_DOMINION);
                var durationTicks = context.milestone() >= 2 ? 240 : 200;
                var duration = Math.max(1, Math.round(durationTicks * AeromanipConfig.durationMultiplier(player, SkillNames.ATMOSPHERIC_DOMINION)));
                var radius = context.milestone() >= 2 ? 26.0 : 22.0;
                var field = new AirflowField(java.util.UUID.randomUUID(), player.getUUID(), level.dimension(), AirflowField.Type.ATMOSPHERIC_DOMINION,
                        AirflowField.Shape.SPHERE, player.position(), player.getLookAngle(), radius * range, 0, 1.0f, duration, context.milestone());
                AeromanipFieldManager.activate(player, skill, field, Server::tick);
            });
        }
        private static void tick(net.minecraft.server.level.ServerPlayer owner, AirflowField field, int age) {
            var box = field.bounds();
            var handled = 0;
            var cap = ProficiencyPolicy.server(owner).maxBonusEntitiesPerTick();
            for (var target : owner.level().getEntities(owner, box, Entity::isAlive)) {
                if (handled++ >= cap) break;
                if (!field.contains(target.getBoundingBox().getCenter(), target.getBbWidth() * 0.5)) continue;
                if (AeromanipTargeting.isBoss(target)) continue;
                var allied = target == owner
                        || owner.isAlliedTo(target)
                        || target instanceof TamableAnimal animal && animal.isOwnedBy(owner);
                if (!allied && !AeromanipTargeting.canAffectNegatively(owner, target)) continue;
                var friendly = allied;
                if (friendly) {
                    if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.SPEED, 12, 0, false, false, true));
                        living.resetFallDistance();
                        if (living.getAirSupply() < living.getMaxAirSupply()) living.setAirSupply(living.getMaxAirSupply());
                    }
                    if (target instanceof Projectile) continue;
                    AeromanipTargeting.addClampedVelocity(target, field.direction().scale(0.025));
                } else if (target instanceof Projectile projectile) {
                    AeromanipTargeting.addClampedVelocity(projectile, field.direction().scale(0.04));
                } else {
                    var current = target.getDeltaMovement();
                    AeromanipTargeting.addClampedVelocity(target, current.scale(-0.15));
                    if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 0, false, false, true));
                    }
                }
            }
        }
    }
    @PacketTarget(ThreadType.SERVER) public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> { public static final CastPacket INSTANCE = new CastPacket(); public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE); private CastPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() { return PacketTypes.ATMOSPHERIC_DOMINION_CAST.get(); } }
}
