package org.academy.internal.common.ability.accelerator.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
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
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.DirStrikeBlockFx;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DirStrike extends Skill {
    public static final int EFFECT_RADIUS = 12;
    public static final int ATTACK_RADIUS = 12;
    private static final int EFFECT_RADIUS_SQUARED = EFFECT_RADIUS * EFFECT_RADIUS;
    private static final int EFFECT_MIN_Y_OFFSET = -3;
    private static final int EFFECT_MAX_Y_OFFSET = 5;
    private static final int MAX_EFFECT_BLOCKS = 96;
    private static final int EFFECT_BASE_DURATION = 18;
    private static final int EFFECT_PEAK_HOLD_TICKS = 20;
    private static final float EFFECT_BASE_PEAK = 0.38f;
    private static final float BASE_DAMAGE = 12.0f;

    public DirStrike() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(20)
                .iterationTicks(5)
                .maxStacks(1)
                .dependsOn(Skills.VECTOR_BLAST)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition("Vector Blast", "academy:vector_blast"))
        );
    }

    public static boolean isInsideAttackRadius(double xOffset, double zOffset) {
        return xOffset * xOffset + zOffset * zOffset <= ATTACK_RADIUS * ATTACK_RADIUS;
    }

    public static float getDamage(float abilityPower, float damageMultiplier) {
        return BASE_DAMAGE * Math.max(0.0f, abilityPower) * Math.max(0.0f, damageMultiplier);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_S,
                                InputConstants.PRESS, InputConstants.MOD_ALT)),
                ctx -> Client.onAction());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DIR_STRIKE.get(),
                        List.of(VectorBlast.Client.SKILL_INFO),
                        R.textures.dir_strike_icon,
                        100,
                        110
                )
        );
        public static final String KEY_NAME = SkillNames.DIR_STRIKE + "_use";
        public static Config CONFIG = new Config();

        private Client() {
        }

        public static void onAction() {
            MisakaNetworkClient.send(ActionPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
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
        private Server() {
        }

        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.DIR_STRIKE.get();
            skill.executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                var playerPos = player.blockPosition();
                level.playSound(null, playerPos, SoundEvents.DIR_STRIKE.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                spawnGroundFx(level, player.position(), playerPos);

                var minY = playerPos.getY() + EFFECT_MIN_Y_OFFSET;
                var maxY = playerPos.getY() + EFFECT_MAX_Y_OFFSET + 1;
                var center = player.position();
                var area = new AABB(
                        center.x - ATTACK_RADIUS, minY, center.z - ATTACK_RADIUS,
                        center.x + ATTACK_RADIUS, maxY, center.z + ATTACK_RADIUS
                );
                var damage = getDamage(
                        ctx.system().getPlayerAbilityPowerMultiplier(player.getUUID()),
                        ctx.system().getPlayerDamageMultiplier(player.getUUID())
                );
                var source = SkillDamageSource.of(player, skill);
                var targets = level.getEntitiesOfClass(LivingEntity.class, area,
                        target -> target != player
                                && target.isAlive()
                                && !target.isSpectator()
                                && !player.isAlliedTo(target)
                                && target.getY() >= minY
                                && target.getY() <= maxY
                                && isInsideAttackRadius(target.getX() - center.x, target.getZ() - center.z));
                for (var target : targets) {
                    target.hurtServer(level, source, damage);
                }
            });
        }

        private static void spawnGroundFx(ServerLevel level, Vec3 playerCenter, BlockPos playerPos) {
            var candidates = new ArrayList<BlockPos>();
            for (var xOffset = -EFFECT_RADIUS; xOffset <= EFFECT_RADIUS; xOffset++) {
                for (var zOffset = -EFFECT_RADIUS; zOffset <= EFFECT_RADIUS; zOffset++) {
                    var distanceSquared = xOffset * xOffset + zOffset * zOffset;
                    if (distanceSquared > EFFECT_RADIUS_SQUARED) continue;
                    if (((xOffset + zOffset) & 1) != 0 && level.getRandom().nextFloat() < 0.45f) continue;
                    var surface = findSurfaceBlock(level, playerPos, xOffset, zOffset);
                    if (surface != null) candidates.add(surface);
                }
            }

            candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(playerCenter)));
            var limit = Math.min(MAX_EFFECT_BLOCKS, candidates.size());
            for (var index = 0; index < limit; index++) {
                var pos = candidates.get(index);
                var blockState = level.getBlockState(pos);
                var blockCenter = Vec3.atCenterOf(pos);
                var outward = blockCenter.subtract(playerCenter);
                outward = outward.lengthSqr() < 1.0E-4
                        ? new Vec3(0.0, 0.0, 1.0)
                        : outward.normalize();

                var distance = (float) Math.sqrt(pos.distToCenterSqr(playerCenter));
                var delay = Math.max(0, Mth.floor(distance * 1.1f) - 1) + level.getRandom().nextInt(2);
                var duration = EFFECT_BASE_DURATION + level.getRandom().nextInt(3);
                var peak = EFFECT_BASE_PEAK
                        + level.getRandom().nextFloat() * 0.2f
                        + Math.max(0.0f, 1.0f - distance / EFFECT_RADIUS) * 0.08f;

                var effect = new DirStrikeBlockFx(
                        EntityTypes.DIR_STRIKE_BLOCK_FX.get(), level,
                        pos, blockState, delay, duration, EFFECT_PEAK_HOLD_TICKS, peak);
                effect.setYRot((float) Math.toDegrees(Math.atan2(outward.x, outward.z)));
                level.addFreshEntity(effect);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                        pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        4, 0.18, 0.08, 0.18, 0.02);
            }
        }

        private static BlockPos findSurfaceBlock(Level level, BlockPos playerPos, int xOffset, int zOffset) {
            for (var yOffset = EFFECT_MAX_Y_OFFSET; yOffset >= EFFECT_MIN_Y_OFFSET; yOffset--) {
                var pos = playerPos.offset(xOffset, yOffset, zOffset);
                var state = level.getBlockState(pos);
                if (!isRenderableGroundBlock(level, pos, state)) continue;
                var abovePos = pos.above();
                var aboveState = level.getBlockState(abovePos);
                if (!aboveState.isAir() && !aboveState.getCollisionShape(level, abovePos).isEmpty()) continue;
                return pos;
            }
            return null;
        }

        private static boolean isRenderableGroundBlock(Level level, BlockPos pos, BlockState state) {
            return !state.isAir()
                    && !state.hasBlockEntity()
                    && state.getRenderShape() != RenderShape.INVISIBLE
                    && state.getDestroySpeed(level, pos) >= 0.0f
                    && state.getFluidState().isEmpty();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final ActionPacket INSTANCE = new ActionPacket();
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActionPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.DIR_STRIKE.get();
        }
    }
}
