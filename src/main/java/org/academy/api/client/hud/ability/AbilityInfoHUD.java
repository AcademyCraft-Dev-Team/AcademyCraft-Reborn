package org.academy.api.client.hud.ability;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.Render;
import org.academy.api.client.Resource;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.render.UiContext;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.render.TextureBinding;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.academy.api.client.ability.AbilitySystemClient.getAvailableCP;
import static org.academy.api.client.ability.AbilitySystemClient.getMaxCP;

public final class AbilityInfoHUD {
    @Nullable
    private static AbilityInfoHUD INSTANCE;

    private final Context context = new Context();
    private final UiContext uiContext = new UiContext();

    private AbilityInfoHUD() {
    }

    public static AbilityInfoHUD getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("AbilityInfoHUD has not been initialized.");
        return INSTANCE;
    }

    public static void initMain() {
        INSTANCE = new AbilityInfoHUD();
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    public void perform(double mouseX, double mouseY, float deltaPartialTick) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick);
    }

    public void render(RenderTarget target) {
        if (!AbilitySystemClient.isActiveHUD()) return;
        uiContext.upload(target, false);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        context.get().tick();
    }

    private static class Context implements WidgetContext {
        final FrameLayoutWidget root = createRoot();

        @Override
        public WidgetContainer get() {
            return root;
        }

        FrameLayoutWidget createRoot() {
            var root = new FrameLayoutWidget();
            {
                var cp = new FrameLayoutWidget();
                cp.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .size(240, 27)
                                .margin(0, 4, 4, 0)
                                .gravity(Gravity.TOP_RIGHT)
                );
                root.addChild("cp", cp);
                {
                    var back = new ImageWidget(Resource.Textures.CP_BAR_BACKGROUND);
                    back.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT)
                    );
                    cp.addChild("back", back);

                    var content = new AbstractWidget() {
                        @Nullable GpuTextureView textureView;
                        final GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                        final List<Particle> particles = new ArrayList<>();
                        float lastCp = getAvailableCP();
                        float visualCp = getAvailableCP();

                        @Override
                        protected void renderInternal(RenderContext context) {
                            super.renderInternal(context);
                            if (textureView == null || textureView.isClosed()) {
                                try {
                                    var tex = Minecraft.getInstance().getTextureManager().getTexture(Resource.Textures.CP_BAR_VALUE);
                                    textureView = tex.getTextureView();
                                } catch (Exception e) {
                                    return;
                                }
                            }
                            if (textureView == null) return;

                            var spacing = 7f / 4f;
                            var topPadding = 21f / 4f;
                            var bottomPadding = 56f / 4f;
                            var leftPadding = 107f / 4f;
                            var rightPadding = 130f / 4f;

                            // because tan(45°) = 1
                            var progress = visualCp / getMaxCP();
                            var offset = height - topPadding - bottomPadding;
                            var i = 10 - Mth.ceil(progress / 0.1f);
                            var barWidth = width - leftPadding - rightPadding - offset - 9 * spacing;
                            var progressOffsetX = (1 - progress) * barWidth + i * spacing;

                            var topPaddingU = topPadding / height;
                            var bottomPaddingU = bottomPadding / height;

                            var topLeft = leftPadding + progressOffsetX;
                            var bottomLeft = topLeft + offset;
                            var topRight = width - rightPadding - offset;
                            var bottomRight = topRight + offset;

                            var topLeftU = topLeft / width;
                            var bottomLeftU = bottomLeft / width;
                            var topRightU = topRight / width;
                            var bottomRightU = bottomRight / width;

                            var alpha = getAlpha() * context.getAccumulatedAlpha();

                            context.submit(new DrawCommand(
                                    Render.RenderPipelines.IMAGE,
                                    List.of(new TextureBinding("Sampler0", textureView, sampler)),
                                    List.of()
                            ) {
                                @Override
                                public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
                                    consumer.addVertex(pose, topLeft, topPadding, 0).setUv(topLeftU, topPaddingU).setColor(1, 1, 1, alpha);
                                    consumer.addVertex(pose, bottomLeft, height - bottomPadding, 0).setUv(bottomLeftU, 1 - bottomPaddingU).setColor(1, 1, 1, alpha);
                                    consumer.addVertex(pose, bottomRight, height - bottomPadding, 0).setUv(bottomRightU, 1 - bottomPaddingU).setColor(1, 1, 1, alpha);
                                    consumer.addVertex(pose, topRight, topPadding, 0).setUv(topRightU, topPaddingU).setColor(1, 1, 1, alpha);
                                }
                            });

                            for (var particle : particles) {
                                var currentI = 10 - Mth.ceil(particle.current / 0.1f);
                                var lastI = 10 - Mth.ceil(particle.last / 0.1f);
                                var currentOffsetX = (1 - particle.current) * barWidth + currentI * spacing;
                                var lastOffsetX = (1 - particle.last) * barWidth + lastI * spacing;

                                var leftX = particle.increase ? currentOffsetX : lastOffsetX;
                                var rightX = particle.increase ? lastOffsetX : currentOffsetX;

                                var particleTopLeft = leftPadding + leftX;
                                var particleTopRight = leftPadding + rightX;
                                var particleBottomLeft = particleTopLeft + offset;
                                var particleBottomRight = particleTopRight + offset;

                                var particleTopLeftU = particleTopLeft / width;
                                var particleTopRightU = particleTopRight / width;
                                var particleBottomLeftU = particleBottomLeft / width;
                                var particleBottomRightU = particleBottomRight / width;

                                context.pose().pushPose();
                                {
                                    context.pose().translate(particle.posOffset, particle.posOffset, 0);

                                    context.submit(new DrawCommand(
                                            Render.RenderPipelines.IMAGE,
                                            List.of(new TextureBinding("Sampler0", textureView, sampler)),
                                            List.of()
                                    ) {
                                        @Override
                                        public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
                                            consumer.addVertex(pose, particleTopLeft, topPadding, 0).setUv(particleTopLeftU, topPaddingU).setColor(1, 1, 1, particle.alpha);
                                            consumer.addVertex(pose, particleBottomLeft, height - bottomPadding, 0).setUv(particleBottomLeftU, 1 - bottomPaddingU).setColor(1, 1, 1, particle.alpha);
                                            consumer.addVertex(pose, particleBottomRight, height - bottomPadding, 0).setUv(particleBottomRightU, 1 - bottomPaddingU).setColor(1, 1, 1, particle.alpha);
                                            consumer.addVertex(pose, particleTopRight, topPadding, 0).setUv(particleTopRightU, topPaddingU).setColor(1, 1, 1, particle.alpha);
                                        }
                                    });
                                }
                                context.pose().popPose();
                            }
                        }

                        @Override
                        public void tick() {
                            var animationTime = 750L;
                            var currentCp = getAvailableCP();

                            if (currentCp != lastCp) {
                                var maxCp = getMaxCP();
                                var lastProgress = lastCp / maxCp;
                                var currentProgress = currentCp / maxCp;
                                var deltaProgress = currentProgress - lastProgress;
                                var increase = deltaProgress > 0;

                                if (!increase) {
                                    visualCp = currentCp;
                                    var iterator = particles.iterator();
                                    while (iterator.hasNext()) {
                                        var particle = iterator.next();
                                        if (particle.increase) {
                                            iterator.remove();
                                            if (particle.animator != null) particle.animator.cancel();
                                        }
                                    }
                                }

                                var progressTracker = lastProgress;
                                var i = 0;
                                while (true) {
                                    var start = progressTracker;
                                    float end;

                                    if (increase) {
                                        var nextBoundary = (float) (Mth.floor(start / 0.1f) + 1) * 0.1f;
                                        end = Math.min(nextBoundary, currentProgress);
                                    } else {
                                        var nextBoundary = (float) Mth.ceil(start / 0.1f - 1) * 0.1f;
                                        end = Math.max(nextBoundary, currentProgress);
                                    }

                                    var progressChanged = Math.abs(start - end) > 0;
                                    if (!progressChanged) break;

                                    var particle = new Particle(start, end, increase);
                                    var animator = ObjectAnimator
                                            .ofFloat(particle::setProgress, 0, 1)
                                            .setDuration(animationTime).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
                                            .setStartDelay(i * animationTime / 10);

                                    animator.addListener(new AnimatorListener() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            particles.remove(particle);
                                            if (particle.increase) {
                                                visualCp = end * maxCp;
                                            }
                                        }
                                    });
                                    animator.start();
                                    particle.setAnimator(animator);
                                    particles.add(particle);

                                    progressTracker = end;
                                    i++;

                                    if (progressTracker == currentProgress) break;
                                }
                                lastCp = currentCp;
                            }
                        }

                        static class Particle {
                            @Nullable Animator animator;
                            final float last, current;
                            final boolean increase;
                            float posOffset, alpha = 1;

                            Particle(float last, float current, boolean increase) {
                                this.last = last;
                                this.current = current;
                                this.increase = increase;
                            }

                            void setProgress(float progress) {
                                posOffset = increase ? -10 + progress * 10 : progress * -10;
                                alpha = increase ? progress : 1 - progress;
                            }

                            void setAnimator(Animator animator) {
                                this.animator = animator;
                            }
                        }
                    };
                    content.setLayoutParams(
                            new FrameLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT)
                    );
                    cp.addChild("content", content);
                }
            }
            return root;
        }
    }
}