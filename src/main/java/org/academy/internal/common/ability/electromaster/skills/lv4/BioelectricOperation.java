package org.academy.internal.common.ability.electromaster.skills.lv4;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.BioelectricSurgeData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.server.world.level.storage.SkillDataSerializer;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class BioelectricOperation extends Skill {
    private static final Identifier MODIFIER_ID = AcademyCraft.academy(SkillNames.BIOELECTRIC_OPERATION);
    private static final String LEGACY_BIOELECTRIC_SURGE = "academy:bioelectric_surge";

    static {
        SkillDataSerializer.registerType(BioelectricSurgeData.ID, BioelectricSurgeData.class);
    }

    public BioelectricOperation() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(40)
                .iterationTicks(40)
                .dependsOn(Skills.ELECTRICAL_CONTACT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
        );
    }

    static double getAttackDamageBonus(float abilityPower) {
        return 4.0 * Math.max(0, abilityPower);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(
                                InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_Y,
                                InputConstants.RELEASE,
                                0
                        )
                ),
                _ -> Client.toggle()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.BIOELECTRIC_OPERATION.get(),
                        List.of(),
                        R.textures.bioelectric_operation_icon,
                        184,
                        46
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.BIOELECTRIC_OPERATION + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.BIOELECTRIC_OPERATION.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
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
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            Skills.BIOELECTRIC_OPERATION.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final Set<ServerPlayer> MIGRATED_PLAYERS =
                Collections.newSetFromMap(new WeakHashMap<>());

        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            migrateLegacySkill(player);
            var skill = Skills.BIOELECTRIC_OPERATION.get();
            var system = AbilitySystemServer.getSystem(player);
            var enabled = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (enabled) {
                enabled = system.ensurePermanentOccupation(
                        player.getUUID(),
                        skill.getMaintenanceCost(skill.getLevel(player)),
                        skill
                );
                if (!enabled) {
                    system.toggleSkill(player.getUUID(), skill.getKeyString());
                }
            }

            if (enabled) {
                if (player.tickCount % 40 == 0) spawnBioelectricArcs(player);
            }

            var power = enabled
                    ? system.getPlayerAbilityPowerMultiplier(player.getUUID())
                    : 0;
            syncModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), 0.1, enabled);
            syncModifier(player.getAttribute(Attributes.STEP_HEIGHT), 0.4, enabled);
            syncModifier(player.getAttribute(Attributes.MOVEMENT_EFFICIENCY), 1.0, enabled);
            syncModifier(player.getAttribute(Attributes.JUMP_STRENGTH), 0.58, enabled);
            syncModifier(player.getAttribute(Attributes.ATTACK_SPEED), 2.4, enabled);
            syncModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), getAttackDamageBonus(power), enabled);
            syncModifier(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), 0.5, enabled);
            syncModifier(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), 10.0, enabled);
        }

        private static void spawnBioelectricArcs(ServerPlayer player) {
            var position = player.position();
            var horizontalLook = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
            if (horizontalLook.lengthSqr() <= 1.0E-6) {
                horizontalLook = Vec3.directionFromRotation(0, player.getYRot());
            } else {
                horizontalLook = horizontalLook.normalize();
            }
            var left = new Vec3(-horizontalLook.z, 0, horizontalLook.x);
            var right = left.scale(-1);
            var height = player.getBbHeight();
            var sideOffset = player.getBbWidth() * 0.5 + 0.65;

            var leftUp = position.add(0, height * 0.85, 0).add(left.scale(sideOffset));
            spawnArc(player, leftUp, leftUp.add(left.scale(0.8)).add(0, 0.45, 0));
            var leftDown = position.add(0, height * 0.15, 0).add(left.scale(sideOffset));
            spawnArc(player, leftDown, leftDown.add(left.scale(0.8)).add(0, -0.45, 0));
            var rightUp = position.add(0, height * 0.85, 0).add(right.scale(sideOffset));
            spawnArc(player, rightUp, rightUp.add(right.scale(0.8)).add(0, 0.45, 0));
            var rightDown = position.add(0, height * 0.15, 0).add(right.scale(sideOffset));
            spawnArc(player, rightDown, rightDown.add(right.scale(0.8)).add(0, -0.45, 0));
        }

        private static void spawnArc(ServerPlayer player, Vec3 start, Vec3 end) {
            var paths = List.of(ElectromasterArcEffects.arc(start, end, MathUtil.RANDOM.nextLong()));
            ElectromasterArcEffects.spawnArc(player.level(), paths, 8, start);
        }

        private static void migrateLegacySkill(ServerPlayer player) {
            if (MIGRATED_PLAYERS.contains(player)) return;
            var system = AbilitySystemServer.getSystem(player);
            var playerData = system.getPlayerData(player.getUUID());
            if (playerData == null) return;
            var map = playerData.getSkillDataMap();
            var legacy = map.remove(LEGACY_BIOELECTRIC_SURGE);
            if (legacy != null) {
                var skill = Skills.BIOELECTRIC_OPERATION.get();
                var target = map.get(skill.getKeyString());
                if (target == null) {
                    target = skill.createData(player);
                    map.put(skill.getKeyString(), target);
                }
                mergeProgress(target, legacy);
                target.setEnabled(target.isEnabled() || legacy.isEnabled());
                playerData.markDirty();
                system.releaseMaintenanceOccupation(player.getUUID(), LEGACY_BIOELECTRIC_SURGE);
                system.getSyncManager().schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
            }
            MIGRATED_PLAYERS.add(player);
        }

        private static void mergeProgress(SkillData target, SkillData legacy) {
            target.setProficiency(Math.max(target.getProficiency(), legacy.getProficiency()));
        }

        private static void syncModifier(AttributeInstance attribute, double amount, boolean enabled) {
            if (attribute == null) return;
            var current = attribute.getModifier(MODIFIER_ID);
            if (!enabled) {
                if (current != null) attribute.removeModifier(MODIFIER_ID);
                return;
            }
            if (current != null && Double.compare(current.amount(), amount) == 0) return;
            if (current != null) attribute.removeModifier(MODIFIER_ID);
            attribute.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID,
                    amount,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.BIOELECTRIC_OPERATION_TOGGLE.get();
        }
    }
}
