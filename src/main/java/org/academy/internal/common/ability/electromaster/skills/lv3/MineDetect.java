package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.Render;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.awt.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MineDetect extends Skill {
    static final int RADIUS = 64;
    static final int RADIUS_SQUARED = RADIUS * RADIUS;
    private static final int POSITIONS_PER_TICK = 32_768;
    private static final int RESCAN_DISTANCE = RADIUS / 2;
    private static final int PERIODIC_RESCAN_TICKS = 100;

    public MineDetect() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(5_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MAGNET_MANIPULATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    static boolean isInsideScanRadius(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz <= RADIUS_SQUARED;
    }

    static boolean isOrePath(String path) {
        return path.endsWith("_ore") || path.contains("ore_") || path.contains("_ore_");
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
                                InputConstants.KEY_M,
                                InputConstants.RELEASE,
                                InputConstants.MOD_ALT
                        )
                ),
                _ -> Client.toggle()
        );
        NeoForge.EVENT_BUS.register(Client.class);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MINE_DETECT.get(),
                        List.of(MagnetManipulation.Client.SKILL_INFO),
                        R.textures.mine_detect_icon,
                        96,
                        24
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.MINE_DETECT + "_toggle";
        private static final TagKey<Block> FORGE_ORES = TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath("forge", "ores")
        );
        private static final TagKey<Block> COMMON_ORES = TagKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath("c", "ores")
        );
        private static final LongOpenHashSet ORES = new LongOpenHashSet();
        private static final Map<Block, Boolean> ORE_CACHE = new IdentityHashMap<>();
        private static final int[][] EDGES = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        public static Config CONFIG = new Config();

        private static ClientLevel scanLevel;
        private static BlockPos scanCenter;
        private static long nextPeriodicScan;
        private static boolean active;
        private static boolean scanning;
        private static int minX;
        private static int maxX;
        private static int minY;
        private static int maxY;
        private static int minZ;
        private static int maxZ;
        private static int x;
        private static int y;
        private static int z;
        private static int scanRadius = RADIUS;

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.MINE_DETECT.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            var player = minecraft.player;
            var skill = Skills.MINE_DETECT.get();
            if (level == null || player == null || !AbilitySystemClient.canUseSkill(skill)) {
                if (active) reset();
                return;
            }

            active = true;
            var playerPos = player.blockPosition();
            var milestone = AbilitySystemClient.getSkillProficiencyMilestone(skill);
            var desiredRadius = milestone >= 2 ? 80 : RADIUS;
            if (level != scanLevel
                    || scanCenter == null
                    || desiredRadius != scanRadius
                    || scanCenter.distSqr(playerPos) > square(desiredRadius / 2)
                    || !scanning && level.getGameTime() >= nextPeriodicScan) {
                beginScan(level, playerPos, desiredRadius);
            }
            scanBatch(level);
        }

        @SubscribeEvent
        public static void onLevelRender(LevelRenderEvent event) {
            if (!active || ORES.isEmpty()) return;

            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) return;

            var renderType = Render.RenderTypes.MINE_DETECT_LINES;
            var cameraPos = minecraft.gameRenderer.mainCamera().position();
            var matrixStack = event.getMatrixStack();
            var lineWidth = minecraft.getWindow().getAppropriateLineWidth();

            matrixStack.pushPose();
            matrixStack.translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
            event.submitCustomGeometry(renderType, (snapshot, consumer) -> {
                for (var iterator = ORES.iterator(); iterator.hasNext(); ) {
                    var pos = BlockPos.of(iterator.nextLong());
                    var color = oreColor(minecraft.level.getBlockState(pos));
                    renderBox(snapshot, consumer,
                            new AABB(pos).inflate(0.002), lineWidth, color);
                }
            });
            matrixStack.popPose();
        }

        private static void beginScan(ClientLevel level, BlockPos center, int radius) {
            scanLevel = level;
            scanCenter = center.immutable();
            scanRadius = radius;
            ORES.clear();
            ORE_CACHE.clear();
            minX = center.getX() - radius;
            maxX = center.getX() + radius;
            minY = Math.max(level.getMinY(), center.getY() - radius);
            maxY = Math.min(level.getMaxY() - 1, center.getY() + radius);
            minZ = center.getZ() - radius;
            maxZ = center.getZ() + radius;
            x = minX;
            y = minY;
            z = minZ;
            scanning = true;
        }

        private static void scanBatch(ClientLevel level) {
            if (!scanning || scanCenter == null || level != scanLevel) return;

            var mutable = new BlockPos.MutableBlockPos();
            var processed = 0;
            var milestone = AbilitySystemClient.getSkillProficiencyMilestone(Skills.MINE_DETECT.get());
            var budget = milestone >= 2 ? POSITIONS_PER_TICK + POSITIONS_PER_TICK / 2 : POSITIONS_PER_TICK;
            while (processed < budget && scanning) {
                if (x > maxX) {
                    scanning = false;
                    nextPeriodicScan = level.getGameTime() + PERIODIC_RESCAN_TICKS;
                    break;
                }

                var dx = x - scanCenter.getX();
                var dy = y - scanCenter.getY();
                var dz = z - scanCenter.getZ();
                if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= (long) scanRadius * scanRadius) {
                    mutable.set(x, y, z);
                    if (level.isLoaded(mutable)) {
                        var state = level.getBlockState(mutable);
                        if (isOre(state)) ORES.add(mutable.asLong());
                    }
                }

                processed++;
                advanceCursor();
            }
        }

        private static void advanceCursor() {
            y++;
            if (y <= maxY) return;
            y = minY;
            z++;
            if (z <= maxZ) return;
            z = minZ;
            x++;
        }

        private static boolean isOre(BlockState state) {
            var block = state.getBlock();
            var cached = ORE_CACHE.get(block);
            if (cached != null) return cached;

            var result = state.is(FORGE_ORES) || state.is(COMMON_ORES);
            if (!result) {
                var id = BuiltInRegistries.BLOCK.getKey(block);
                result = id != null && isOrePath(id.getPath());
            }
            ORE_CACHE.put(block, result);
            return result;
        }

        private static int square(int value) {
            return value * value;
        }

        static float[] oreColor(BlockState state) {
            var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            var path = id == null ? "unknown" : id.getPath();
            if (path.contains("redstone")) return new float[]{1.0f, 0.12f, 0.08f, 0.92f};
            if (path.contains("lapis")) return new float[]{0.18f, 0.35f, 1.0f, 0.92f};
            if (path.contains("diamond")) return new float[]{0.15f, 1.0f, 0.95f, 0.92f};
            if (path.contains("emerald")) return new float[]{0.12f, 1.0f, 0.28f, 0.92f};
            if (path.contains("gold")) return new float[]{1.0f, 0.78f, 0.08f, 0.92f};
            if (path.contains("iron")) return new float[]{0.86f, 0.84f, 0.80f, 0.92f};
            if (path.contains("copper")) return new float[]{0.95f, 0.42f, 0.18f, 0.92f};
            if (path.contains("coal")) return new float[]{0.24f, 0.26f, 0.30f, 0.92f};
            if (path.contains("quartz")) return new float[]{1.0f, 0.92f, 0.82f, 0.92f};
            if (path.contains("debris")) return new float[]{0.52f, 0.25f, 0.32f, 0.92f};

            var hash = path.hashCode();
            var hue = Mth.positiveModulo(hash, 360) / 360.0f;
            var rgb = Color.HSBtoRGB(hue, 0.72f, 1.0f);
            return new float[]{
                    ((rgb >> 16) & 0xff) / 255.0f,
                    ((rgb >> 8) & 0xff) / 255.0f,
                    (rgb & 0xff) / 255.0f,
                    0.92f
            };
        }

        private static void renderBox(
                MatrixStack matrixStack,
                VertexConsumer consumer,
                AABB box,
                float lineWidth,
                float[] color
        ) {
            var vertices = new float[][]{
                    {(float) box.minX, (float) box.minY, (float) box.minZ},
                    {(float) box.maxX, (float) box.minY, (float) box.minZ},
                    {(float) box.maxX, (float) box.minY, (float) box.maxZ},
                    {(float) box.minX, (float) box.minY, (float) box.maxZ},
                    {(float) box.minX, (float) box.maxY, (float) box.minZ},
                    {(float) box.maxX, (float) box.maxY, (float) box.minZ},
                    {(float) box.maxX, (float) box.maxY, (float) box.maxZ},
                    {(float) box.minX, (float) box.maxY, (float) box.maxZ}
            };
            var matrix = matrixStack.lastMatrix();
            for (var edge : EDGES) {
                var first = vertices[edge[0]];
                var second = vertices[edge[1]];
                consumer.addVertex(matrix, first[0], first[1], first[2])
                        .setColor(color[0], color[1], color[2], color[3])
                        .setNormal(0, 1, 0)
                        .setLineWidth(lineWidth);
                consumer.addVertex(matrix, second[0], second[1], second[2])
                        .setColor(color[0], color[1], color[2], color[3])
                        .setNormal(0, 1, 0)
                        .setLineWidth(lineWidth);
            }
        }

        private static void reset() {
            active = false;
            scanning = false;
            scanLevel = null;
            scanCenter = null;
            nextPeriodicScan = 0;
            ORES.clear();
            ORE_CACHE.clear();
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
            Skills.MINE_DETECT.get().toggle(packet.getPacketListener().getPlayer());
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.MINE_DETECT.get();
            if (!skill.isEnabled(player)) return;

            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            if (!system.ensurePermanentOccupation(
                    uuid,
                    skill.getMaintenanceCost(player),
                    skill
            )) {
                system.toggleSkill(uuid, skill.getKeyString());
            }
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
            return PacketTypes.MINE_DETECT_TOGGLE.get();
        }
    }
}
