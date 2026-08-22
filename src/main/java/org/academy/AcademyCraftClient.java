package org.academy;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.math.Axis;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.editor.UiLayoutEditor;
import org.academy.api.client.gui.editor.UiLayoutEditorScreen;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.screen.ScreenDispatcher;
import org.academy.api.client.hud.HudManager;
import org.academy.api.client.hud.terminal.TerminalHud;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.post.GlowEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.internal.common.ability.teleport.InstantTeleportSyncPacket;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.util.FileUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState;
import org.academy.internal.client.ability.program.AbilityProgramEditorClient;
import org.academy.internal.client.app.music.backend.MusicPlayerBackend;
import org.academy.internal.client.app.music.ui.MusicApp;
import org.academy.internal.client.app.props.PropsApp;
import org.academy.internal.client.app.props.PropsClientState;
import org.academy.internal.client.app.props.PropsIcon;
import org.academy.internal.client.app.settings.ui.SettingsApp;
import org.academy.internal.client.app.settings.ui.SkillSettingsApp;
import org.academy.internal.client.commands.ClientProfileCommand;
import org.academy.internal.client.gui.debug.UiDebugBrowserScreen;
import org.academy.internal.client.gui.debug.UiDebugLayoutDefinition;
import org.academy.internal.client.gui.debug.UiDebugLayoutRegistry;
import org.academy.internal.client.gui.debug.UiDebugSession;
import org.academy.internal.client.gui.screen.AbilityDeveloperLayoutEditor;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.hud.HudDebugScreen;
import org.academy.internal.client.hud.HudLayoutConfig;
import org.academy.internal.client.particle.BloodSplashParticle;
import org.academy.internal.client.particle.BloodSprayParticle;
import org.academy.internal.client.particle.ImagPhaseFluidParticle;
import org.academy.internal.client.particle.ImagPhaseLeavesParticle;
import org.academy.internal.client.particle.VectorBlastParticle;
import org.academy.internal.client.profiler.ProfilerClientHooks;
import org.academy.internal.client.render.vfx.*;
import org.academy.internal.client.render.fluid.ImagPhaseFluidRenderer;
import org.academy.internal.client.renderer.blockentity.WindGenPillarRenderer;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumInterferenceLayer;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.client.world.item.ImagPhaseDowsingRodClient;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.ProficiencySkillSettings;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.academy.internal.common.world.level.material.Fluids;

import java.io.File;
import java.util.function.BiConsumer;

import static org.academy.AcademyCraft.academy;
import static org.academy.AcademyCraft.vanilla;

@EventBusSubscriber(Dist.CLIENT)
@Mod(value = AcademyCraft.MOD_ID, dist = Dist.CLIENT)
public final class AcademyCraftClient {
    private static boolean renderInitialized = false;

    public static boolean isUiDebugEnvironment() {
        return Dev.HAS_IM_GUI && Boolean.parseBoolean(System.getenv("IS_DEV"));
    }

    public static void initMain() {
        HudLayoutConfig.init();
        ProfilerClientHooks.INSTANCE.initMain();
        TerminalHud.Companion.addApp(SettingsApp.INSTANCE);
        TerminalHud.Companion.addApp(SkillSettingsApp.INSTANCE);
        TerminalHud.Companion.addApp(MusicApp.INSTANCE);
        PropsIcon.INSTANCE.init();
        TerminalHud.Companion.addApp(PropsApp.INSTANCE);
        PropsClientState.init();

        MusicPlayerBackend.Companion.init();
        Screens.register();
        HudManager.INSTANCE.initMain();
        AbilitySystemClient.init();
        AbilityProgramEditorClient.init();
        InstantTeleportSyncPacket.initClient();
        ProficiencyPolicy.initClient();
        ProficiencySkillSettings.initClient();
        ClientSyncManager.init();
        ImagPhaseDowsingRodClient.init();
        BeamVfxClient.register();
        SmokeVfxClient.register();
        ArcVfxClient.register();
        WingVfxClient.register();
        PlasmaVfxClient.register();
        SkyStrikeVfxClient.register();
    }

    public static void initRender() {
        Render.init();
        VfxManager.INSTANCE.init();
        GlowEffect.init();
        ScreenDispatcher.Companion.init();
        HudManager.INSTANCE.initRender();

        MsdfFontService.genDefaultGlyph();
        renderInitialized = true;
    }

    public static boolean isRenderInitialized() {
        return renderInitialized;
    }

    @SubscribeEvent
    public static void onClientStarted(ClientStartedEvent event) {
        ImGuiUtilApi.INSTANCE.init();
        initMain();
        initRender();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        InputSystem.tickMaintainedKeyBindings();
        AbilityControlTabletSpecialRenderer.tickHeldItems();
        ImagPhaseDowsingRodClient.tick();
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("academy")
                        .then(Commands.literal("debug")
                                .then(Commands.literal("skillgui")
                                        .executes(_ -> setSkillGuiDebug(AbilityDeveloperLayoutEditor.toggleDebugMode()))
                                        .then(Commands.literal("on")
                                                .executes(_ -> setSkillGuiDebug(true)))
                                        .then(Commands.literal("off")
                                                .executes(_ -> setSkillGuiDebug(false)))
                                        .then(Commands.literal("toggle")
                                                .executes(_ -> setSkillGuiDebug(AbilityDeveloperLayoutEditor.toggleDebugMode())))
                                        .then(Commands.literal("reset")
                                                .executes(_ -> {
                                                    AbilityDeveloperLayoutEditor.resetSession();
                                                    notifyClient("Skill GUI layout reset to built-in defaults.");
                                                    return 1;
                                                }))
                                        .then(Commands.literal("export")
                                                .executes(_ -> {
                                                    try {
                                                        var path = AbilityDeveloperLayoutEditor.exportAll();
                                                        notifyClient("Skill GUI layout exported to " + path.toAbsolutePath());
                                                        return 1;
                                                    } catch (Exception exception) {
                                                        AcademyCraft.getLogger().error("Unable to export skill GUI layout", exception);
                                                        notifyClient("Unable to export Skill GUI layout: " + exception.getMessage());
                                                        return 0;
                                                    }
                                                }))
                                ))
        );
        ClientProfileCommand.register(event.getDispatcher());
        if (!isUiDebugEnvironment()) return;
        event.getDispatcher().register(
                Commands.literal("academy")
                        .then(
                                Commands.literal("debug")
                                        .then(
                                                Commands.literal("ui")
                                                        .executes(_ -> {
                                                            UiDebugBrowserScreen.Companion.open();
                                                            return 1;
                                                        })
                                                        .then(
                                                                Commands.argument(
                                                                                "layout",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                                                                UiDebugLayoutRegistry.INSTANCE.gui().stream()
                                                                                        .map(UiDebugLayoutDefinition::getId)
                                                                                        .toList(),
                                                                                builder
                                                                        ))
                                                                        .executes(ctx -> {
                                                                            var layout = StringArgumentType
                                                                                    .getString(ctx, "layout");
                                                                            if (UiDebugLayoutRegistry.INSTANCE.gui().stream()
                                                                                    .noneMatch(definition -> definition.getId().equals(layout))) {
                                                                                return 0;
                                                                            }
                                                                            UiLayoutEditorScreen.Companion
                                                                                    .openDebug(layout);
                                                                            return 1;
                                                                        })
                                                        )
                                        )
                                        .then(
                                                Commands.literal("hud")
                                                        .executes(_ -> {
                                                            HudDebugScreen.Companion.open();
                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                Commands.literal("save")
                                                        .executes(_ -> {
                                                            UiDebugBrowserScreen.Companion.notifyPublish(UiDebugSession.INSTANCE.publish());
                                                            return 1;
                                                        })
                                        )
                        )
                        .then(
                                Commands.literal("uieditor")
                                        .executes(_ -> {
                                            UiDebugBrowserScreen.Companion.open();
                                            return 1;
                                        })
                                        .then(
                                                Commands.argument("file", StringArgumentType.word())
                                                        .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                                                UiDebugLayoutRegistry.INSTANCE.all().stream()
                                                                        .map(UiDebugLayoutDefinition::getId)
                                                                        .toList(),
                                                                builder
                                                        ))
                                                        .executes(ctx -> {
                                                            String file = StringArgumentType.getString(ctx, "file");
                                                            UiLayoutEditor.INSTANCE.open(file);
                                                            return 1;
                                                        })
                                        )
                        )
        );
    }

    private static int setSkillGuiDebug(boolean enabled) {
        AbilityDeveloperLayoutEditor.setDebugMode(enabled);
        notifyClient("Skill GUI layout editor " + (enabled ? "enabled" : "disabled") + '.');
        return 1;
    }

    private static void notifyClient(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(message));
    }

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        resize(event.getWidth(), event.getHeight());
    }

    public static void resize(int width, int height) {
        Render.resize();
        PostEffect.resize(width, height);
        HudManager.INSTANCE.resize(width, height);
    }

    @SubscribeEvent
    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        Config.INSTANCE.save();
    }

    @SubscribeEvent
    public static void onClientStopped(ClientStoppedEvent event) {
        MentaloutRosterClientState.clearLocal();
        if (isUiDebugEnvironment()) UiDebugSession.INSTANCE.close();
        ImGuiUtilApi.INSTANCE.close();
        MsdfFontService.INSTANCE.close();
        MsdfAtlasManager.closeAll();
        PostEffect.close();
        GlowEffect.getInstance().close();
        VfxManager.INSTANCE.close();
        Render.close();
    }

    @SubscribeEvent
    public static void onExtractBlockOutlineRenderState(ExtractBlockOutlineRenderStateEvent event) {
        var state = event.getBlockState();
        var pillar = state.getBlock() == Blocks.WIND_GEN_PILLAR.get();
        var base = state.getBlock() == Blocks.WIND_GEN_BASE.get();
        var top = state.getBlock() == Blocks.WIND_GEN_TOP.get();
        if (pillar || base || top) {
            event.addCustomRenderer((
                    blockOutlineRenderState,
                    bufferSource,
                    poseStack,
                    levelRenderState
            ) -> {
                poseStack.pushPose();
                var pos = blockOutlineRenderState.pos();
                var cam = levelRenderState.cameraRenderState.pos;
                var camX = cam.x;
                var camY = cam.y;
                var camZ = cam.z;
                poseStack.translate(pos.getX() - camX + 0.5f, pos.getY() - camY, pos.getZ() - camZ + 0.5f);
                if (top) poseStack.scale(1, 1f / 16f, 1);
                if (base && state.getValue(MultiBlock.TYPE) == MultiBlock.MultiBlockType.MAIN) {
                    poseStack.scale(1, 15 / 16f, 1);
                    poseStack.translate(0, 1 / 16f, 0);
                }
                poseStack.mulPose(Axis.YN.rotationDegrees(22.5f));
                bufferSource.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
                    var ps = new PoseStack();
                    ps.last().set(pose);
                    CylinderRenderer.renderCylinderWireframe(
                            ps,
                            buffer,
                            WindGenPillarRenderer.PILLAR_OUTLINE_VERTEX_BUFFER,
                            0, 0, 0, 0.4f
                    );
                });

                poseStack.popPose();
                return pillar || (
                        base && (state.getValue(MultiBlock.TYPE) == MultiBlock.MultiBlockType.SUBJECT)
                );
            });
        }
    }

    @SubscribeEvent
    public static void onEntityRenderersAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skinType : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skinType);
            if (renderer != null) {
                renderer.addLayer(new SkillEffectsLayer(renderer));
                addQuantumLayerIfPossible(renderer);
            }
        }

        for (var type : event.getEntityTypes()) {
            var renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?>) {
                addQuantumLayerIfPossible(UncheckedUtil.uncheckedCast(renderer));
            }
        }
    }

    private static <T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
    void addQuantumLayerIfPossible(
            LivingEntityRenderer<T, S, M> renderer
    ) {
        renderer.addLayer(new QuantumInterferenceLayer<>(renderer));
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState renderState) {
                renderState.setRenderData(ElectromasterWeaponVfx.ENTITY_ID_CONTEXT, avatar.getId());
                renderState.setRenderData(
                        ElectromasterWeaponVfx.MAGNETIC_CONTEXT,
                        avatar.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA)
                );
                renderState.setRenderData(
                        ElectromasterWeaponVfx.IRON_SAND_CONTEXT,
                        avatar.getData(AttachmentTypes.IRON_SAND_DATA)
                );
            }
        });
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
                },
                living()
        );
    }

    private static BiConsumer<LivingEntity, LivingEntityRenderState> living() {
        return (livingEntity, livingEntityRenderState) ->
                livingEntityRenderState.setRenderData(
                        QuantumInterferenceLayer.CONTEXT_KEY,
                        livingEntity.getExistingDataOrNull(AttachmentTypes.QUANTUM_DATA.get())
                );
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypes.IMAG_PHASE_LEAVES.get(), ImagPhaseLeavesParticle.Provider::new);
        event.registerSpriteSet(ParticleTypes.IMAG_PHASE_FLUID.get(), sprites ->
                (type, level, x, y, z, xSpeed, ySpeed, zSpeed, random) -> {
                    var particle = new ImagPhaseFluidParticle(
                            level, sprites, x, y, z, xSpeed, ySpeed, zSpeed, random
                    );
                    particle.scale(0.5F + random.nextFloat() * 0.25F);
                    int[][] colors = {
                            {245, 144, 144}, {178, 232, 243}, {209, 170, 225},
                            {243, 182, 224}, {196, 238, 156}
                    };
                    int[] color = colors[random.nextInt(colors.length)];
                    particle.setColor(
                            Math.max(0, Math.min(255, color[0] + random.nextInt(-20, 20))) / 255.0F,
                            Math.max(0, Math.min(255, color[1] + random.nextInt(-20, 20))) / 255.0F,
                            Math.max(0, Math.min(255, color[2] + random.nextInt(-20, 20))) / 255.0F
                    );
                    return particle;
                });
        event.registerSpriteSet(ParticleTypes.VECTOR_BLAST.get(), VectorBlastParticle.Provider::new);
        event.registerSpriteSet(ParticleTypes.BLOOD_SPLASH.get(), BloodSplashParticle.Provider::new);
        event.registerSpriteSet(ParticleTypes.BLOOD_SPRAY_GROUND.get(), BloodSprayParticle.Provider::new);
        event.registerSpriteSet(ParticleTypes.BLOOD_SPRAY_WALL.get(), BloodSprayParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterSpecialModelRenderer(RegisterSpecialModelRendererEvent event) {
        event.register(
                academy("wireless_node"),
                WirelessNodeSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("wind_gen_base"),
                WindGenBaseSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("wind_gen_pillar"),
                WindGenPillarSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("wind_gen_top"),
                WindGenTopSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("ability_developer"),
                AbilityDeveloperSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("ability_control_tablet"),
                AbilityControlTabletSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("omni_crafting_table"),
                OmniCraftingTableSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("solar_gen"),
                SolarGenSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("imag_phase_dowsing_rod"),
                ImagPhaseDowsingRodSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("darkmatter_trident"),
                DarkmatterTridentSpecialRenderer.Unbaked.MAP_CODEC
        );
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(
                    LivingEntity entity,
                    InteractionHand hand,
                    ItemStack stack
            ) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        }, Items.IMAG_PHASE_DOWSING_ROD.get());
        // The shaped armor remains a fully functional equipment set, but its worn layer is
        // intentionally invisible. Keep the equipment asset and animated textures available
        // for item rendering/resource packs while suppressing only the humanoid layer submit.
        event.registerItem(new IClientItemExtensions() {
            @Override
            public int getArmorLayerTintColor(
                    ItemStack stack, EquipmentClientInfo.Layer layer,
                    int layerIdx, int fallbackColor
            ) {
                return 0;
            }
        }, Items.DARK_MATTER_HELMET.get(), Items.DARK_MATTER_CHESTPLATE.get(),
                Items.DARK_MATTER_LEGGINGS.get(), Items.DARK_MATTER_BOOTS.get());
    }

    @SubscribeEvent
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        var still = new Material(vanilla("block/water_still"));
        var flowing = new Material(vanilla("block/water_flow"));
        var overlay = new Material(vanilla("block/water_overlay"));
        var model = new FluidModel.Unbaked(
                still,
                flowing,
                overlay,
                FluidTintSources.constant(0x9908050D),
                ImagPhaseFluidRenderer.INSTANCE
        );
        event.register(
                model,
                Fluids.IMAG_PHASE,
                Fluids.FLOWING_IMAG_PHASE
        );
    }

    public static final class Config {
        public static final File CLIENT_CONFIG_FILE;
        public static final AcademyCraftConfig INSTANCE;

        static {
            CLIENT_CONFIG_FILE = new File(
                    Minecraft.getInstance().gameDirectory,
                    "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json"
            );
            FileUtil.checkFile(CLIENT_CONFIG_FILE);
            INSTANCE = new AcademyCraftConfig(CLIENT_CONFIG_FILE);
        }

        private Config() {
        }
    }
}
