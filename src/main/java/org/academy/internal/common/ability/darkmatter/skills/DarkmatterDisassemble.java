package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class DarkmatterDisassemble extends Skill {
    static final double RANGE = 32.0;
    static final float BASE_DAMAGE = 12.0f;

    public DarkmatterDisassemble() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(30)
                .iterationTicks(40)
                .maxStacks(1)
                .dependsOn(Skills.DARKMATTER_SHAPING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Shaping", "academy:darkmatter_shaping"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.MOUSE, InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.cast());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_DISASSEMBLE.get(),
                        List.of(DarkmatterShaping.Client.SKILL_INFO),
                        R.textures.darkmatter_disassemble_icon,
                        20,
                        72
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_DISASSEMBLE + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_DISASSEMBLE.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {
                }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) return;
            var hit = pick(level, player);
            if (hit.entity != null) {
                attack(player, level, hit.entity);
            } else if (hit.block != null && canDestroy(level, player, hit.block.getBlockPos())) {
                destroy(player, level, hit.block.getBlockPos());
            }
        }

        private static Hit pick(ServerLevel level, ServerPlayer player) {
            var start = player.getEyePosition();
            var direction = player.getLookAngle().normalize();
            var end = start.add(direction.scale(RANGE));
            var block = level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            var bestDistance = block.getType() == HitResult.Type.MISS
                    ? RANGE * RANGE
                    : start.distanceToSqr(block.getLocation());
            LivingEntity best = null;
            var search = new AABB(start, end).inflate(1.0);
            for (var candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                    target -> validTarget(player, target))) {
                var clipped = candidate.getBoundingBox().inflate(0.3).clip(start, end);
                if (clipped.isEmpty()) continue;
                var distance = start.distanceToSqr(clipped.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            return new Hit(best, block.getType() == HitResult.Type.MISS ? null : block);
        }

        static boolean validTarget(ServerPlayer player, LivingEntity target) {
            return target != player && target.isAlive() && !target.isRemoved()
                    && !player.isAlliedTo(target);
        }

        private static void attack(ServerPlayer player, ServerLevel level, LivingEntity target) {
            var skill = Skills.DARKMATTER_DISASSEMBLE.get();
            var targets = DarkmatterSixWings.Server.isActive(player)
                    ? level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(3), candidate -> validTarget(player, candidate))
                    : List.of(target);
            skill.executeActive(player, (context, actualCost) -> {
                var multiplier = AbilitySystemServer.getSystem(player)
                        .getPlayerDamageMultiplier(player.getUUID());
                var source = SkillDamageSource.of(player, skill);
                for (var current : targets) {
                    if (!current.hurtServer(level, source, BASE_DAMAGE * multiplier)) continue;
                    level.sendParticles(ParticleTypes.CLOUD,
                            current.getX(), current.getY() + current.getBbHeight() * 0.5, current.getZ(),
                            16, 0.45, 0.45, 0.45, 0.03);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.GRAVEL_BREAK,
                        SoundSource.PLAYERS, 1.0f, 0.95f);
            });
        }

        private static boolean canDestroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) return false;
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
            var restricted = player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
                    || state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks();
            var event = new BreakBlockEvent(level, pos.immutable(), state, player);
            event.setCanceled(restricted);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }

        private static void destroy(ServerPlayer player, ServerLevel level, BlockPos pos) {
            if (!DestroyBlocksSetting.canDestroyBlocks(player)) return;
            Skills.DARKMATTER_DISASSEMBLE.get().executeActive(player, (context, actualCost) -> {
                var destroyed = false;
                if (DarkmatterSixWings.Server.isActive(player)) {
                    for (var candidate : BlockPos.betweenClosed(pos.offset(-3, -3, -3),
                            pos.offset(3, 3, 3))) {
                        if (candidate.distSqr(pos) > 9 || !canDestroy(level, player, candidate)) continue;
                        destroyed |= level.destroyBlock(candidate.immutable(), true, player);
                    }
                } else if (canDestroy(level, player, pos)) {
                    destroyed = level.destroyBlock(pos, true, player);
                }
                if (!destroyed) return;
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(pos).inflate(DarkmatterSixWings.Server.isActive(player) ? 4.5 : 1.5),
                        entity -> entity.isAlive())) {
                    item.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
                    item.setDeltaMovement(Vec3.ZERO);
                }
                level.playSound(null, pos, SoundEvents.GRAVEL_BREAK,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
            });
        }

        private record Hit(LivingEntity entity, BlockHitResult block) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);
        private CastPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_DISASSEMBLE_CAST.get();
        }
    }
}
