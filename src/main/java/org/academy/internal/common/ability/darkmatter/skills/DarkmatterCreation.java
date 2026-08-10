package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
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
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.DarkmatterCreationData;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.UUID;

public final class DarkmatterCreation extends Skill {
    public static final int MAX_BEETLES = 8;
    public static final float RESERVED_CP_PER_BEETLE = 20.0f;

    public DarkmatterCreation() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .cpCost(80)
                .iterationTicks(40)
                .maxStacks(2)
                .dependsOn(Skills.DARKMATTER_REPAIR)
                .withCustomData(DarkmatterCreationData.ID, DarkmatterCreationData.class,
                        player -> new DarkmatterCreationData())
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Repair", "academy:darkmatter_repair"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputConstants.RELEASE, 0)
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
                        Skills.DARKMATTER_CREATION.get(),
                        List.of(DarkmatterRepair.Client.SKILL_INFO),
                        R.textures.darkmatter_creation_icon,
                        230,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_CREATION + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_CREATION.get())) return;
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

            var lookedAt = findLookedAtOwned(player);
            if (lookedAt != null) {
                lookedAt.discard();
                return;
            }

            var skill = Skills.DARKMATTER_CREATION.get();
            var ids = owned(player);
            if (ids.size() >= MAX_BEETLES) return;
            var system = AbilitySystemServer.getSystem(player);
            var baseCast = skill.getCpCost(skill.getLevel(player));
            var cast = DarkmatterSixWings.Server.adjustCategoryCost(
                    player, skill, baseCast, skill.getCpCost(player));
            var maintenance = adjustedMaintenance(player, 1);
            var required = (cast + maintenance)
                    * system.getPlayerCalculationIntensity(player.getUUID());
            if (system.getPlayerAvailableCP(player.getUUID()) + 1.0e-5f < required) return;

            skill.executeActive(player, (context, actualCost) -> {
                if (owned(player).size() >= MAX_BEETLES) return;
                var beetle = EntityTypes.DARKMATTER_BEETLE.get().create(
                        level, EntitySpawnReason.MOB_SUMMONED);
                if (beetle == null) return;
                beetle.setOwnerUUID(player.getUUID());
                var spawn = player.position().add(player.getLookAngle().scale(1.5));
                beetle.snapTo(spawn.x, player.getY(), spawn.z, player.getYRot(), 0);
                if (!level.noCollision(beetle) || !level.addFreshEntity(beetle)) return;
                addOwned(player, beetle.getUUID());
                if (!syncReservation(player)) beetle.discard();
            });
        }

        private static DarkmatterBeetle findLookedAtOwned(ServerPlayer player) {
            var start = player.getEyePosition();
            var direction = player.getLookAngle().normalize();
            var end = start.add(direction.scale(16));
            var search = player.getBoundingBox().expandTowards(direction.scale(16)).inflate(1.5);
            var hit = ProjectileUtil.getEntityHitResult(player, start, end, search,
                    entity -> entity instanceof DarkmatterBeetle beetle
                            && beetle.isOwnedBy(player) && entity.isPickable(), 16 * 16);
            if (hit == null || hit.getType() == HitResult.Type.MISS) return null;
            return hit.getEntity() instanceof DarkmatterBeetle beetle ? beetle : null;
        }

        public static List<UUID> owned(ServerPlayer player) {
            return Skills.DARKMATTER_CREATION.get()
                    .<DarkmatterCreationData>getRuntimeData(player)
                    .map(DarkmatterCreationData::getOwnedBeetles)
                    .orElseGet(List::of);
        }

        private static void addOwned(ServerPlayer player, UUID uuid) {
            AbilitySystemServer.getSystem(player).updatePlayerSkillData(
                    player.getUUID(), Skills.DARKMATTER_CREATION.get(),
                    DarkmatterCreationData.class, data -> data.add(uuid));
        }

        public static void removeOwned(ServerPlayer player, UUID uuid) {
            AbilitySystemServer.getSystem(player).updatePlayerSkillData(
                    player.getUUID(), Skills.DARKMATTER_CREATION.get(),
                    DarkmatterCreationData.class, data -> data.remove(uuid));
            syncReservation(player);
        }

        private static boolean syncReservation(ServerPlayer player) {
            var system = AbilitySystemServer.getSystem(player);
            var count = owned(player).size();
            if (count == 0) {
                system.releaseMaintenanceOccupation(player.getUUID(),
                        Skills.DARKMATTER_CREATION.get().getKeyString());
                return true;
            }
            var skill = Skills.DARKMATTER_CREATION.get();
            return system.ensurePermanentOccupation(player.getUUID(),
                    adjustedMaintenance(player, count), skill);
        }

        private static float adjustedMaintenance(ServerPlayer player, int count) {
            var skill = Skills.DARKMATTER_CREATION.get();
            var base = RESERVED_CP_PER_BEETLE * count;
            var proficiency = skill.adjustProficiencyCost(
                    player, SkillProficiencyProfile.CostKind.MAINTENANCE, base);
            return DarkmatterSixWings.Server.adjustCategoryCost(player, skill, base, proficiency);
        }

        private static void discardAll(ServerPlayer player) {
            var ids = List.copyOf(owned(player));
            var server = player.level().getServer();
            if (server != null) {
                for (var id : ids) {
                    for (var level : server.getAllLevels()) {
                        if (level.getEntity(id) instanceof DarkmatterBeetle beetle
                                && beetle.isOwnedBy(player)) beetle.discard();
                    }
                }
            }
            AbilitySystemServer.getSystem(player).updatePlayerSkillData(
                    player.getUUID(), Skills.DARKMATTER_CREATION.get(),
                    DarkmatterCreationData.class, DarkmatterCreationData::clear);
            AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                    player.getUUID(), Skills.DARKMATTER_CREATION.get().getKeyString());
        }

        private static void tick(ServerPlayer player) {
            if (player.tickCount % 20 != 0 || owned(player).isEmpty()) return;
            if (!Skills.DARKMATTER_CREATION.get().isEnabled(player)
                    || !player.isAlive() || player.hasDisconnected()) {
                discardAll(player);
                return;
            }
            if (syncReservation(player)) return;
            var ids = owned(player);
            if (ids.isEmpty()) return;
            var newest = ids.getLast();
            var server = player.level().getServer();
            if (server != null) {
                for (var level : server.getAllLevels()) {
                    if (level.getEntity(newest) instanceof DarkmatterBeetle beetle) beetle.discard();
                }
            }
            removeOwned(player, newest);
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

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.discardAll(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);
        private CastPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CREATION_CAST.get();
        }
    }
}
