package org.academy.internal.common.ability.builtin.electromaster.skills;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AbilitySystemServer;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.academy.api.server.network.AcademyCraftNetworkSystemServer;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Railgun extends Skill {
    public static final Skill INSTANCE = new Railgun();
    public static final String KEY_NAME = "railgun.shoot";

    private Railgun() {
        super("railgun", 5);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftNetworkSystemServer.CLIENT_TO_SERVER_PACKET_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_RAILGUN_SHOOT_PACKET, (serverPacketListener, packet) -> Server.handleShoot((serverPacketListener).player));
    }

    @Override
    public void initClient() {
        Client.KEY = AcademyCraftClient.clientConfig.getKey(KEY_NAME,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>()
                )));

        InputSystem.registerKeyBinding(KEY_NAME, Client.KEY, Client::handleKey);
    }

    public static final class Client {
        public static AcademyCraftClientConfig.InputPair KEY;

        public static void handleKey() {
            if (ClientUtil.isScreenNull()) {
                AcademyCraftNetworkSystemClient.sendPacket(new ClientToServerPacket(AcademyCraftNetworkResourceLocations.C2S_RAILGUN_SHOOT_PACKET, new FriendlyByteBuf(Unpooled.buffer())));
            }
        }
    }

    @SuppressWarnings("resource")
    public static final class Server {
        public static void handleShoot(final @NotNull Player player) {
            final UUID uuid = player.getUUID();
            final float computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);
            if (computingPower < 100) {
                return;
            }
            EntityType<?> targetEntity = AcademyCraftEntityTypes.THROWN_COIN_ENTITY_TYPE;
            Vec3 lookVec = player.getLookAngle().scale(2);
            BlockPos pos = new BlockPos((int) (lookVec.x + player.getX()), (int) (lookVec.y + player.getEyeY()), (int) (lookVec.z + player.getZ()));

            AABB box = new AABB(pos).inflate(2);
            List<Entity> entities = player.level().getEntities(player, box, entity -> entity.getType() == targetEntity);
            if (!entities.isEmpty()) {
                if (!AcademyCraft.DEBUG_MODE) {
                    AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
                }
                player.sendSystemMessage(Component.literal("Yes"));
                ThrownCoin coin = (ThrownCoin) entities.get(0);
                coin.damage = computingPower;
                coin.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 10F, 0);
                RailgunRay railgunRay = new RailgunRay(AcademyCraftEntityTypes.RAILGUN_RAY_ENTITY_TYPE, player.level());
                railgunRay.setPos(player.getEyePosition().add(0, -0.5, 0));
                railgunRay.setYRot(player.getYRot());
                railgunRay.setXRot(player.getXRot());

                player.level().addFreshEntity(railgunRay);
                railgunRay.playSound(AcademyCraftSoundEvents.RAILGUN);
            } else {
                player.sendSystemMessage(Component.literal("No"));
            }
        }
    }
}