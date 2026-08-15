package org.academy.internal.common.ability.aeromanip.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldManager;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class LaminarCutter extends Skill {
    private static final double BLADE_LENGTH = 5.0;
    private static final double BLADE_HALF_WIDTH = BLADE_LENGTH * 0.5;
    private static final double BLADE_HALF_THICKNESS = 0.55;
    private static final Identifier LAMINAR_FRACTURE_ID = AcademyCraft.academy("laminar_fracture");

    public LaminarCutter() {
        super(Builder.of(AbilityCategories.AEROMANIP.get()).level(AbilityLevel.LEVEL3).energyCost(30_000)
                .cpCost(20).iterationTicks(10).maxStacks(20).dependsOn(Skills.PNEUMATIC_GRASP)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3)));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_V, InputConstants.RELEASE, InputConstants.MOD_ALT)), _ -> Client.cast());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(), new AbilitySystemClient.SkillInfo(Skills.LAMINAR_CUTTER.get(), List.of(PneumaticGrasp.Client.SKILL_INFO), R.textures.laminar_cutter_icon, 75, 104));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CAST = SkillNames.LAMINAR_CUTTER + "_cast";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private static void cast() {
            if (AbilitySystemClient.canUseSkill(Skills.LAMINAR_CUTTER.get()))
                MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

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
        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            tryCast(player, player.getLookAngle(), -1.0f, 1.0f, -1.0f);
        }

        public static boolean tryProgramCast(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                float baseCost
        ) {
            return tryCast(player, direction, maximumRange, damageScale, baseCost);
        }

        private static boolean tryCast(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                float baseCost
        ) {
            if (player == null || direction == null
                    || !Double.isFinite(direction.x)
                    || !Double.isFinite(direction.y)
                    || !Double.isFinite(direction.z)
                    || direction.lengthSqr() < 1.0E-12
                    || !Float.isFinite(maximumRange)
                    || !Float.isFinite(damageScale)
                    || !Float.isFinite(baseCost)
                    || (maximumRange != -1.0f
                    && (maximumRange <= 0.0f || maximumRange > 64.0f))
                    || damageScale < 0.0f
                    || damageScale > 2.0f) {
                return false;
            }
            var skill = Skills.LAMINAR_CUTTER.get();
            var normalizedDirection = direction.normalize();
            return skill.executeActive(player, context -> (baseCost < 0.0f
                            ? skill.getCpCost(context.level()) : baseCost)
                    * AeromanipConfig.cpMultiplier(player, SkillNames.LAMINAR_CUTTER),
                    (context, _) -> executeCut(
                            player,
                            normalizedDirection,
                            maximumRange,
                            damageScale,
                            context
                    ));
        }

        private static void executeCut(
                ServerPlayer player,
                Vec3 direction,
                float maximumRange,
                float damageScale,
                Skill.SkillContext context
        ) {
            if (!(player.level() instanceof ServerLevel level)) return;
            var skill = Skills.LAMINAR_CUTTER.get();
            var eye = player.getEyePosition();
            var cutterLevel = Math.max(0, Math.min(2, skill.getLevel(player)));
            var length = (24.0 + cutterLevel * 4.0) * AeromanipFieldManager.rangeMultiplier(player)
                    * AeromanipConfig.rangeMultiplier(player, SkillNames.LAMINAR_CUTTER);
            if (context.milestone() >= 2) length *= 1.2;
            if (maximumRange > 0.0f) length = Math.min(length, maximumRange);
            var resolvedLength = length;
            var end = eye.add(direction.scale(length));
            var bladeRight = new Vec3(-direction.z, 0.0, direction.x);
            if (bladeRight.lengthSqr() < 1.0E-8) bladeRight = new Vec3(1.0, 0.0, 0.0);
            bladeRight = bladeRight.normalize();
            var bladeNormal = direction.cross(bladeRight).normalize();
            var box = new AABB(eye, end).inflate(BLADE_HALF_WIDTH, 1.5, BLADE_HALF_WIDTH);
            var source = SkillDamageSource.of(player, Skills.LAMINAR_CUTTER.get());
            level.playSound(null, player.blockPosition(),
                    SoundEvents.AIRFLOW_IMPACT.get(),
                    SoundSource.PLAYERS, 0.65f, 1.15f);
            var damage = 4.0f * AeromanipConfig.damageMultiplier(player, SkillNames.LAMINAR_CUTTER)
                    * context.system().getPlayerAbilityPowerMultiplier(player.getUUID())
                    * context.system().getPlayerDamageMultiplier(player.getUUID())
                    * damageScale;
            var finalBladeRight = bladeRight;
            var finalBladeNormal = bladeNormal;
            for (var target : level.getEntitiesOfClass(LivingEntity.class, box,
                    living -> living != player
                            && living.isAlive()
                            && player.hasLineOfSight(living)
                            && intersectsBlade(
                            living,
                            eye,
                            direction,
                            finalBladeRight,
                            finalBladeNormal,
                            resolvedLength
                    ))) {
                if (target.hurtServer(level, source, damage)) {
                    Skills.LAMINAR_CUTTER.get().onHurt(player, target, damage);
                    if (context.milestone() >= 3) applyFracture(player, target);
                }
            }
            clearSoftBlocks(player, level, eye, end, direction, bladeRight, bladeNormal, context.milestone());
            spawnBladeVisual(level, eye, direction, bladeRight, length);
        }

        private static boolean intersectsBlade(LivingEntity target, Vec3 start, Vec3 direction,
                                               Vec3 bladeRight, Vec3 bladeNormal, double range) {
            var relative = target.getBoundingBox().getCenter().subtract(start);
            var forward = relative.dot(direction);
            if (forward < -target.getBbWidth() || forward > range + target.getBbWidth()) return false;
            var lateral = Math.abs(relative.dot(bladeRight));
            if (lateral > BLADE_HALF_WIDTH + target.getBbWidth() * 0.5) return false;
            return Math.abs(relative.dot(bladeNormal))
                    <= BLADE_HALF_THICKNESS + target.getBbHeight() * 0.5;
        }

        private static void spawnBladeVisual(ServerLevel level, Vec3 start, Vec3 direction,
                                             Vec3 bladeRight, double range) {
            for (var step = 0; step <= 12; step++) {
                var center = start.add(direction.scale(range * step / 12.0));
                for (var across = -2; across <= 2; across++) {
                    var point = center.add(bladeRight.scale(across * BLADE_HALF_WIDTH / 2.0));
                    level.sendParticles(ParticleTypes.CLOUD,
                            point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
                }
            }
            var tip = start.add(direction.scale(range));
            for (var across = -2; across <= 2; across++) {
                var point = tip.add(bladeRight.scale(across * BLADE_HALF_WIDTH / 2.0));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        private static void clearSoftBlocks(net.minecraft.server.level.ServerPlayer player, ServerLevel level,
                                             Vec3 start, Vec3 end, Vec3 direction,
                                             Vec3 bladeRight, Vec3 bladeNormal, int milestone) {
            var settings = AeromanipConfig.settings(player);
            if (!settings.allowSoftBlockInteraction
                    || !DestroyBlocksSetting.canDestroyBlocks(player, Skills.LAMINAR_CUTTER.get())) return;
            var min = new Vec3(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
            var max = new Vec3(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
            var padding = Mth.ceil(BLADE_HALF_WIDTH) + 1;
            var from = BlockPos.containing(min).offset(-padding, -2, -padding);
            var to = BlockPos.containing(max).offset(padding, 2, padding);
            for (var pos : BlockPos.betweenClosed(from, to)) {
                var center = Vec3.atCenterOf(pos);
                var relative = center.subtract(start);
                var projection = relative.dot(direction);
                if (projection < -0.5 || projection > start.distanceTo(end) + 0.5) continue;
                if (Math.abs(relative.dot(bladeRight)) > BLADE_HALF_WIDTH + 0.5) continue;
                if (Math.abs(relative.dot(bladeNormal)) > BLADE_HALF_THICKNESS + 0.5) continue;
                var state = level.getBlockState(pos);
                if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                    level.removeBlock(pos, false);
                    continue;
                }
                var predefinedSoft = state.is(Blocks.COBWEB)
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
                        || state.is(BlockTags.LEAVES);
                if (!predefinedSoft && (milestone < 2 || state.getDestroySpeed(level, pos) < 0.0f
                        || state.getDestroySpeed(level, pos) > 1.5f)) continue;
                level.destroyBlock(pos.immutable(), true, player);
            }
        }

        private static void applyFracture(net.minecraft.server.level.ServerPlayer owner, LivingEntity target) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null) return;
            var amount = target instanceof Player ? -0.1 : -0.2;
            var current = armor.getModifier(LAMINAR_FRACTURE_ID);
            if (current != null) armor.removeModifier(LAMINAR_FRACTURE_ID);
            armor.addTransientModifier(new AttributeModifier(
                    LAMINAR_FRACTURE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            var skill = Skills.LAMINAR_CUTTER.get();
            TimedSkillEffectRuntime.put(owner, target.getUUID(), skill, "fracture", 100, (float) -amount);
            TimedSkillEffectRuntime.schedule(owner, 100, () -> {
                if (TimedSkillEffectRuntime.get(owner.getUUID(), target.getUUID(), skill,
                        "fracture", owner.level().getGameTime()).isEmpty()) {
                    var targetArmor = target.getAttribute(Attributes.ARMOR);
                    if (targetArmor != null) targetArmor.removeModifier(LAMINAR_FRACTURE_ID);
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);

        private CastPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.LAMINAR_CUTTER_CAST.get();
        }
    }
}
