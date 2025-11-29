package org.academy;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.academy.api.client.Render;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.screen.ScreenDispatcher;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.internal.client.app.Apps;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.hud.HUDManager;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.academy.internal.client.renderer.effect.RailgunEffectRenderer;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;

import java.io.File;
import java.util.function.BiConsumer;

import static org.academy.AcademyCraft.academy;

@Mod(value = AcademyCraft.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(Dist.CLIENT)
public final class AcademyCraftClient {
    public AcademyCraftClient(IEventBus modEventBus) {
        modEventBus.register(ModBusSubscriber.class);
    }

    @MainThread
    public static void initMain() {
        Screens.register();
        HUDManager.initMain();
        Apps.register();
        ParticleRenderTypes.init();
        AbilitySystemClient.init();
        ClientSyncManager.init();
    }

    @RenderThread
    public static void initRender() {
        Render.init();
        BloomEffect.init();
        ScreenDispatcher.init();
        HUDManager.initRender();
    }

    public static void init() {
        initMain();
        initRender();
    }

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        resize(event.getWidth(), event.getHeight());
    }

    public static void resize(int width, int height) {
        Render.resize();
        ScreenDispatcher.resize(width, height);
        PostEffect.resize(width, height);
        BloomEffect.resize(width, height);
        HUDManager.resize(width, height);
    }

    @SubscribeEvent
    public static void onClientPauseChange(ClientPauseChangeEvent.Post event) {
        Config.INSTANCE.save();
    }

    @SubscribeEvent
    public static void onExtractBlockOutlineRenderState(ExtractBlockOutlineRenderStateEvent event) {
        var state = event.getBlockState();
        var pillar = state.getBlock() == Blocks.WIND_GEN_PILLAR.get();
        var base = state.getBlock() == Blocks.WIND_GEN_BASE.get();
        var top = state.getBlock() == Blocks.WIND_GEN_TOP.get();
        if (pillar || base || top) {
            event.addCustomRenderer((blockOutlineRenderState, bufferSource, poseStack, _, levelRenderState) -> {
                poseStack.pushPose();
                var pos = blockOutlineRenderState.pos();
                var cam = levelRenderState.cameraRenderState.pos;
                var camX = cam.x;
                var camY = cam.y;
                var camZ = cam.z;
                poseStack.translate(pos.getX() - camX + 0.5f, pos.getY() - camY, pos.getZ() - camZ + 0.5f);
                if (top) {
                    poseStack.scale(1, 1f / 16f, 1);
                }
                if (base && state.getValue(MultiBlock.TYPE) == MultiBlock.MultiBlockType.MAIN) {
                    poseStack.scale(1, 15 / 16f, 1);
                    poseStack.translate(0, 1 / 16f, 0);
                }
                poseStack.mulPose(Axis.YN.rotationDegrees(22.5f));
                CylinderRenderer.renderCylinderWireframe(poseStack, bufferSource.getBuffer(RenderTypes.lines()), WindGenBaseModel.PILLAR_OUTLINE_VERTEX_BUFFER, 0, 0, 0, 0.4f);
                poseStack.popPose();
                return pillar || (base && (state.getValue(MultiBlock.TYPE) == MultiBlock.MultiBlockType.SUBJECT));
            });
        }
    }

    public static final class Config {
        public static final File CLIENT_CONFIG_FILE;
        public static final AcademyCraftConfig INSTANCE;

        static {
            CLIENT_CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config" + File.separator + AcademyCraft.MOD_ID + "-client" + ".json");
            AcademyCraft.checkFile(CLIENT_CONFIG_FILE);
            INSTANCE = new AcademyCraftConfig(CLIENT_CONFIG_FILE);
        }

        private Config() {
        }
    }

    private static final class ModBusSubscriber {
        private ModBusSubscriber() {
        }

        @SubscribeEvent
        public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
            event.registerEntityModifier(
                    UncheckedUtil.uncheckedCast(AvatarRenderer.class), stateModify()
            );
        }

        private static <AvatarlikeEntity extends Avatar & ClientAvatarEntity>
        BiConsumer<AvatarlikeEntity, AvatarRenderState> stateModify() {
            return (avatarlikeEntity, avatarRenderState) -> {
                avatarRenderState.setRenderData(
                        StormWingEffectRenderer.CONTEXT_KEY,
                        avatarlikeEntity.getData(AttachmentTypes.ACTIVATED_STORM_WING)
                );
                avatarRenderState.setRenderData(
                        RailgunEffectRenderer.CONTEXT_KEY,
                        avatarlikeEntity.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA)
                );
            };
        }

        @SubscribeEvent
        public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
            event.registerPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR);
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, academy("hud"), HUDManager::render);
        }

        @SubscribeEvent
        public static void onRegisterSpecialModelRenderer(RegisterSpecialModelRendererEvent event) {
            event.register(academy("wireless_node"), WirelessNodeSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(academy("wind_gen_base"), WindGenBaseSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(academy("wind_gen_pillar"), WindGenPillarSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(academy("wind_gen_top"), WindGenTopSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(academy("ability_developer"), AbilityDeveloperSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(academy("omni_crafting_table"), OmniCraftingTableSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(academy("solar_gen"), SolarGenSpecialRenderer.Unbaked.MAP_CODEC);
        }

        @SubscribeEvent
        public static void onRegisterSpecialBlockModelRenderer(RegisterSpecialBlockModelRendererEvent event) {
            event.register(Blocks.WIND_GEN_PILLAR.get(), WindGenPillarSpecialRenderer.Unbaked.INSTANCE);
        }
    }
}