package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.academy.internal.common.ability.builtin.electromaster.skills.Railgun.Client.KEY_NAME;

public class Railgun extends Skill {
    public static final Skill INSTANCE = new Railgun();

    private Railgun() {
        super(SkillNames.RAILGUN, 5, 15000, List.of(ArcGenerate.INSTANCE));
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.C2S_PACKET_HANDLER_MAP.put(Packets.C2S_RAILGUN_SHOOT,
                (serverPacketListener, packet) ->
                        Server.handleShoot(serverPacketListener.player)
        );
    }

    @Override
    public void initClient() {
        AcademyCraftClient.CLIENT_CONFIG.setSkillClientConfig(INSTANCE.name, Client.CLIENT_CONFIG);
        InputSystem.addKeyBinding(KEY_NAME, Client.CLIENT_CONFIG.getKeyBinding(KEY_NAME,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>())
                )
        ), Client::handleKey);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(),
                        TextureResources.TEXTURE_RAILGUN_ICON, 200, 70.25f);
        public static final String KEY_NAME = SkillNames.RAILGUN + ".shoot";
        public static RailgunClientConfig CLIENT_CONFIG = new RailgunClientConfig();

        public static void handleKey() {
            //       if (ClientUtil.hasScreen() || ClientUtil.lacksSkill(INSTANCE)) return;
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_RAILGUN_SHOOT));
        }

        public static final class RailgunClientConfig extends SkillClientConfig.KeyBindingConfig {
        }
    }

    @SuppressWarnings("resource")
    public static final class Server {
        public static void handleShoot(final @NotNull Player player) {
            //      if (ServerUtil.lacksSkill(player.getUUID(), INSTANCE)) return;
            final UUID uuid = player.getUUID();
            final float computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);
            if (computingPower < 100) {
                return;
            }
            EntityType<?> targetEntity = EntityTypes.THROWN_COIN_ENTITY_TYPE;

            Vec3 lookVec = player.getLookAngle().scale(0.25);
            BlockPos checkPos = new BlockPos((int) (lookVec.x + player.getX()),
                    (int) (lookVec.y + player.getEyeY()),
                    (int) (lookVec.z + player.getZ()));

            Vec3 boxSize = new Vec3(2, 2, 2);
            AABB box = new AABB(checkPos)
                    .inflate(boxSize.x / 2, boxSize.y / 2, boxSize.z / 2);
            List<Entity> entities = player.level().getEntities(player, box, entity -> entity.getType() == targetEntity);
            if (!entities.isEmpty()) {
                if (!AcademyCraft.DEBUG_MODE) {
                    AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
                }
                player.sendSystemMessage(Component.literal("Yes"));
                ThrownCoin coin = (ThrownCoin) entities.get(0);
                RailgunRay railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY_ENTITY_TYPE, player.level());
                coin.kill();

                float length = 50;
                Vec3 startPos = new Vec3(coin.getX(), coin.getY(), coin.getZ());
                Vec3 endPos = startPos.add(player.getLookAngle().scale(length));
                railgunRay.setPos(startPos);
                railgunRay.setYRot(player.getYRot());
                railgunRay.setXRot(player.getXRot());
                player.level().addFreshEntity(railgunRay);

                Pair<Boolean, Double> result = LevelUtil.destroyBlocksAlongPath(railgunRay.level(), startPos, endPos, 0.025f, 10, false, true, true, false);
                if (result.getKey()) {
                    double d = result.getValue();
                    length = (float) d;
                    endPos = startPos.add(player.getLookAngle().scale(length));
                }
                LevelUtil.attackEntitiesAlongPath(railgunRay.level(), startPos, endPos, 0.125f, new DamageSource(railgunRay.level().damageSources().damageTypes.getHolderOrThrow(DamageTypes.MOB_ATTACK), railgunRay), 150);
                railgunRay.playSound(AcademyCraftSoundEvents.RAILGUN);
            } else {
                player.sendSystemMessage(Component.literal("No"));
            }
        }
    }
}