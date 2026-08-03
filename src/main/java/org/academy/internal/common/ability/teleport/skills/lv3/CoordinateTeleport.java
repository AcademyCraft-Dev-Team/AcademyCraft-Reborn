package org.academy.internal.common.ability.teleport.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.TeleportChunkForceManager;
import org.academy.internal.common.ability.teleport.TeleportSafety;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.CoordinateTeleportData;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Map;
import java.util.WeakHashMap;

public class CoordinateTeleport extends Skill {
    private static final int COMPUTE_TICKS = 200;

    public CoordinateTeleport() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .cpCost(0)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.SPATIAL_SYNERGY)
                .dependsOn(Skills.CUT_THROUGH)
                .withCustomData(CoordinateTeleportData.ID, CoordinateTeleportData.class,
                        player -> new CoordinateTeleportData())
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_SAVE, Client.CONFIG.getKeyBinding(Client.KEY_SAVE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_T, InputConstants.PRESS, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT))
                , ctx -> Client.onSave());

        InputSystem.addKeyBinding(Client.KEY_TP, Client.CONFIG.getKeyBinding(Client.KEY_TP,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_Y, InputConstants.PRESS, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT))
                , ctx -> Client.onTeleport());
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_SAVE = SkillNames.COORDINATE_TELEPORT + "_save";
        public static final String KEY_TP = SkillNames.COORDINATE_TELEPORT + "_tp";
        public static Config CONFIG = new Config();

        public static void onSave() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.gui.screen() != null
                    || !org.academy.api.client.ability.AbilitySystemClient.canUseSkill(
                    Skills.COORDINATE_TELEPORT.get())) return;
            MisakaNetworkClient.send(SavePositionPacket.INSTANCE);
        }

        public static void onTeleport() {
            MisakaNetworkClient.send(new RequestTeleportPacket());
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public CoordinateTeleport.Client.Config getDefault() {
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
        private static final Map<ServerPlayer, ComputeContext> COMPUTE_MAP = new WeakHashMap<>();

        @SubscribePacket
        public static void handleSave(SavePositionPacket p) {
            var player = p.getPacketListener().getPlayer();
            var skill = Skills.COORDINATE_TELEPORT.get();
            if (!skill.isEnabled(player)) return;
            var pos = player.position();
            var dim = player.level().dimension().toString();
            var name = String.format("(%.0f, %.0f, %.0f)", pos.x, pos.y, pos.z);
            var updated = AbilitySystemServer.getSystem(player).updatePlayerSkillData(
                    player.getUUID(), skill, CoordinateTeleportData.class,
                    data -> data.addPosition(new CoordinateTeleportData.SavedPosition(
                            name, pos.x, pos.y, pos.z, dim))
            );
            if (updated) player.sendSystemMessage(Component.translatable("academy.coordinate.saved", name));
        }

        @SubscribePacket
        public static void handleRequestTeleport(RequestTeleportPacket p) {
            var player = p.getPacketListener().getPlayer();
            var data = Skills.COORDINATE_TELEPORT.get()
                    .<CoordinateTeleportData>getRuntimeData(player).orElse(null);
            if (data == null || data.getSavedPositions().isEmpty()) {
                player.sendSystemMessage(Component.translatable("academy.coordinate.no_saved"));
                return;
            }

            var existing = COMPUTE_MAP.get(player);
            if (existing != null) {
                existing.end();
            }

            var lastPos = data.getSavedPositions().getLast();
            Skills.COORDINATE_TELEPORT.get().executeActive(player, (ctx, c) -> {
                var computeCtx = new ComputeContext(player, lastPos);
                COMPUTE_MAP.put(player, computeCtx);
                AbilitySystemServer.registerContext(computeCtx);
            });
        }
    }

    public static final class ComputeContext extends ServerContext {
        private final CoordinateTeleportData.SavedPosition target;
        private final Vec3 startPos;
        private int ticks;
        private boolean ended;

        private ComputeContext(ServerPlayer player, CoordinateTeleportData.SavedPosition target) {
            super(player);
            this.target = target;
            startPos = player.position();
            player.sendSystemMessage(Component.translatable("academy.coordinate.computing"));
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre ev) {
            ticks++;
            if (ended || player.hasDisconnected() || !player.isAlive()) {
                end();
                return;
            }

            if (player.position().distanceToSqr(startPos) > 0.25) {
                player.sendSystemMessage(Component.translatable("academy.coordinate.cancelled"));
                end();
                return;
            }

            if (ticks >= COMPUTE_TICKS) {
                var destinationLevel = resolveLevel();
                if (destinationLevel == null) {
                    player.sendSystemMessage(Component.translatable("academy.coordinate.invalid"));
                    end();
                    return;
                }
                var operation = "coordinate_" + player.getStringUUID();
                TeleportChunkForceManager.forceChunk(destinationLevel, operation,
                        (int) Math.floor(target.x()),
                        (int) Math.floor(target.z()),
                        TeleportChunkForceManager.DEFAULT_TIMEOUT_TICKS);
                destinationLevel.getChunk(
                        ((int) Math.floor(target.x())) >> 4,
                        ((int) Math.floor(target.z())) >> 4
                );
                var safe = TeleportSafety.findSafe(
                        player, destinationLevel, new Vec3(target.x(), target.y(), target.z())
                );
                if (safe == null) {
                    player.sendSystemMessage(Component.translatable("academy.coordinate.invalid"));
                    end();
                    return;
                }
                player.teleportTo(destinationLevel, safe.x, safe.y, safe.z,
                        java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                player.resetFallDistance();
                player.sendSystemMessage(Component.translatable("academy.coordinate.teleported"));
                end();
            }
        }

        private ServerLevel resolveLevel() {
            var id = Identifier.tryParse(target.dimension());
            return id == null ? null : player.level().getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, id)
            );
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.COMPUTE_MAP.remove(player);
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SavePositionPacket extends Packet<ServerGamePacketListenerImpl, SavePositionPacket> {
        public static final SavePositionPacket INSTANCE = new SavePositionPacket();
        public static final StreamCodec<ByteBuf, SavePositionPacket> CODEC = StreamCodec.unit(INSTANCE);

        private SavePositionPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SavePositionPacket> getPacketType() {
            return PacketTypes.COORDINATE_TELEPORT_SAVE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RequestTeleportPacket extends Packet<ServerGamePacketListenerImpl, RequestTeleportPacket> {
        public static final RequestTeleportPacket INSTANCE = new RequestTeleportPacket();
        public static final StreamCodec<ByteBuf, RequestTeleportPacket> CODEC = StreamCodec.unit(INSTANCE);

        private RequestTeleportPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, RequestTeleportPacket> getPacketType() {
            return PacketTypes.COORDINATE_TELEPORT_REQUEST.get();
        }
    }
}
