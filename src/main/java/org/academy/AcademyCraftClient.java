package org.academy;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.gui.editor.UiLayoutEditor;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.screen.ScreenDispatcher;
import org.academy.api.client.hud.HudManager;
import org.academy.api.client.hud.terminal.TerminalHud;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.util.FileUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.internal.client.app.music.backend.MusicPlayerBackend;
import org.academy.internal.client.app.music.ui.MusicApp;
import org.academy.internal.client.app.settings.ui.SkillSettingsApp;
import org.academy.internal.client.app.settings.ui.SettingsApp;
import org.academy.internal.client.app.props.PropsApp;
import org.academy.internal.client.app.props.PropsClientState;
import org.academy.internal.client.app.props.PropsIcon;
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.hud.HudLayoutConfig;
import org.academy.internal.client.hud.HudDebugScreen;
import org.academy.internal.client.gui.debug.UiDebugBrowserScreen;
import org.academy.internal.client.gui.debug.UiDebugLayoutRegistry;
import org.academy.internal.client.gui.debug.UiDebugSession;
import org.academy.internal.client.profiler.ProfilerClientHooks;
import org.academy.internal.client.renderer.blockentity.WindGenPillarRenderer;
import org.academy.internal.client.particle.VectorBlastParticle;
import org.academy.internal.client.particle.BloodSplashParticle;
import org.academy.internal.client.particle.BloodSprayParticle;
import org.academy.internal.client.renderer.effect.*;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumInterferenceLayer;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.client.renderer.vfx.ArcVfxClient;
import org.academy.internal.client.renderer.vfx.BeamVfxClient;
import org.academy.internal.client.renderer.vfx.SmokeVfxClient;
import org.academy.internal.client.renderer.vfx.StormWingVfxClient;
import org.academy.internal.client.renderer.vfx.PlasmaVfxClient;
import org.academy.internal.client.renderer.vfx.SkyStrikeVfxClient;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.core.particles.ParticleTypes;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;

import java.io.File;
import java.util.function.BiConsumer;

import static org.academy.AcademyCraft.academy;

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
        ClientSyncManager.init();
        BeamVfxClient.register();
        SmokeVfxClient.register();
        ArcVfxClient.register();
        StormWingVfxClient.register();
        PlasmaVfxClient.register();
        SkyStrikeVfxClient.register();
    }

    public static void initRender() {
        Render.init();
        VfxManager.INSTANCE.init();
        BloomEffect.init();
        ScreenDispatcher.Companion.init();
        HudManager.INSTANCE.initRender();

        MsdfFontService.genDefaultGlyph();

        if (IrisCompat.hasIris()) {
            IrisPipelines.assignPipeline(Render.RenderPipelines.LEVEL_POS_COLOR_QUADS, ShaderKey.BASIC_COLOR);
            IrisPipelines.assignPipeline(Render.RenderPipelines.LEVEL_POS_COLOR_QUADS_ADDITIVE, ShaderKey.BASIC_COLOR);
            IrisPipelines.assignPipeline(Render.RenderPipelines.LEVEL_POS_COLOR_TRANGLES, ShaderKey.BASIC_COLOR);
            IrisPipelines.assignPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR, ShaderKey.TEXTURED_COLOR);
        }
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
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (!isUiDebugEnvironment()) return;
        event.getDispatcher().register(
                Commands.literal("academy")
                        .then(
                                Commands.literal("debug")
                                        .then(
                                                Commands.literal("ui")
                                                        .executes(ctx -> {
                                                            UiDebugBrowserScreen.open();
                                                            return 1;
                                                        })
                                                        .then(
                                                                Commands.argument(
                                                                                "layout",
                                                                                com.mojang.brigadier.arguments.StringArgumentType.word()
                                                                        )
                                                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                                                UiDebugLayoutRegistry.gui().stream()
                                                                                        .map(definition -> definition.getId())
                                                                                        .toList(),
                                                                                builder
                                                                        ))
                                                                        .executes(ctx -> {
                                                                            var layout = com.mojang.brigadier.arguments.StringArgumentType
                                                                                    .getString(ctx, "layout");
                                                                            if (UiDebugLayoutRegistry.gui().stream()
                                                                                    .noneMatch(definition -> definition.getId().equals(layout))) {
                                                                                return 0;
                                                                            }
                                                                            org.academy.api.client.gui.editor.UiLayoutEditorScreen
                                                                                    .openDebug(layout);
                                                                            return 1;
                                                                        })
                                                        )
                                        )
                                        .then(
                                                Commands.literal("hud")
                                                        .executes(ctx -> {
                                                            HudDebugScreen.open();
                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                Commands.literal("save")
                                                        .executes(ctx -> {
                                                            UiDebugBrowserScreen.notifyPublish(UiDebugSession.publish());
                                                            return 1;
                                                        })
                                        )
                        )
                        .then(
                                Commands.literal("uieditor")
                                        .executes(ctx -> {
                                            UiDebugBrowserScreen.open();
                                            return 1;
                                        })
                                        .then(
                                                Commands.argument("file", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                                UiDebugLayoutRegistry.all().stream()
                                                                        .map(definition -> definition.getId())
                                                                        .toList(),
                                                                builder
                                                        ))
                                                        .executes(ctx -> {
                                                            String file = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "file");
                                                            UiLayoutEditor.INSTANCE.open(file);
                                                            return 1;
                                                        })
                                        )
                        )
        );
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
        if (isUiDebugEnvironment()) UiDebugSession.close();
        ImGuiUtilApi.INSTANCE.close();
        MsdfFontService.INSTANCE.close();
        MsdfAtlasManager.closeAll();
        PostEffect.close();
        BloomEffect.getInstance().close();
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
                renderState.setRenderData(WingEffectRenderer.ENTITY_ID_CONTEXT, avatar.getId());
                renderState.setRenderData(
                        StormWingEffectRenderer.CONTEXT_KEY,
                        avatar.getData(AttachmentTypes.ACTIVATED_STORM_WING)
                );
                renderState.setRenderData(
                        WingEffectRenderer.BLACK_CONTEXT,
                        avatar.getData(AttachmentTypes.ACTIVATED_BLACK_WING)
                );
                renderState.setRenderData(
                        WingEffectRenderer.WHITE_CONTEXT,
                        avatar.getData(AttachmentTypes.ACTIVATED_WHITE_WING)
                );
                renderState.setRenderData(
                        WingEffectRenderer.PLATINUM_CONTEXT,
                        avatar.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING)
                );
                renderState.setRenderData(
                        DarkmatterSixWingsEffectRenderer.CONTEXT_KEY,
                        avatar.getData(AttachmentTypes.DARKMATTER_SIX_WINGS)
                );
                renderState.setRenderData(
                        RailgunEffectRenderer.CONTEXT_KEY,
                        avatar.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA)
                );
                renderState.setRenderData(
                        LightShieldEffectRenderer.CONTEXT_KEY,
                        avatar.getData(AttachmentTypes.LIGHT_SHIELD_ACTIVE)
                );
                renderState.setRenderData(
                        ElectromasterWeaponEffectRenderer.MAGNETIC_CONTEXT,
                        avatar.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA)
                );
                renderState.setRenderData(
                        ElectromasterWeaponEffectRenderer.IRON_SAND_CONTEXT,
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
                academy("omni_crafting_table"),
                OmniCraftingTableSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                academy("solar_gen"),
                SolarGenSpecialRenderer.Unbaked.MAP_CODEC
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
