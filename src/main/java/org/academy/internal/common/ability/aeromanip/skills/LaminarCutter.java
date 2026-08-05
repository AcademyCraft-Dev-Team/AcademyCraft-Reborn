package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class LaminarCutter extends Skill {
    public LaminarCutter() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL3).energyCost(30_000)
                .cpCost(30).iterationTicks(25).maxStacks(1).dependsOn(Skills.PNEUMATIC_GRASP)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3)));
    }
    @Override public void initClient() {
        var key = getKey(); AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE); Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_V, InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.cast());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.LAMINAR_CUTTER.get(), List.of(PneumaticGrasp.Client.SKILL_INFO), R.textures.laminar_cutter_icon, 75, 104));
    }
    @Override public void initServer(MinecraftServerContext context) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }
    public static final class Client {
        public static AbilitySystemClient.SkillInfo SKILL_INFO; public static final String KEY_NAME_CAST = SkillNames.LAMINAR_CUTTER + "_cast"; public static Config CONFIG = new Config();
        private static void cast() { if (AbilitySystemClient.canUseSkill(Skills.LAMINAR_CUTTER.get())) MisakaNetworkClient.send(CastPacket.INSTANCE); }
        public static final class Config extends KeyBindingConfig { public static final class Action implements TypeHandler<Config> { public static final TypeHandler<Config> INSTANCE = new Action(); private Action() { } @Override public Config getDefault() { return new Config(); } @Override public Class<Config> getTypeClass() { return Config.class; } } }
    }
    public static final class Server {
        @SubscribePacket public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.LAMINAR_CUTTER.get();
            skill.executeActive(player, context -> skill.getCpCost(context.level())
                    * AeromanipConfig.cpMultiplier(player, SkillNames.LAMINAR_CUTTER), (context, _) -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                var eye = player.getEyePosition(); var direction = player.getLookAngle().normalize();
                var cutterLevel = Math.max(0, Math.min(2, skill.getLevel(player)));
                var length = (24.0 + cutterLevel * 4.0) * AeromanipFieldManager.rangeMultiplier(player)
                        * AeromanipConfig.rangeMultiplier(player, SkillNames.LAMINAR_CUTTER);
                var end = eye.add(direction.scale(length));
                var box = new AABB(eye, end).inflate(0.35);
                var source = SkillDamageSource.of(player, Skills.LAMINAR_CUTTER.get());
                level.playSound(null, player.blockPosition(),
                        org.academy.internal.common.sounds.SoundEvents.AIRFLOW_IMPACT.get(),
                        SoundSource.PLAYERS, 0.65f, 1.15f);
                var damage = 6.0f * AeromanipConfig.damageMultiplier(player, SkillNames.LAMINAR_CUTTER)
                        * context.system().getPlayerAbilityPowerMultiplier(player.getUUID())
                        * context.system().getPlayerDamageMultiplier(player.getUUID());
                for (var target : level.getEntitiesOfClass(LivingEntity.class, box, living -> living != player && living.isAlive() && player.hasLineOfSight(living) && living.getBoundingBox().clip(eye, end).isPresent())) {
                    if (target.hurtServer(level, source, damage)) Skills.LAMINAR_CUTTER.get().onHurt(player, target, damage);
                }
                clearSoftBlocks(player, level, eye, end);
                level.sendParticles(ParticleTypes.CLOUD, eye.x, eye.y, eye.z, 24, 0.2, 0.2, 0.2, 0.04);
            });
        }

        private static void clearSoftBlocks(net.minecraft.server.level.ServerPlayer player, ServerLevel level,
                                             Vec3 start, Vec3 end) {
            var settings = AeromanipConfig.settings(player);
            if (!settings.allowSoftBlockInteraction
                    || !DestroyBlocksSetting.canDestroyBlocks(player, Skills.LAMINAR_CUTTER.get())) return;
            var min = new Vec3(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
            var max = new Vec3(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
            var from = BlockPos.containing(min).offset(-1, -1, -1);
            var to = BlockPos.containing(max).offset(1, 1, 1);
            for (var pos : BlockPos.betweenClosed(from, to)) {
                var center = Vec3.atCenterOf(pos);
                var projection = center.subtract(start).dot(end.subtract(start).normalize());
                if (projection < -0.5 || projection > start.distanceTo(end) + 0.5) continue;
                var nearest = start.add(end.subtract(start).normalize().scale(Math.max(0.0, projection)));
                if (center.distanceToSqr(nearest) > 0.6 * 0.6) continue;
                var state = level.getBlockState(pos);
                if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                    level.removeBlock(pos, false);
                    continue;
                }
                if (!(state.is(Blocks.COBWEB)
                        || state.is(Blocks.VINE)
                        || state.is(Blocks.WEEPING_VINES)
                        || state.is(Blocks.TWISTING_VINES)
                        || state.is(Blocks.SHORT_GRASS)
                        || state.is(Blocks.TALL_GRASS)
                        || state.is(Blocks.FERN)
                        || state.is(Blocks.LARGE_FERN)
                        || state.is(Blocks.DEAD_BUSH)
                        || state.is(Blocks.SEAGRASS)
                        || state.is(Blocks.TALL_SEAGRASS)
                        || state.is(Blocks.NETHER_SPROUTS)
                        || state.is(BlockTags.LEAVES))) continue;
                level.destroyBlock(pos.immutable(), true, player);
            }
        }
    }
    @PacketTarget(ThreadType.SERVER) public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> { public static final CastPacket INSTANCE = new CastPacket(); public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE); private CastPacket() { } @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() { return PacketTypes.LAMINAR_CUTTER_CAST.get(); } }
}
