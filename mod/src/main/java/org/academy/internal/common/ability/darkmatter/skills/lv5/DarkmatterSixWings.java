package org.academy.internal.common.ability.darkmatter.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.vfxgraph.instance.EffectInstanceGeometrySource;
import org.academy.api.client.render.vfxgraph.instance.InstanceGeometryFrame;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.resource.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.ability.darkmatter.targeting.DarkmatterTargeting;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.ability.SkillFlightController;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.academy.internal.client.render.vfxgraph.FrameContext;
import org.academy.internal.client.render.vfxgraph.TickingVfx;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.client.render.vfxgraph.VfxContexts;




public final class DarkmatterSixWings extends Skill {
    private static final Map<WingStrikePair, Long> NEXT_WING_STRIKE_TICK =
            new ConcurrentHashMap<>();

    private record WingStrikePair(UUID attacker, UUID target) {
    }

    public static final float MIN_RESERVED_CP = 120.0f;
    public static final float ACTIVATION_MATTER_COST = 10.0f;
    private static final Identifier FLIGHT_SOURCE =
            AcademyCraft.academy(SkillNames.DARKMATTER_SIX_WINGS);
    private static final Identifier TRUE_RESISTANCE_MODIFIER_ID =
            AcademyCraft.academy("darkmatter_six_wings_true_resistance");

    public DarkmatterSixWings() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(MIN_RESERVED_CP)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_CREATION)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Genesis", "academy:darkmatter_creation"))
        );
    }

    @Override
    public float getMaintenanceCost(ServerPlayer player) {
        var maximum = AbilitySystemServer.getSystem(player).getPlayerMaxCP(player.getUUID());
        var ratio = maintenanceRatio(getEffectiveProficiencyMilestone(player));
        return Math.max(MIN_RESERVED_CP, maximum * ratio);
    }

    static float maintenanceRatio(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 1 ? 0.30f : 0.35f;
    }

    static float activationMatterCost(int milestone) {
        return Math.clamp(milestone, 0, 3) >= 3 ? 5.0f : ACTIVATION_MATTER_COST;
    }

    @Override
    public void initClient() {
        Client.registerEffect();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_SIX_WINGS.get(),
                        List.of(DarkmatterCreation.Client.SKILL_INFO),
                        R.textures.darkmatter_six_wings_icon,
                        235,
                        72
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.DARKMATTER_SIX_WINGS + "_toggle";
        public static Config CONFIG = new Config();

        private static boolean effectRegistered;

        private Client() {
        }

        public static void registerEffect() {
            if (effectRegistered) return;
            effectRegistered = true;
            VfxContexts.register(new DarkmatterSixWingsVfx());
        }
        public static final class DarkmatterSixWingsGraphVfx {
            private static final int VERTEX_STRIDE = 5 * Float.BYTES;
            private static final int INITIAL_VERTEX_CAPACITY = 4096;
            private static final Matrix4f BASE_MATRIX = new Matrix4f().translate(0.0f, -0.20f, 0.10f);
            private static final float BASE_SCALE = 0.84f;
            private static final int IDENTITY_INSTANCE_BYTES = Float.BYTES * 16;
            private static final ByteBuffer IDENTITY_INSTANCE = BufferUtils
                    .createByteBuffer(IDENTITY_INSTANCE_BYTES).order(ByteOrder.nativeOrder());
            private static final Identifier ASSET = AcademyCraft.academy("vfxgraph/darkmatter_six_wings");

            private static final Map<Integer, ActiveEffect> EFFECTS = new HashMap<>();
            private static final Map<Integer, SixWingsGeometry> GEOMETRY = new HashMap<>();

            static {
                var scratch = new float[16];
                new Matrix4f().get(scratch);
                for (var value : scratch) IDENTITY_INSTANCE.putFloat(value);
                IDENTITY_INSTANCE.flip();
            }

            public static void sync(FrameContext ctx, Map<Integer, Matrix4f> roots) {
                var level = Minecraft.getInstance().level;
                if (level == null) return;
                var camera = ctx.cameraPos();
                for (var entry : roots.entrySet()) {
                    if (!(level.getEntity(entry.getKey()) instanceof Player player)) continue;
                    if (!player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get())) {
                        var stale = EFFECTS.remove(entry.getKey());
                        if (stale != null) stale.stop();
                        GEOMETRY.remove(entry.getKey());
                        continue;
                    }
                    var worldRoot = new Matrix4f()
                            .translation(camera.x, camera.y, camera.z)
                            .mul(entry.getValue());
                    var effect = EFFECTS.get(entry.getKey());
                    if (effect == null || effect.isStopped()) {
                        var spawned = VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f());
                        spawned.setAlwaysVisible(true);
                        var geometry = new SixWingsGeometry(entry.getKey());
                        spawned.bindInstanceGeometry(geometry);
                        GEOMETRY.put(entry.getKey(), geometry);
                        EFFECTS.put(entry.getKey(), spawned);
                        effect = spawned;
                    }
                    effect.setPosition(worldRoot.getTranslation(new Vector3f()));
                    effect.setRotation(worldRoot.getUnnormalizedRotation(new Quaternionf()));
                    effect.setScale(worldRoot.getScale(new Vector3f()).x);
                }
                var iterator = EFFECTS.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (!roots.containsKey(entry.getKey())) {
                        entry.getValue().stop();
                        GEOMETRY.remove(entry.getKey());
                        iterator.remove();
                    }
                }
            }

            private static final class SixWingsGeometry implements EffectInstanceGeometrySource {
                private final int entityId;
                private ByteBuffer vertices =
                        BufferUtils.createByteBuffer(INITIAL_VERTEX_CAPACITY * VERTEX_STRIDE);

                SixWingsGeometry(int entityId) {
                    this.entityId = entityId;
                }

                @Override
                public @Nullable InstanceGeometryFrame sample(float timeSeconds) {
                    var level = Minecraft.getInstance().level;
                    if (level == null) return null;
                    if (!(level.getEntity(entityId) instanceof Player player)) return null;
                    if (!player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get())) return null;
                    var age = player.tickCount
                            + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    var model = new AvianWingsModel(AvianWingsModel.createBodyLayer().bakeRoot());
                    var poseStack = new PoseStack();
                    poseStack.mulPose(BASE_MATRIX);
                    poseStack.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);
                    vertices.clear();
                    ensureCapacity(3072);
                    bakeWingPair(model, poseStack, vertices, age, 0.92f, -58.0f);
                    bakeWingPair(model, poseStack, vertices, age, 1.10f, 0.0f);
                    bakeWingPair(model, poseStack, vertices, age, 0.92f, 58.0f);
                    vertices.flip();
                    if (!vertices.hasRemaining()) return null;
                    return new InstanceGeometryFrame(vertices, vertices.limit() / VERTEX_STRIDE,
                            IDENTITY_INSTANCE, 1);
                }

                private void ensureCapacity(int requiredVertices) {
                    var requiredBytes = (long) requiredVertices * VERTEX_STRIDE;
                    if (vertices.capacity() >= requiredBytes) return;
                    var newCapacity = Math.max(requiredVertices,
                            (vertices.capacity() / VERTEX_STRIDE) * 2);
                    vertices = BufferUtils.createByteBuffer(Math.toIntExact((long) newCapacity * VERTEX_STRIDE));
                }

                private static void bakeWingPair(AvianWingsModel model, PoseStack poseStack,
                                                  ByteBuffer target, float age, float scale,
                                                  float zRotationDegrees) {
                    poseStack.pushPose();
                    if (zRotationDegrees < 0.0f) {
                        poseStack.translate(-0.30f, 0.25f, 0.0f);
                    } else if (zRotationDegrees > 0.0f) {
                        poseStack.translate(0.30f, 0.25f, 0.0f);
                    }
                    poseStack.rotate(new Quaternionf().rotateZ(zRotationDegrees * Mth.DEG_TO_RAD));
                    poseStack.scale(scale, scale, scale);
                    model.setupAnim(age);
                    bakeMesh(model.root, poseStack, target);
                    poseStack.popPose();
                }

                private static void bakeMesh(ModelPart root, PoseStack poseStack, ByteBuffer target) {
                    root.visit(poseStack, (pose, path, cubeIndex, cube) -> {
                        var matrix = pose.pose();
                        for (var polygon : cube.polygons) {
                            var vertices = polygon.vertices();
                            if (vertices.length < 3) continue;
                            var v0 = transform(vertices[0], matrix);
                            var v1 = transform(vertices[1], matrix);
                            var v2 = transform(vertices[2], matrix);
                            putVertex(target, v0);
                            putVertex(target, v1);
                            putVertex(target, v2);
                            if (vertices.length < 4) continue;
                            var v3 = transform(vertices[3], matrix);
                            putVertex(target, v0);
                            putVertex(target, v2);
                            putVertex(target, v3);
                        }
                    });
                }

                private static void putVertex(ByteBuffer target, float[] vertex) {
                    target.putFloat(vertex[0]);
                    target.putFloat(vertex[1]);
                    target.putFloat(vertex[2]);
                    target.putFloat(vertex[3]);
                    target.putFloat(vertex[4]);
                }

                private static float[] transform(ModelPart.Vertex vertex, Matrix4f matrix) {
                    var position = new Vector3f(
                            vertex.worldX(),
                            vertex.worldY(),
                            vertex.worldZ()
                    ).mulPosition(matrix);
                    return new float[]{
                            position.x,
                            position.y,
                            position.z,
                            vertex.u(),
                            vertex.v()
                    };
                }

                private static final class AvianWingsModel extends Model<AvatarRenderState> {
                    private final ModelPart root;
                    private final ModelPart coracoidLeft;
                    private final ModelPart humerusLeft;
                    private final ModelPart ulnaLeft;
                    private final ModelPart carpalsLeft;
                    private final ModelPart coracoidRight;
                    private final ModelPart humerusRight;
                    private final ModelPart ulnaRight;
                    private final ModelPart carpalsRight;

                    private AvianWingsModel(ModelPart root) {
                        super(root, RenderTypes::entityTranslucent);
                        this.root = root;
                        coracoidLeft = root.getChild("coracoidLeft");
                        humerusLeft = coracoidLeft.getChild("humerusLeft");
                        ulnaLeft = humerusLeft.getChild("ulnaLeft");
                        carpalsLeft = ulnaLeft.getChild("carpalsLeft");
                        coracoidRight = root.getChild("coracoidRight");
                        humerusRight = coracoidRight.getChild("humerusRight");
                        ulnaRight = humerusRight.getChild("ulnaRight");
                        carpalsRight = ulnaRight.getChild("carpalsRight");
                    }

                    private void setupAnim(float ageInTicks) {
                        var s = Mth.sin(ageInTicks * 0.05f);
                        setupSegment(0.0f, -23.5f + (s * 5.0f - 14.0f) * 0.5f, -16.0f,
                                coracoidLeft, coracoidRight);
                        setupSegment(0.0f, 13.0f, 29.0f, humerusLeft, humerusRight);
                        setupSegment(0.0f, 12.0f + (s * 5.0f - 14.0f) * 0.5f, -28.0f,
                                ulnaLeft, ulnaRight);
                        setupSegment(0.0f, 4.0f + (s * 5.0f - 14.0f), 18.3f,
                                carpalsLeft, carpalsRight);
                    }

                    private static void setupSegment(float xDeg, float yDeg, float zDeg,
                                                     ModelPart left, ModelPart right) {
                        var x = xDeg * Mth.DEG_TO_RAD;
                        var y = yDeg * Mth.DEG_TO_RAD;
                        var z = zDeg * Mth.DEG_TO_RAD;
                        left.setRotation(x, y, z);
                        right.setRotation(x, -y, -z);
                    }

                    private static LayerDefinition createBodyLayer() {
                        var mesh = new MeshDefinition();
                        var root = mesh.getRoot();
                        var deformation = new CubeDeformation(0.0f);
                        var coracoidLeft = root.addOrReplaceChild("coracoidLeft",
                                CubeListBuilder.create().texOffs(0, 28)
                                        .addBox(0, -1.5f, -1.5f, 5, 3, 3, deformation),
                                PartPose.offset(1.5f, 5.5f, 2.5f));
                        var coracoidRight = root.addOrReplaceChild("coracoidRight",
                                CubeListBuilder.create().texOffs(0, 34)
                                        .addBox(-5, -1.5f, -1.5f, 5, 3, 3, deformation),
                                PartPose.offset(-1.5f, 5.5f, 2.5f));
                        var humerusLeft = coracoidLeft.addOrReplaceChild("humerusLeft",
                                CubeListBuilder.create().texOffs(0, 0)
                                        .addBox(-0.1f, -1.1f, -2, 7, 3, 4, deformation),
                                PartPose.offset(4.7f, -0.6f, 0.1f));
                        var humerusRight = coracoidRight.addOrReplaceChild("humerusRight",
                                CubeListBuilder.create().texOffs(0, 7)
                                        .addBox(-6.9f, -1.1f, -2, 7, 3, 4, deformation),
                                PartPose.offset(-4.7f, -0.6f, 0.1f));
                        var ulnaLeft = humerusLeft.addOrReplaceChild("ulnaLeft",
                                CubeListBuilder.create().texOffs(22, 0)
                                        .addBox(0, -1.5f, -1.5f, 9, 3, 3, deformation),
                                PartPose.offset(6.5f, 0.2f, 0.1f));
                        var ulnaRight = humerusRight.addOrReplaceChild("ulnaRight",
                                CubeListBuilder.create().texOffs(22, 6)
                                        .addBox(-9, -1.5f, -1.5f, 9, 3, 3, deformation),
                                PartPose.offset(-6.5f, 0.2f, 0.1f));
                        var carpalsLeft = ulnaLeft.addOrReplaceChild("carpalsLeft",
                                CubeListBuilder.create().texOffs(22, 0)
                                        .addBox(0, -1, -1, 5, 2, 2, deformation),
                                PartPose.offset(8.5f, 0, 0));
                        var carpalsRight = ulnaRight.addOrReplaceChild("carpalsRight",
                                CubeListBuilder.create().texOffs(22, 0)
                                        .addBox(-5, -1, -1, 5, 2, 2, deformation),
                                PartPose.offset(-8.5f, 0, 0));

                        addFeathers(coracoidLeft, "feathersCoracoidLeft", 6, 40,
                                0, 0, -1, 6, 8, 0.2f, 0.4f, 0, 1, deformation);
                        addFeathers(coracoidRight, "feathersCoracoidRight", 0, 40,
                                -6, 0, -1, 6, 8, 0.2f, -0.4f, 0, 1, deformation);
                        addFeathers(humerusLeft, "feathersTertiaryLeft", 10, 14,
                                0, 0, -0.5f, 10, 14, 0.2f, 0, 1.5f, 1, deformation);
                        addFeathers(humerusRight, "feathersTertiaryRight", 0, 14,
                                -10, 0, -0.5f, 10, 14, 0.2f, 0, 1.5f, 1, deformation);
                        addFeathers(ulnaLeft, "feathersSecondaryLeft", 31, 14,
                                -2, 0, -0.5f, 11, 12, 0.2f, 0, 1, 0, deformation);
                        addFeathers(ulnaRight, "feathersSecondaryRight", 20, 14,
                                -9, 0, -0.5f, 11, 12, 0.2f, 0, 1, 0, deformation);
                        addFeathers(carpalsLeft, "feathersPrimaryLeft", 53, 14,
                                0, -2.1f, -0.5f, 11, 11, 0.2f, 0, 0, 0, deformation);
                        addFeathers(carpalsRight, "feathersPrimaryRight", 42, 14,
                                -11, -2.1f, -0.5f, 11, 11, 0.2f, 0, 0, 0, deformation);
                        return LayerDefinition.create(mesh, 64, 64);
                    }

                    private static void addFeathers(PartDefinition parent, String name, int u, int v,
                                                    float x, float y, float z, float width, float height,
                                                    float depth, float offsetX, float offsetY, float offsetZ,
                                                    CubeDeformation deformation) {
                        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                                        .addBox(x, y, z, width, height, depth, deformation),
                                PartPose.offset(offsetX, offsetY, offsetZ));
                    }
                }
            }
        }

        public static final class DarkmatterSixWingsVfx implements TickingVfx {
            @Override
            public void tick(FrameContext ctx) {
                var roots = Accelerator.Client.WingAvatarRegistry.entries();
                if (roots.isEmpty()) return;
                DarkmatterSixWingsGraphVfx.sync(ctx, roots);
            }

            @Override
            public boolean isAlive() {
                return true;
            }
        }

        private static void toggle() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.beginToggleRequest(Skills.DARKMATTER_SIX_WINGS.get())) return;
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
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            if (!skill.isEnabled(player)) {
                var system = AbilitySystemServer.getSystem(player);
                var required = skill.getMaintenanceCost(player)
                        * system.getPlayerCalculationIntensity(player.getUUID());
                var activationCost = activationMatterCost(
                        skill.getEffectiveProficiencyMilestone(player));
                if (system.getPlayerAvailableCP(player.getUUID()) + 1.0e-5f < required
                        || !system.getDarkmatterResourceManager().consume(
                        player,
                        activationCost,
                        skill,
                        skill.getIterationTicks(player)
                )) return;
            }
            skill.toggle(player);
            sync(player);
            AbilitySystemServer.getSystem(player).getDarkmatterResourceManager().requestSync(player);
        }

        public static boolean isActive(ServerPlayer player) {
            return Skills.DARKMATTER_SIX_WINGS.get().isEnabled(player)
                    && player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get());
        }

        public static float adjustCategoryCost(ServerPlayer player, Skill skill,
                                               float baseCost, float adjustedCost) {
            if (player == null || skill == null || !Float.isFinite(baseCost)
                    || !Float.isFinite(adjustedCost) || baseCost < 0.0f || adjustedCost < 0.0f
                    || skill == Skills.DARKMATTER_SIX_WINGS.get()
                    || skill.getCategory() != AbilityCategories.DARKMATTER.get()
                    || !isActive(player)
                    || !Skills.DARKMATTER_SIX_WINGS.get().hasProficiencyMilestone(player, 3)) {
                return adjustedCost;
            }
            return adjustedCategoryCost(baseCost, adjustedCost,
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player));
        }

        public static boolean debugSetActive(ServerPlayer player, boolean active) {
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            if (skill.getRuntimeData(player).isEmpty()) return false;
            if (skill.isEnabled(player) != active) skill.toggle(player);
            var system = AbilitySystemServer.getSystem(player);
            if (!active) system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            sync(player);
            system.getDarkmatterResourceManager().requestSync(player);
            return isActive(player) == active;
        }

        public static float adjustedCategoryCost(float baseCost, float adjustedCost, int milestone) {
            if (!Float.isFinite(baseCost) || !Float.isFinite(adjustedCost)
                    || baseCost < 0.0f || adjustedCost < 0.0f) return Float.NaN;
            return adjustedCost;
        }

        public static float flightSpeed(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 2 ? 0.0575f : 0.05f;
        }

        public static float flightSpeed(float betaPower, int milestone) {
            var speed = 0.05f * (1.0f + 0.08f * Math.max(0.0f, betaPower));
            return Math.clamp(milestone, 0, 3) >= 2 ? speed * 1.15f : speed;
        }

        public static double areaMultiplier(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 2 ? 1.15 : 1.0;
        }

        public static float gammaMagnitudeMultiplier(int milestone) {
            return Math.clamp(milestone, 0, 3) >= 3 ? 1.20f : 1.0f;
        }

        public static float gammaMagnitudeMultiplier(ServerPlayer player) {
            return isActive(player) ? gammaMagnitudeMultiplier(
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player)) : 1.0f;
        }

        public static float darkmatterPenetration(float betaPower) {
            return Math.min(0.25f, Math.max(0.0f,
                    Float.isFinite(betaPower) ? betaPower : 0.0f) * 0.05f);
        }

        private static void sync(ServerPlayer player) {
            var active = Skills.DARKMATTER_SIX_WINGS.get().isEnabled(player)
                    && player.isAlive() && !player.hasDisconnected();
            var type = AttachmentTypes.DARKMATTER_SIX_WINGS.get();
            if (player.getData(type) != active) {
                player.setData(type, active);
                player.syncData(type);
            }
            SkillFlightController.setSource(player, FLIGHT_SOURCE, active);
            var flightSpeed = active
                    ? flightSpeed(DarkmatterPhase.beta(player),
                    Skills.DARKMATTER_SIX_WINGS.get().getEffectiveProficiencyMilestone(player))
                    : 0.05f;
            if (Math.abs(player.getAbilities().getFlyingSpeed() - flightSpeed) > 1.0e-5f) {
                player.getAbilities().setFlyingSpeed(flightSpeed);
                player.onUpdateAbilities();
            }
            PlayerAttributeRuntime.syncTrueResistanceModifier(
                    player, TRUE_RESISTANCE_MODIFIER_ID, 0.0, false);
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.DARKMATTER_SIX_WINGS.get();
            if (skill.isEnabled(player) && (!player.isAlive() || player.hasDisconnected())) {
                skill.toggle(player);
                AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                        player.getUUID(), skill.getKeyString());
            }
            var active = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (active) {
                active = AbilitySystemServer.getSystem(player).ensurePermanentOccupation(
                        player.getUUID(), skill.getMaintenanceCost(player), skill);
                if (!active && skill.isEnabled(player)) skill.toggle(player);
            }
            sync(player);
        }
    }

    @EventBusSubscriber
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || event.isCanceled()
                    || !(event.getAmount() > 0.0f)
                    || !Server.isActive(player)) return;
            if (event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) return;
            var reduction = Math.min(0.20f, DarkmatterPhase.alpha(player) * 0.04f);
            event.setAmount(event.getAmount() * (1.0f - reduction));
        }

        @SubscribeEvent
        public static void onAttack(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(event.getTarget() instanceof LivingEntity target)
                    || !DarkmatterTargeting.isAttackableBy(player, target)
                    || !Server.isActive(player)) return;
            var alpha = DarkmatterPhase.alpha(player);
            if (!(alpha > 0.0f)) return;
            var now = level.getGameTime();
            var pair = new WingStrikePair(player.getUUID(), target.getUUID());
            if (now < NEXT_WING_STRIKE_TICK.getOrDefault(pair, 0L)) return;
            NEXT_WING_STRIKE_TICK.put(pair, now + 10L);
            DarkmatterTargeting.hurt(level, target,
                    SkillDamageSource.of(player, Skills.DARKMATTER_SIX_WINGS.get()),
                    1.0f + alpha);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onDarkmatterDamageApplied(LivingDamageEvent.Pre event) {
            if (!(event.getSource() instanceof SkillDamageSource skillSource)
                    || skillSource.getSkill().getCategory() != AbilityCategories.DARKMATTER.get()
                    || !(event.getSource().getEntity() instanceof ServerPlayer player)
                    || !Server.isActive(player) || !(event.getNewDamage() > 0.0f)) return;
            var penetration = Server.darkmatterPenetration(DarkmatterPhase.beta(player));
            if (penetration > 0.0f) {
                event.setNewDamage(event.getNewDamage() / (1.0f - penetration));
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
            return PacketTypes.DARKMATTER_SIX_WINGS_TOGGLE.get();
        }
    }
}
