package org.academy;

import com.google.common.reflect.TypeToken;
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
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.academy.api.client.Render;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.screen.ScreenDispatcher;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.hud.terminal.TerminalHUD;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.util.FileUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.internal.client.app.music.backend.MusicPlayerBackend;
import org.academy.internal.client.app.music.ui.MusicApp;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.client.renderer.effect.RailgunEffectRenderer;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumInterferenceLayer;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;

import java.io.File;
import java.util.function.BiConsumer;

import static org.academy.AcademyCraft.academy;

@EventBusSubscriber(Dist.CLIENT)
@Mod(value = AcademyCraft.MOD_ID, dist = Dist.CLIENT)
public final class AcademyCraftClient {
    private static boolean renderInitialized = false;

    public static void initMain() {
        TerminalHUD.Companion.addApp(MusicApp.INSTANCE);

        MusicPlayerBackend.Companion.init();
        Screens.register();
        HUDManager.INSTANCE.initMain();
        AbilitySystemClient.init();
        ClientSyncManager.init();
    }

    public static void initRender() {
        Render.init();
        BloomEffect.init();
        ScreenDispatcher.Companion.init();
        HUDManager.INSTANCE.initRender();

        MsdfFontService.INSTANCE.genDefaultGlyph();

        if (IrisCompat.hasIris()) {
            IrisPipelines.assignPipeline(Render.RenderPipelines.LEVEL_POS_COLOR_QUADS, ShaderKey.BASIC_COLOR);
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
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        resize(event.getWidth(), event.getHeight());
    }

    public static void resize(int width, int height) {
        Render.resize();
        PostEffect.resize(width, height);
        HUDManager.INSTANCE.resize(width, height);
    }

    @SubscribeEvent
    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        Config.INSTANCE.save();
    }

    @SubscribeEvent
    public static void onClientStopped(ClientStoppedEvent event) {
        ImGuiUtilApi.INSTANCE.close();
        MsdfFontService.INSTANCE.close();
        MsdfAtlasManager.INSTANCE.closeAll();
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
                    _,
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
                CylinderRenderer.renderCylinderWireframe(
                        poseStack,
                        bufferSource.getBuffer(RenderTypes.lines()),
                        WindGenBaseModel.PILLAR_OUTLINE_VERTEX_BUFFER,
                        0, 0, 0, 0.4f
                );
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

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState renderState) {
                renderState.setRenderData(
                        StormWingEffectRenderer.CONTEXT_KEY,
                        avatar.getData(AttachmentTypes.ACTIVATED_STORM_WING)
                );
                renderState.setRenderData(
                        RailgunEffectRenderer.CONTEXT_KEY,
                        avatar.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA)
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
    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR);
        event.registerPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR_HELLFLARE);
        event.registerPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR_HELLFLARE_ADDITIVE);
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
}
