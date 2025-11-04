package org.academy;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.academy.api.client.Render;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.hud.terminal.HUDController;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.renderer.CylinderRenderer;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.internal.client.app.Apps;
import org.academy.internal.client.gui.screen.Screens;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.client.renderer.special.*;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;

import java.io.File;
import java.util.function.BiConsumer;

@Mod(value = AcademyCraft.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class AcademyCraftClient {
    public AcademyCraftClient(IEventBus modEventBus) {
        modEventBus.register(ModBusSubscriber.class);
    }

    public static void init() {
        Screens.register();
        HUDManager.init();
        Apps.register();
        ParticleRenderTypes.init();
        AbilitySystemClient.init();
        HUDController.INSTANCE.init();
        BlurEffect.init();
    }

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        resize(event.getWidth(), event.getHeight());
    }

    public static void resize(int width, int height) {
        PostEffect.resize(width, height);
        BloomEffect.resize(width, height);
        BlurEffect.resize(width, height);
        HUDManager.resize(width, height);
        HUDController.INSTANCE.resize(width, height);
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
            event.addCustomRenderer((blockOutlineRenderState, bufferSource, poseStack, b, levelRenderState) -> {
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
                CylinderRenderer.renderCylinderWireframe(poseStack, bufferSource.getBuffer(RenderType.lines()), WindGenBaseModel.PILLAR_OUTLINE_VERTEX_BUFFER, 0, 0, 0, 0.4f);
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
            event.registerEntityModifier(baseRenderer(), stateModify());
        }

        private static <AvatarlikeEntity extends Avatar & ClientAvatarEntity> Class<AvatarRenderer<AvatarlikeEntity>> baseRenderer() {
            return UncheckedUtil.uncheckedCast(AvatarRenderer.class);
        }

        private static <AvatarlikeEntity extends Avatar & ClientAvatarEntity> BiConsumer<AvatarlikeEntity, AvatarRenderState> stateModify() {
            return (avatarlikeEntity, avatarRenderState) -> avatarRenderState.setRenderData(
                    StormWingEffectRenderer.CONTEXT_KEY,
                    avatarlikeEntity.getData(AttachmentTypes.ACTIVATED_STORM_WING)
            );
        }

        @SubscribeEvent
        public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
            event.registerPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR);
        }

        @SubscribeEvent
        public static void onRegisterSpecialModelRenderer(RegisterSpecialModelRendererEvent event) {
            event.register(AcademyCraft.academy("wireless_node"), WirelessNodeSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(AcademyCraft.academy("wind_gen_base"), WindGenBaseSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(AcademyCraft.academy("wind_gen_pillar"), WindGenPillarSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(AcademyCraft.academy("wind_gen_top"), WindGenTopSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(AcademyCraft.academy("ability_developer"), AbilityDeveloperSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(AcademyCraft.academy("omni_crafting_table"), OmniCraftingTableSpecialRenderer.Unbaked.MAP_CODEC);
         //   event.register(AcademyCraft.academy("imagiphase_dowsing_rod"), ImagiphaseDowsingRodSpecialRenderer.Unbaked.MAP_CODEC);
            event.register(AcademyCraft.academy("solar_gen"), SolarGenSpecialRenderer.Unbaked.MAP_CODEC);
        }

        @SubscribeEvent
        public static void onRegisterSpecialBlockModelRenderer(RegisterSpecialBlockModelRendererEvent event) {
            event.register(Blocks.WIND_GEN_PILLAR.get(), WindGenPillarSpecialRenderer.Unbaked.INSTANCE);
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
/*            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public int getTintColor() {
                    return 0XFF000000;
                }

                @Override
                public ResourceLocation getStillTexture() {
                    return ImagiphasePlasma.TEXTURE;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return ImagiphasePlasma.TEXTURE;
                }
            }, FluidTypes.IMAGIPHASE_PLASMA.get());*/
        }
    }
}