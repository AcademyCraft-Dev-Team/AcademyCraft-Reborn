package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.renderer.BallRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.internal.client.renderer.entity.state.HellFlareRayRenderState;
import org.academy.internal.common.world.entity.skill.HellFlareRay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.academy.AcademyCraft.academy;

public class HellFlareRayRenderer extends EntityRenderer<HellFlareRay, HellFlareRayRenderState> {

    public static final float[][] ORB_BUFFER = VertexUtil.Ball.getIcosphereVertexBuffer(1, 2, true);
    private static final int FULL_BRIGHT = 15728880;
    private static final Identifier P3_STEAM_TEX = academy("textures/ability/meltdowner/ray/hellflare_steam.png");
    private static final RenderType P3_STEAM_TYPE = Render.RenderTypes.getHellFlareSteam(P3_STEAM_TEX);
    private static final RenderType P3_STEAM_BLOOM_TYPE = Render.RenderTypes.getHellFlareSteamBloom(P3_STEAM_TEX);

    private static final class C {

        static final int TUBE_SIDES = 8;
        static final int PT_COUNT = 64;
        static final float TUBE_FLOW = 0.15f;
        static final float T_P1_END = 120.0f;
        static final float T_P2_END = 240.0f;
        static final float TRANSITION = 24.0f;

        static final float P1_SCALE = 0.6f;
        static final float P1_CORE_R = 0.1f * P1_SCALE;
        static final float P1_GLOW_R = 0.2f * P1_SCALE;
        static final int P1_COL_CORE = 0xFFFF6F45;
        static final int P1_COL_GLOW = 0xCC661018;
        static final float P1_TUBE_SCALE = 2.2f * P1_SCALE;
        static final float P1_TUBE_STR = 0.025f;
        static final float P1_ORB_SCALE = 0.35f;

        static final float P2_CORE_R = 0.15f;
        static final float P2_GLOW_R = 0.3f;
        static final int P2_COL_CORE_S = 0xFFFFE9A6, P2_COL_CORE_E = 0xFFFFC55E;
        static final int P2_COL_GLOW_S = 0xD0FF9D32, P2_COL_GLOW_E = 0xB8FF6E14;
        static final float P2_TUBE_SCALE = 2.2f;
        static final float P2_TUBE_STR = 0.04f;
        static final float P2_ROT_SPD = 6.0f;
        static final float P2_SPIRAL = 4.0f;
        static final float P2_TAPER_S = 0.8f, P2_TAPER_E = 0.2f;
        static final float P2_PT_SIZE = 0.05f;
        static final float P2_PT_SCATTER = 0.35f;
        static final float P2_ORB_SCALE = 0.7f;

        static final float P3_CORE_R = 0.12f;
        static final float P3_GLOW_R = 0.42f;
        static final int P3_COL_CORE = 0xFFF3FBFF;
        static final int P3_COL_GLOW = 0xFF76BBFF;
        static final float P3_TUBE_SCALE = 1.0f;
        static final float P3_ORB_SCALE = 0.38f;
        static final float P3_ROT_SPD = 2.2f;
        static final float P3_SPIRAL = 1.5f;

        static final float P1_STEAM_SCALE = 0.34f;
        static final float P2_STEAM_SCALE = 1.36f;
        static final float P3_STEAM_SCALE = 1.85f;
        static final float P1_STEAM_ALPHA = 0.42f;
        static final float P2_STEAM_ALPHA = 1.56f;
        static final float P3_STEAM_ALPHA = 1.60f;
    }

    private record RenderParams(
            float coreR, float glowR,
            int cCoreS, int cCoreE,
            int cGlowS, int cGlowE,
            float tubeScale, float tubeStr,
            float rotSpd, float spiralFactor,
            float orbScale
    ) {}

    public HellFlareRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static int lerpColor(float t, int c1, int c2) {
        var a = (int) Mth.lerp(t, (c1 >> 24) & 0xFF, (c2 >> 24) & 0xFF);
        var r = (int) Mth.lerp(t, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
        var g = (int) Mth.lerp(t, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
        var b = (int) Mth.lerp(t, c1 & 0xFF, c2 & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public HellFlareRayRenderState createRenderState() {
        return new HellFlareRayRenderState();
    }

    @Override
    public void extractRenderState(HellFlareRay entity, HellFlareRayRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity.getOwner() == null) {
            state.isValid = false;
            return;
        }
        state.isValid = true;
        state.targetId = entity.getId();

        var owner = entity.getOwner();
        var lookVec = owner.getViewVector(partialTick).normalize();
        var look = new Vector3f((float)lookVec.x, (float)lookVec.y, (float)lookVec.z);
        var up = new Vector3f(0, 1, 0);
        var right = new Vector3f(look).cross(up).normalize();

        var offset = new Vector3f(right).mul(0.6f).add(new Vector3f(up).mul(-0.4f)).add(new Vector3f(look).mul(0.9f));
        var eyePosVec = owner.getEyePosition(partialTick);
        var startVec = new Vector3f((float)eyePosVec.x, (float)eyePosVec.y, (float)eyePosVec.z).add(offset);
        state.startPos.set(startVec.x, startVec.y, startVec.z);

        var beamLen = Math.max(0.1f, entity.getBeamLength());
        var target = entity.getTargetEntity();
        if (target != null) {
            var tPos = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
            state.endPos.set((float) tPos.x, (float) tPos.y, (float) tPos.z);
        } else {
            var endVec = eyePosVec.add(lookVec.scale(beamLen));
            state.endPos.set((float) endVec.x, (float) endVec.y, (float) endVec.z);
        }

        state.length = state.startPos.distance(state.endPos);
        state.direction = new Vector3f(state.endPos).sub(state.startPos).normalize();
        state.age = entity.tickCount + partialTick;
        state.phase = entity.getPhase();
    }

    private RenderParams calcParams(HellFlareRayRenderState state) {
        var t12 = phaseBlend12(state);
        var t23 = phaseBlend23(state);

        var coreR12 = Mth.lerp(t12, C.P1_CORE_R, C.P2_CORE_R);
        var glowR12 = Mth.lerp(t12, C.P1_GLOW_R, C.P2_GLOW_R);
        var coreS12 = lerpColor(t12, C.P1_COL_CORE, C.P2_COL_CORE_S);
        var coreE12 = lerpColor(t12, C.P1_COL_CORE, C.P2_COL_CORE_E);
        var glowS12 = lerpColor(t12, C.P1_COL_GLOW, C.P2_COL_GLOW_S);
        var glowE12 = lerpColor(t12, C.P1_COL_GLOW, C.P2_COL_GLOW_E);
        var tubeScale12 = Mth.lerp(t12, C.P1_TUBE_SCALE, C.P2_TUBE_SCALE);
        var tubeStr12 = Mth.lerp(t12, C.P1_TUBE_STR, C.P2_TUBE_STR);
        var rot12 = Mth.lerp(t12, 0.0f, C.P2_ROT_SPD);
        var spiral12 = Mth.lerp(t12, 0.0f, C.P2_SPIRAL);
        var orb12 = Mth.lerp(t12, C.P1_ORB_SCALE, C.P2_ORB_SCALE);

        return new RenderParams(
                Mth.lerp(t23, coreR12, C.P3_CORE_R),
                Mth.lerp(t23, glowR12, C.P3_GLOW_R),
                lerpColor(t23, coreS12, C.P3_COL_CORE),
                lerpColor(t23, coreE12, C.P3_COL_CORE),
                lerpColor(t23, glowS12, C.P3_COL_GLOW),
                lerpColor(t23, glowE12, C.P3_COL_GLOW),
                Mth.lerp(t23, tubeScale12, C.P3_TUBE_SCALE),
                Mth.lerp(t23, tubeStr12, C.P2_TUBE_STR),
                Mth.lerp(t23, rot12, C.P3_ROT_SPD),
                Mth.lerp(t23, spiral12, C.P3_SPIRAL),
                Mth.lerp(t23, orb12, C.P3_ORB_SCALE)
        );
    }

    private void drawOrbLayers(HellFlareRayRenderState state, PoseStack poseStack, RenderParams p) {
        var bufferSource = BloomEffect.getBlitToMainPost();
        var additiveBuilder = bufferSource.getBuffer(Render.RenderTypes.POS_COLOR_TRANGLES_BLOOM_ADDITIVE);
        var mainBuilder = bufferSource.getBuffer(Render.RenderTypes.POS_COLOR_TRANGLES);

        var random = RandomSource.create((long) (state.age * 50));
        var cameraRotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        var t12 = phaseBlend12(state);
        var t23 = phaseBlend23(state);
        var flare = Geometry.prominence(0.52f, state.age * 0.9f, 1.6f);
        var spot = Geometry.sunspot(0.33f, state.age * 0.7f, 2.3f);
        var tone = 1.0f - spot * 0.10f + flare * 0.13f;
        var coreColor = Geometry.scaleRgb(p.cCoreS, tone);
        var glowColor = Geometry.scaleRgb(lerpColor(t23, p.cGlowS, C.P3_COL_GLOW), 1.0f + flare * 0.08f);
        var haloColor = lerpColor(t23, p.cGlowE, 0xFFB6D9FF);

        var coreR = ((coreColor >> 16) & 0xFF) / 255.0f;
        var coreG = ((coreColor >> 8) & 0xFF) / 255.0f;
        var coreB = (coreColor & 0xFF) / 255.0f;
        var glowR = ((glowColor >> 16) & 0xFF) / 255.0f;
        var glowG = ((glowColor >> 8) & 0xFF) / 255.0f;
        var glowB = (glowColor & 0xFF) / 255.0f;
        var haloR = ((haloColor >> 16) & 0xFF) / 255.0f;
        var haloG = ((haloColor >> 8) & 0xFF) / 255.0f;
        var haloB = (haloColor & 0xFF) / 255.0f;

        var rel = new Vector3f(state.startPos).sub((float)state.x, (float)state.y, (float)state.z);

        poseStack.pushPose();
        poseStack.translate(rel.x, rel.y, rel.z);
        poseStack.mulPose(cameraRotation);

        poseStack.pushPose();
        var coreScale = p.orbScale * Mth.lerp(t12, 0.36f, 0.42f);
        poseStack.scale(coreScale, coreScale, coreScale);
        BallRenderer.renderBall(poseStack.last(), mainBuilder, ORB_BUFFER, coreR, coreG, coreB, 1.0f, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.pushPose();
        var pulse1 = 1.0f + random.nextFloat() * 0.08f + flare * 0.07f;
        var s1 = p.orbScale * 0.72f * pulse1;
        poseStack.scale(s1, s1, s1);
        BallRenderer.renderBall(poseStack.last(), additiveBuilder, ORB_BUFFER, glowR, glowG, glowB, 0.74f, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.pushPose();
        var pulse2 = 1.0f + random.nextFloat() * 0.12f + t23 * 0.1f;
        var s2 = p.orbScale * (1.15f + 0.12f * t23) * pulse2;
        poseStack.scale(s2, s2, s2);
        BallRenderer.renderBall(poseStack.last(), additiveBuilder, ORB_BUFFER, haloR, haloG, haloB, 0.40f + 0.12f * t23, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        if (t23 > 0.02f) {
            poseStack.pushPose();
            var pulse3 = 1.0f + random.nextFloat() * 0.16f + flare * 0.1f;
            var s3 = p.orbScale * (1.32f + 0.28f * t23) * pulse3;
            poseStack.scale(s3, s3, s3);
            BallRenderer.renderBall(poseStack.last(), additiveBuilder, ORB_BUFFER, 0.80f, 0.90f, 1.0f, 0.22f + 0.28f * t23, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        if (spot > 0.08f) {
            poseStack.pushPose();
            var s4 = p.orbScale * 0.48f;
            poseStack.scale(s4, s4, s4);
            var dark = 0.17f + 0.18f * spot;
            BallRenderer.renderBall(poseStack.last(), mainBuilder, ORB_BUFFER, dark, dark * 0.76f, dark * 0.82f, 0.28f * spot, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        if (t12 < 0.01f) {
            poseStack.pushPose();
            var lavaPulse = 1.0f + random.nextFloat() * 0.18f;
            var s5 = p.orbScale * 0.95f * lavaPulse;
            poseStack.scale(s5, s5, s5);
            BallRenderer.renderBall(poseStack.last(), additiveBuilder, ORB_BUFFER, 1.0f, 0.54f, 0.36f, 0.28f, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
    private void drawCore(HellFlareRayRenderState state, Matrix4f mat, VertexConsumer consumer, RenderParams p) {
        var t12 = phaseBlend12(state);
        var t23 = phaseBlend23(state);
        var step = t12 > 0.02f;
        Geometry.drawVolumetric(mat, consumer, state.length, p.coreR, 0.15f, 4.0f, 0.4f, 0.01f, p.cCoreS, p.cCoreE, step, state, 0, 3, false, p.rotSpd, p.spiralFactor);
        Geometry.drawVolumetric(mat, consumer, state.length, p.glowR, 0.15f, 4.0f, 1.0f, 0.02f, p.cGlowS, p.cGlowE, step, state, 100, 3, true, p.rotSpd, p.spiralFactor);
        if (t23 > 0.01f) {
            var flare = Geometry.prominence(0.47f, state.age * 0.8f, 2.6f);
            var spot = Geometry.sunspot(0.29f, state.age * 0.52f, 1.4f);
            var hotTone = 1.0f - spot * 0.08f + flare * 0.14f;
            var hotStart = Geometry.scaleRgb(lerpColor(t23, p.cCoreS, 0xFFF7FCFF), hotTone);
            var hotEnd = Geometry.scaleRgb(lerpColor(t23, p.cGlowE, 0xFF93CFFF), 0.94f + flare * 0.12f);
            var hotRadius = p.coreR * (0.55f + 0.35f * t23);
            var hotAmp = 0.35f + 0.50f * t23;
            Geometry.drawVolumetric(mat, consumer, state.length, hotRadius, 0.26f, 10.0f, hotAmp, 0.01f, hotStart, hotEnd, true, state, 239, 5, true, p.rotSpd * 2.1f, p.spiralFactor * 3.8f + 0.8f);

            var flareStart = lerpColor(t23, p.cGlowS, 0xFFB1DBFF);
            var flareEnd = lerpColor(t23, p.cGlowE, 0xFF6EB8FF);
            var flareRadius = p.glowR * (0.68f + 0.22f * t23);
            var flareAmp = 0.28f + 0.32f * t23;
            Geometry.drawVolumetric(mat, consumer, state.length, flareRadius, 0.18f, 7.0f, flareAmp, 0.016f, flareStart, flareEnd, true, state, 377, 4, true, p.rotSpd * 1.8f, p.spiralFactor * 2.5f + 0.6f);
        }
    }

    @Override
    public void submit(HellFlareRayRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.isValid || state.length < 0.2f) return;

        var p = calcParams(state);
        var t12 = phaseBlend12(state);
        var t23 = phaseBlend23(state);
        drawOrbLayers(state, poseStack, p);

        collector.submitCustomGeometry(poseStack, Render.RenderTypes.DISTORTION_TUBE_TYPE,
                (pose, consumer) -> drawDistortion(state, pose, consumer, p.tubeScale, p.tubeStr));

        collector.submitCustomGeometry(poseStack, Render.RenderTypes.POS_COLOR_QUADS_BLOOM,
                (pose, consumer) -> {
                    var mat = prepareMatrix(state, pose);
                    var alpha12 = Mth.lerp(t12, 0x52, 0x64);
                    var alphaBase = (int) Mth.lerp(t23, alpha12, 0xC8);
                    var alpha = alphaBase << 24;
                    var cGS_A = (p.cGlowS & 0x00FFFFFF) | alpha;
                    var cGE_A = (p.cGlowE & 0x00FFFFFF) | alpha;
                    var glowScale12 = Mth.lerp(t12, 1.45f, 1.8f);
                    var glowScale = Mth.lerp(t23, glowScale12, 2.8f);
                    Geometry.drawBillboardGlow(mat, consumer, state.length, p.glowR * glowScale, cGS_A, cGE_A, state, camera);
                });

        collector.submitCustomGeometry(poseStack, Render.RenderTypes.POS_COLOR_QUADS_BLOOM,
                (pose, consumer) -> {
                    var mat = prepareMatrix(state, pose);
                    drawCore(state, mat, consumer, p);
                    drawParticles(state, mat, consumer, p);
                    if (t23 > 0.01f) {
                        var alpha = Mth.clamp((int) (255.0f * t23), 0, 255);
                        var coreHot = (C.P3_COL_CORE & 0x00FFFFFF) | (0xFF << 24);
                        coreHot = (coreHot & 0x00FFFFFF) | (alpha << 24);
                        Geometry.drawBillboardGlow(mat, consumer, state.length, p.glowR * (0.8f + 0.45f * t23), coreHot, coreHot, state, camera);
                    }
                });
        collector.submitCustomGeometry(poseStack, Render.RenderTypes.POS_COLOR_QUADS_BLOOM_POST,
                (pose, consumer) -> {
                    if (t23 <= 0.01f) return;
                    var mat = prepareMatrix(state, pose);
                    var alpha = Mth.clamp((int) (255.0f * t23), 0, 255);
                    var coreBloom = (C.P3_COL_CORE & 0x00FFFFFF) | (alpha << 24);
                    Geometry.drawBillboardGlow(mat, consumer, state.length, p.glowR * (1.9f + 1.9f * t23), coreBloom, coreBloom, state, camera);
                });
        collector.submitCustomGeometry(poseStack, P3_STEAM_TYPE,
                (pose, consumer) -> {
                    var mat = prepareMatrix(state, pose);
                    Geometry.drawSteamBandLit(mat, consumer, state, p);
                });
        collector.submitCustomGeometry(poseStack, P3_STEAM_BLOOM_TYPE,
                (pose, consumer) -> {
                    var mat = prepareMatrix(state, pose);
                    Geometry.drawSteamBandBloom(mat, consumer, state, p);
                });

    }

    private void drawParticles(HellFlareRayRenderState state, Matrix4f mat, VertexConsumer consumer, RenderParams p) {
        var rand = RandomSource.create(state.targetId * 31L);
        var t12 = phaseBlend12(state);
        var t23 = phaseBlend23(state);
        var scatter12 = Mth.lerp(t12, 0.35f, C.P2_PT_SCATTER);
        var baseSize12 = Mth.lerp(t12, 0.05f, C.P2_PT_SIZE);
        var scatterR = Mth.lerp(t23, scatter12, 0.35f);
        var baseSize = Mth.lerp(t23, baseSize12, 0.05f);
        var step = t12 > 0.02f;
        for (var i = 0; i < C.PT_COUNT; i++) {
            var t = (float) Math.pow(rand.nextFloat(), 3.0) + (state.age * 0.15f * (0.8f + 0.4f * rand.nextFloat()));
            t = (t * 0.1f) % 1.0f;
            var ang = rand.nextFloat() * Mth.TWO_PI;
            var dist = scatterR * (0.6f + rand.nextFloat() * 0.4f);
            var x = Mth.cos(ang) * dist;
            var y = Mth.sin(ang) * dist;
            var baseCol = Geometry.calcColor(p.cCoreS, p.cCoreE, t, 0, step, state.age, state.phase, false);
            var pCol = (baseCol & 0x00FFFFFF) | ((int) (200 + rand.nextFloat() * 55) << 24);
            if (t23 > 0.01f) {
                var darkAlpha = (int) Mth.lerp(t23, (float) ((pCol >> 24) & 0xFF), 0xAA);
                pCol = (baseCol & 0x00FFFFFF) | (darkAlpha << 24);
            }
            var sz = baseSize * (0.6f + rand.nextFloat() * 0.6f) * (1.0f - t * 0.6f);
            Geometry.addQuad(mat, consumer, x-sz, y-sz, t*state.length, x+sz, y-sz, t*state.length, x+sz, y+sz, t*state.length, x-sz, y+sz, t*state.length, pCol, pCol);
        }
    }

    private Matrix4f prepareMatrix(HellFlareRayRenderState state, PoseStack.Pose pose) {
        var m = new Matrix4f(pose.pose());
        var rel = new Vector3f(state.startPos).sub((float)state.x, (float)state.y, (float)state.z);
        m.translate(rel.x, rel.y, rel.z);
        var d = new Vector3f(state.direction);
        if (d.lengthSquared() < 1e-6f) d.set(0, 0, 1);
        m.rotate(new Quaternionf().rotateTo(new Vector3f(0, 0, 1), d.normalize()));
        return m;
    }

    static float getEnv(float t, float age, int phase) {
        var base = (float) Math.pow(Mth.sin(t * Mth.PI), 0.3);
        var tapered = Mth.lerp(t, C.P2_TAPER_S, C.P2_TAPER_E);
        var t12 = phaseBlend12(age, phase);
        var t23 = phaseBlend23(age, phase);
        var p2Weight = t12 * (1.0f - t23);
        var blend = Mth.clamp(p2Weight * 1.35f, 0.0f, 1.0f);
        return Mth.lerp(blend, base, tapered);
    }

    private void drawDistortion(HellFlareRayRenderState state, PoseStack.Pose pose, VertexConsumer consumer, float rScale, float str) {
        var mat = prepareMatrix(state, pose);
        var len = state.length;
        var segs = Math.max(2, (int) (len * 1.5f));
        var rBase = 0.2f * rScale;
        float[] cX = new float[C.TUBE_SIDES + 1], cY = new float[C.TUBE_SIDES + 1];
        for (var i = 0; i <= C.TUBE_SIDES; i++) {
            var a = (float) (i * 2 * Math.PI / C.TUBE_SIDES);
            cX[i] = Mth.cos(a);
            cY[i] = Mth.sin(a);
        }
        for (var i = 0; i < segs; i++) {
            float t1 = (float) i / segs, t2 = (float) (i + 1) / segs;
            float z1 = t1 * len, z2 = t2 * len;
            float env1 = getEnv(t1, state.age, state.phase), env2 = getEnv(t2, state.age, state.phase);
            var r1 = rBase * (1.0f + Mth.sin(t1 * 3.0f - state.age * 0.1f) * 0.1f) * env1;
            var r2 = rBase * (1.0f + Mth.sin(t2 * 3.0f - state.age * 0.1f) * 0.1f) * env2;
            var vOff = -state.age * C.TUBE_FLOW;
            float tv1 = z1 * 0.5f + vOff, tv2 = z2 * 0.5f + vOff;
            for (var s = 0; s < C.TUBE_SIDES; s++) {
                consumer.addVertex(mat, cX[s] * r2, cY[s] * r2, z2).setUv((float) s / C.TUBE_SIDES, tv2).setNormal(str, 0, 0);
                consumer.addVertex(mat, cX[s + 1] * r2, cY[s + 1] * r2, z2).setUv((float) (s + 1) / C.TUBE_SIDES, tv2).setNormal(str, 0, 0);
                consumer.addVertex(mat, cX[s + 1] * r1, cY[s + 1] * r1, z1).setUv((float) (s + 1) / C.TUBE_SIDES, tv1).setNormal(str, 0, 0);
                consumer.addVertex(mat, cX[s] * r1, cY[s] * r1, z1).setUv((float) s / C.TUBE_SIDES, tv1).setNormal(str, 0, 0);
            }
        }
    }

    private static float noise1(float x) { float fl = Mth.floor(x); return Mth.lerp(x-fl, hash(fl), hash(fl+1.0f)); }
    private static float hash(float n) { return Mth.frac(Mth.sin(n) * 43758.5453f); }
    private static float phaseBlend(float age, float center, float width) {
        var half = width * 0.5f;
        var t = Mth.clamp((age - (center - half)) / width, 0.0f, 1.0f);
        return (float) Mth.smoothstep(t);
    }

    private static float phaseBlend12(HellFlareRayRenderState state) {
        return phaseBlend12(state.age, state.phase);
    }

    private static float phaseBlend23(HellFlareRayRenderState state) {
        return phaseBlend23(state.age, state.phase);
    }

    private static float phaseBlend12(float age, int phase) {
        return phase >= 2 ? 1.0f : 0.0f;
    }

    private static float phaseBlend23(float age, int phase) {
        return phase >= 3 ? 1.0f : 0.0f;
    }

    private static class Geometry {
        static void drawBillboardGlow(Matrix4f m, VertexConsumer c, float len, float radius, int cStart, int cEnd, HellFlareRayRenderState s, CameraRenderState camera) {
            var camPos = camera.pos;
            var worldStart = new Vector3f(s.startPos);
            var relCam = new Vector3f((float)camPos.x - worldStart.x, (float)camPos.y - worldStart.y, (float)camPos.z - worldStart.z);
            var invRot = new Quaternionf().rotateTo(s.direction, new Vector3f(0, 0, 1));
            var localCam = invRot.transform(relCam);
            float dx = localCam.x, dy = localCam.y;
            var mag = (float) Math.sqrt(dx * dx + dy * dy);
            if (mag < 1e-5f) { dx = 1.0f; dy = 0.0f; mag = 1.0f; }
            float ux = -dy / mag, uy = dx / mag;

            var segs = Math.max(8, (int) (len * 4.0f));
            for (var i = 0; i < segs; i++) {
                float t1 = (float) i / segs, t2 = (float) (i + 1) / segs;
                float z1 = t1 * len, z2 = t2 * len;
                var r1 = radius * (0.95f + 0.05f * Mth.sin(s.age * 0.5f + t1 * 10)) * getEnv(t1, s.age, s.phase);
                var r2 = radius * (0.95f + 0.05f * Mth.sin(s.age * 0.5f + t2 * 10)) * getEnv(t2, s.age, s.phase);

                var c1 = calcColor(cStart, cEnd, t1, 0, true, s.age, s.phase, false);
                var c2 = calcColor(cStart, cEnd, t2, 0, true, s.age, s.phase, false);
                var c1E = c1 & 0x00FFFFFF;
                var c2E = c2 & 0x00FFFFFF;

                addQuad(m, c, 0.0f, 0.0f, z1, ux*r1, uy*r1, z1, ux*r2, uy*r2, z2, 0.0f, 0.0f, z2, c1, c1E, c2E, c2);
                addQuad(m, c, 0.0f, 0.0f, z1, 0.0f, 0.0f, z2, -ux*r2, -uy*r2, z2, -ux*r1, -uy*r1, z1, c1, c2, c2E, c1E);
            }
        }

        static void drawVolumetric(Matrix4f m, VertexConsumer c, float len, float rBase, float spd, float frq, float amp, float nAmp, int cS, int cE, boolean step, HellFlareRayRenderState s, int seed, int planes, boolean overload, float rotSpd, float spiral) {
            var segs = Math.max(4, (int) (len * 8.0f));
            for (var i = 0; i < segs; i++) {
                float t1 = (float) i / segs, t2 = (float) (i + 1) / segs;
                var rot1 = s.age * rotSpd + t1 * spiral;
                var rot2 = s.age * rotSpd + t2 * spiral;
                float wf1 = wave(t1 * frq - s.age * spd), wf2 = wave(t2 * frq - s.age * spd);
                float r1 = rBase * (1 + wf1 * amp) * getEnv(t1, s.age, s.phase), r2 = rBase * (1 + wf2 * amp) * getEnv(t2, s.age, s.phase);
                int col1 = calcColor(cS, cE, t1, wf1, step, s.age, s.phase, overload), col2 = calcColor(cS, cE, t2, wf2, step, s.age, s.phase, overload);
                float ox1 = jitter(t1, s.age, seed, nAmp), oy1 = jitter(t1, s.age, seed+13, nAmp);
                float ox2 = jitter(t2, s.age, seed, nAmp), oy2 = jitter(t2, s.age, seed+13, nAmp);
                for (var p = 0; p < planes; p++) {
                    var a = (float) (Math.PI * p / planes);
                    float cp1 = Mth.cos(a + rot1), sp1 = Mth.sin(a + rot1), cp2 = Mth.cos(a + rot2), sp2 = Mth.sin(a + rot2);
                    addQuad(m, c, ox1 - cp1*r1, oy1 - sp1*r1, t1*len, ox1 + cp1*r1, oy1 + sp1*r1, t1*len, ox2 + cp2*r2, oy2 + sp2*r2, t2*len, ox2 - cp2*r2, oy2 - sp2*r2, t2*len, col1, col2);
                }
            }
        }

        static void drawSteamBandLit(Matrix4f m, VertexConsumer c, HellFlareRayRenderState s, RenderParams p) {
            drawSteamBandInternal(m, c, s, p, true);
        }

        static void drawSteamBandBloom(Matrix4f m, VertexConsumer c, HellFlareRayRenderState s, RenderParams p) {
            drawSteamBandInternal(m, c, s, p, false);
        }

        private static void drawSteamBandInternal(Matrix4f m, VertexConsumer c, HellFlareRayRenderState s, RenderParams p, boolean lit) {
            var ux = 1.0f;
            var uy = 0.0f;
            var vx = 0.0f;
            var vy = 1.0f;
            var segs = Math.max(24, (int) (s.length * 8.0f));
            var t12 = phaseBlend12(s);
            var t23 = phaseBlend23(s);
            var scale12 = Mth.lerp(t12, C.P1_STEAM_SCALE, C.P2_STEAM_SCALE);
            var alpha12 = Mth.lerp(t12, C.P1_STEAM_ALPHA, C.P2_STEAM_ALPHA);
            var steamScale = Mth.lerp(t23, scale12, C.P3_STEAM_SCALE);
            var steamAlpha = Mth.lerp(t23, alpha12, C.P3_STEAM_ALPHA);
            var baseR = Math.max(1.2f, p.glowR * 4.6f) * steamScale;
            var time = s.age;
            var layerBase12 = Mth.lerp(t12, lit ? 2.0f : 2.4f, lit ? 3.0f : 3.6f);
            var layers = Math.max(2, Math.round(Mth.lerp(t23, layerBase12, lit ? 4.0f : 5.0f)));
            var flowBase12 = Mth.lerp(t12, lit ? 0.010f : 0.016f, lit ? 0.018f : 0.028f);
            var swirlBase12 = Mth.lerp(t12, lit ? 0.028f : 0.048f, lit ? 0.048f : 0.078f);
            var flowBase = Mth.lerp(t23, flowBase12, lit ? 0.026f : 0.038f);
            var swirlBase = Mth.lerp(t23, swirlBase12, lit ? 0.076f : 0.108f);
            for (var layer = 0; layer < layers; layer++) {
                var layerT = (layer + 1.0f) / layers;
                var layerPhase = layer * 1.43f + (lit ? 0.0f : 0.52f);
                var layerRadius = baseR * (0.64f + layerT * (lit ? 0.58f : 0.75f));
                var flow = flowBase * (1.0f + layer * 0.18f);
                var swirlSpeed = swirlBase * (1.0f + layer * 0.16f);
                var spiral = Mth.lerp(t23, 3.2f + layer * 1.0f, 5.6f + layer * 1.65f);
                var jitterAmp = layerRadius * (lit ? 0.16f : 0.23f);
                var alphaMain = lit ? (228 - layer * 28) : (206 - layer * 18);
                alphaMain = (int) Mth.clamp(alphaMain * steamAlpha, 26, 255);
                var alphaCross = (int) (alphaMain * (lit ? 0.78f : 0.9f));
                if (alphaCross < 26) alphaCross = 26;
                var p1Main = color(alphaMain, 255, 108, 96);
                var p2Main = color(alphaMain, 255, 215, 132);
                var p3Main = color(alphaMain, 225, 244, 255);
                var p1Cross = color(alphaCross, 130, 24, 30);
                var p2Cross = color(alphaCross, 255, 170, 84);
                var p3Cross = color(alphaCross, 118, 186, 255);
                var midMain = lerp(p1Main, p2Main, t12);
                var midCross = lerp(p1Cross, p2Cross, t12);
                var colMain = lerp(midMain, p3Main, t23);
                var colCross = lerp(midCross, p3Cross, t23);
                var uStart = 0.06f + ((layer * 0.11f) % 0.28f);
                var uEnd = Math.min(0.98f, uStart + (lit ? 0.78f : 0.86f));
                var vOff = -time * flow + layer * 0.37f;
                for (var i = 0; i < segs; i++) {
                    var t1 = (float) i / segs;
                    var t2 = (float) (i + 1) / segs;
                    var env1 = (float) Math.pow(getEnv(t1, s.age, s.phase), lit ? 0.78f : 0.66f);
                    var env2 = (float) Math.pow(getEnv(t2, s.age, s.phase), lit ? 0.78f : 0.66f);
                    var pulse1 = 0.74f + 0.26f * Mth.sin(t1 * 11.0f - time * (0.11f + layer * 0.013f) + layerPhase);
                    var pulse2 = 0.74f + 0.26f * Mth.sin(t2 * 11.0f - time * (0.11f + layer * 0.013f) + layerPhase);
                    var prominence1 = prominence(t1, time, layerPhase);
                    var prominence2 = prominence(t2, time, layerPhase);
                    var r1 = layerRadius * env1 * pulse1 * (1.0f + prominence1 * (0.30f + 0.24f * layerT));
                    var r2 = layerRadius * env2 * pulse2 * (1.0f + prominence2 * (0.30f + 0.24f * layerT));
                    var a1 = t1 * spiral - time * swirlSpeed + layerPhase;
                    var a2 = t2 * spiral - time * swirlSpeed + layerPhase;
                    var cos1 = Mth.cos(a1);
                    var sin1 = Mth.sin(a1);
                    var cos2 = Mth.cos(a2);
                    var sin2 = Mth.sin(a2);
                    var ax1x = ux * cos1 + vx * sin1;
                    var ax1y = uy * cos1 + vy * sin1;
                    var bx1x = -ux * sin1 + vx * cos1;
                    var bx1y = -uy * sin1 + vy * cos1;
                    var ax2x = ux * cos2 + vx * sin2;
                    var ax2y = uy * cos2 + vy * sin2;
                    var bx2x = -ux * sin2 + vx * cos2;
                    var bx2y = -uy * sin2 + vy * cos2;
                    var d1a = (noise1(t1 * 23.0f + layerPhase * 5.1f - time * 0.17f) - 0.5f) * 2.0f * jitterAmp * env1;
                    var d1b = (noise1(t1 * 19.0f + layerPhase * 8.3f + time * 0.13f) - 0.5f) * 2.0f * jitterAmp * env1;
                    var d2a = (noise1(t2 * 23.0f + layerPhase * 5.1f - time * 0.17f) - 0.5f) * 2.0f * jitterAmp * env2;
                    var d2b = (noise1(t2 * 19.0f + layerPhase * 8.3f + time * 0.13f) - 0.5f) * 2.0f * jitterAmp * env2;
                    var cx1 = bx1x * d1a + ax1x * d1b * 0.35f;
                    var cy1 = bx1y * d1a + ax1y * d1b * 0.35f;
                    var cx2 = bx2x * d2a + ax2x * d2b * 0.35f;
                    var cy2 = bx2y * d2a + ax2y * d2b * 0.35f;
                    var pAng1 = t1 * 12.0f - time * 0.024f + layerPhase * 1.7f;
                    var pAng2 = t2 * 12.0f - time * 0.024f + layerPhase * 1.7f;
                    var ex1x = ax1x * Mth.cos(pAng1) + bx1x * Mth.sin(pAng1);
                    var ex1y = ax1y * Mth.cos(pAng1) + bx1y * Mth.sin(pAng1);
                    var ex2x = ax2x * Mth.cos(pAng2) + bx2x * Mth.sin(pAng2);
                    var ex2y = ax2y * Mth.cos(pAng2) + bx2y * Mth.sin(pAng2);
                    var burst1 = prominence1 * layerRadius * (0.24f + 0.18f * layerT);
                    var burst2 = prominence2 * layerRadius * (0.24f + 0.18f * layerT);
                    cx1 += ex1x * burst1;
                    cy1 += ex1y * burst1;
                    cx2 += ex2x * burst2;
                    cy2 += ex2y * burst2;
                    var sunspot1 = sunspot(t1, time, layerPhase);
                    var sunspot2 = sunspot(t2, time, layerPhase);
                    var spotAvg = (sunspot1 + sunspot2) * 0.5f;
                    var flareAvg = (prominence1 + prominence2) * 0.5f;
                    var segMainAlpha = (int) Mth.clamp(alphaMain * (1.0f - spotAvg * 0.15f + flareAvg * 0.11f), 18.0f, 255.0f);
                    var segCrossAlpha = (int) Mth.clamp(alphaCross * (1.0f - spotAvg * 0.13f + flareAvg * 0.10f), 18.0f, 255.0f);
                    var segMain = (colMain & 0x00FFFFFF) | (segMainAlpha << 24);
                    var segCross = (colCross & 0x00FFFFFF) | (segCrossAlpha << 24);
                    var z1 = t1 * s.length;
                    var z2 = t2 * s.length;
                    var v1 = t1 * (2.2f + layer * 0.75f) + vOff;
                    var v2 = t2 * (2.2f + layer * 0.75f) + vOff;
                    if (lit) {
                        addTexturedQuadLit(m, c, cx1 - ax1x * r1, cy1 - ax1y * r1, z1, cx1 + ax1x * r1, cy1 + ax1y * r1, z1, cx2 + ax2x * r2, cy2 + ax2y * r2, z2, cx2 - ax2x * r2, cy2 - ax2y * r2, z2, uStart, v1, uEnd, v1, uEnd, v2, uStart, v2, segMain);
                        addTexturedQuadLit(m, c, cx1 - bx1x * r1 * 0.72f, cy1 - bx1y * r1 * 0.72f, z1, cx1 + bx1x * r1 * 0.72f, cy1 + bx1y * r1 * 0.72f, z1, cx2 + bx2x * r2 * 0.72f, cy2 + bx2y * r2 * 0.72f, z2, cx2 - bx2x * r2 * 0.72f, cy2 - bx2y * r2 * 0.72f, z2, uStart, v1 + 0.18f, uEnd, v1 + 0.18f, uEnd, v2 + 0.18f, uStart, v2 + 0.18f, segCross);
                    } else {
                        addTexturedQuad(m, c, cx1 - ax1x * r1, cy1 - ax1y * r1, z1, cx1 + ax1x * r1, cy1 + ax1y * r1, z1, cx2 + ax2x * r2, cy2 + ax2y * r2, z2, cx2 - ax2x * r2, cy2 - ax2y * r2, z2, uStart, v1, uEnd, v1, uEnd, v2, uStart, v2, segMain);
                        addTexturedQuad(m, c, cx1 - bx1x * r1 * 0.84f, cy1 - bx1y * r1 * 0.84f, z1, cx1 + bx1x * r1 * 0.84f, cy1 + bx1y * r1 * 0.84f, z1, cx2 + bx2x * r2 * 0.84f, cy2 + bx2y * r2 * 0.84f, z2, cx2 - bx2x * r2 * 0.84f, cy2 - bx2y * r2 * 0.84f, z2, uStart, v1 + 0.18f, uEnd, v1 + 0.18f, uEnd, v2 + 0.18f, uStart, v2 + 0.18f, segCross);
                    }

                    var cme1 = cmePulse(t1, time, layerPhase, t23);
                    var cme2 = cmePulse(t2, time, layerPhase, t23);
                    if (cme1 > 0.04f || cme2 > 0.04f) {
                        var eject1 = layerRadius * (0.28f + 0.56f * layerT) * cme1;
                        var eject2 = layerRadius * (0.28f + 0.56f * layerT) * cme2;
                        var dir1x = ax1x * 0.35f + bx1x * 0.94f;
                        var dir1y = ax1y * 0.35f + bx1y * 0.94f;
                        var dir2x = ax2x * 0.35f + bx2x * 0.94f;
                        var dir2y = ax2y * 0.35f + bx2y * 0.94f;
                        var n1 = Mth.sqrt(dir1x * dir1x + dir1y * dir1y);
                        var n2 = Mth.sqrt(dir2x * dir2x + dir2y * dir2y);
                        if (n1 > 1.0e-4f) {
                            dir1x /= n1;
                            dir1y /= n1;
                        }
                        if (n2 > 1.0e-4f) {
                            dir2x /= n2;
                            dir2y /= n2;
                        }
                        var base1x = cx1 + ax1x * r1 * 0.24f;
                        var base1y = cy1 + ax1y * r1 * 0.24f;
                        var base2x = cx2 + ax2x * r2 * 0.24f;
                        var base2y = cy2 + ax2y * r2 * 0.24f;
                        var tip1x = base1x + dir1x * eject1;
                        var tip1y = base1y + dir1y * eject1;
                        var tip2x = base2x + dir2x * eject2;
                        var tip2y = base2y + dir2y * eject2;
                        var wing1x = base1x + bx1x * r1 * (0.30f + 0.16f * cme1);
                        var wing1y = base1y + bx1y * r1 * (0.30f + 0.16f * cme1);
                        var wing2x = base2x + bx2x * r2 * (0.30f + 0.16f * cme2);
                        var wing2y = base2y + bx2y * r2 * (0.30f + 0.16f * cme2);
                        var cmeA = (int) Mth.clamp(alphaMain * (0.35f + 0.65f * Math.max(cme1, cme2)), 20.0f, 255.0f);
                        var cmeTint = lerp(colMain, color(255, 212, 236, 255), 0.20f + 0.52f * t23);
                        var cmeCol = (cmeTint & 0x00FFFFFF) | (cmeA << 24);
                        var cu1 = 0.12f;
                        var cu2 = 0.88f;
                        var cv1 = v1 + 0.47f;
                        var cv2 = v2 + 0.47f;
                        if (lit) {
                            addTexturedQuadLit(m, c, base1x, base1y, z1, wing1x, wing1y, z1, tip2x, tip2y, z2, tip1x, tip1y, z1, cu1, cv1, cu2, cv1, cu2, cv2, cu1, cv2, cmeCol);
                            addTexturedQuadLit(m, c, wing1x, wing1y, z1, base1x, base1y, z1, tip1x, tip1y, z1, tip2x, tip2y, z2, cu1, cv1, cu2, cv1, cu2, cv2, cu1, cv2, cmeCol);
                        } else {
                            addTexturedQuad(m, c, base1x, base1y, z1, wing1x, wing1y, z1, tip2x, tip2y, z2, tip1x, tip1y, z1, cu1, cv1, cu2, cv1, cu2, cv2, cu1, cv2, cmeCol);
                            addTexturedQuad(m, c, wing1x, wing1y, z1, base1x, base1y, z1, tip1x, tip1y, z1, tip2x, tip2y, z2, cu1, cv1, cu2, cv1, cu2, cv2, cu1, cv2, cmeCol);
                        }
                    }
                }
            }
        }

        static float wave(float x) { return Mth.sin(x)*0.5f + Mth.sin(x*2.3f)*0.3f + Mth.sin(x*4.7f)*0.2f; }
        static float jitter(float t, float a, int s, float amp) { return (1-t) * amp * (Mth.sin(t*80+a*60+s)*0.5f + Mth.cos(t*150-a*40)*0.5f); }
        static int calcColor(int s, int e, float t, float w, boolean step, float age, int phase, boolean ov) {
            var bc = lerp(s, e, step ? Mth.clamp(t * 3.33f, 0, 1) : t);
            var spot = sunspot(t, age * 0.55f, 3.1f);
            var flare = prominence(t, age * 0.75f, 1.7f);
            var t23 = phaseBlend23(age, phase);
            var spotWeight = Mth.lerp(t23, 0.09f, 0.14f);
            var flareWeight = Mth.lerp(t23, 0.08f, 0.12f);
            var tone = 1.0f - spot * spotWeight + flare * flareWeight;
            bc = scaleRgb(bc, tone);
            if (!ov) return bc;
            var t12 = phaseBlend12(age, phase);
            var ovMix = Mth.lerp(t23, t12, 1.0f);
            return lerp(bc, 0xFFFFFFFF, Mth.clamp(w, 0, 1) * 0.85f * ovMix);
        }
        static float prominence(float t, float age, float phase) {
            var wave = Mth.sin(t * 16.0f - age * 0.12f + phase * 2.1f) * 0.5f + 0.5f;
            var noise = noise1(t * 29.0f + age * 0.09f + phase * 11.0f);
            var gate = Mth.clamp((wave * 0.7f + noise * 0.3f - 0.62f) * 2.8f, 0.0f, 1.0f);
            return gate * gate;
        }
        static float sunspot(float t, float age, float phase) {
            var spot = noise1(t * 35.0f + age * 0.11f + phase * 4.7f);
            return Mth.clamp((0.58f - spot) * 1.9f, 0.0f, 1.0f);
        }
        static float cmePulse(float t, float age, float phase, float blueBlend) {
            var wave = Mth.sin(t * 41.0f - age * 0.24f + phase * 3.7f) * 0.5f + 0.5f;
            var noise = noise1(t * 57.0f - age * 0.18f + phase * 9.3f);
            var threshold = 0.86f - blueBlend * 0.08f;
            var gate = Mth.clamp((wave * 0.62f + noise * 0.38f - threshold) * 7.4f, 0.0f, 1.0f);
            return gate * gate;
        }
        static int lerp(int c1, int c2, float t) {
            int a = (int)Mth.lerp(t, (c1>>24)&0xFF, (c2>>24)&0xFF), r = (int)Mth.lerp(t, (c1>>16)&0xFF, (c2>>16)&0xFF);
            int g = (int)Mth.lerp(t, (c1>>8)&0xFF, (c2>>8)&0xFF), b = (int)Mth.lerp(t, c1&0xFF, c2&0xFF);
            return (a<<24)|(r<<16)|(g<<8)|b;
        }
        static int scaleRgb(int c, float factor) {
            var a = (c >>> 24) & 0xFF;
            var r = (int) Mth.clamp(((c >>> 16) & 0xFF) * factor, 0.0f, 255.0f);
            var g = (int) Mth.clamp(((c >>> 8) & 0xFF) * factor, 0.0f, 255.0f);
            var b = (int) Mth.clamp((c & 0xFF) * factor, 0.0f, 255.0f);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        static int color(int a, int r, int g, int b) {
            return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }
        static void addQuad(Matrix4f m, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int c1, int c2, int c3, int c4) {
            c.addVertex(m, x1, y1, z1).setColor(c1); c.addVertex(m, x2, y2, z2).setColor(c2); c.addVertex(m, x3, y3, z3).setColor(c3); c.addVertex(m, x4, y4, z4).setColor(c4);
        }
        static void addQuad(Matrix4f m, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int c1, int c2) {
            c.addVertex(m, x1, y1, z1).setColor(c1); c.addVertex(m, x2, y2, z2).setColor(c1); c.addVertex(m, x3, y3, z3).setColor(c2); c.addVertex(m, x4, y4, z4).setColor(c2);
        }
        static void addTexturedQuad(Matrix4f m, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int col) {
            c.addVertex(m, x1, y1, z1).setUv(u1, v1).setColor(col);
            c.addVertex(m, x2, y2, z2).setUv(u2, v2).setColor(col);
            c.addVertex(m, x3, y3, z3).setUv(u3, v3).setColor(col);
            c.addVertex(m, x4, y4, z4).setUv(u4, v4).setColor(col);
            c.addVertex(m, x4, y4, z4).setUv(u4, v4).setColor(col);
            c.addVertex(m, x3, y3, z3).setUv(u3, v3).setColor(col);
            c.addVertex(m, x2, y2, z2).setUv(u2, v2).setColor(col);
            c.addVertex(m, x1, y1, z1).setUv(u1, v1).setColor(col);
        }
        static void addTexturedQuadLit(Matrix4f m, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int col) {
            c.addVertex(m, x1, y1, z1).setUv(u1, v1).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 1, 0);
            c.addVertex(m, x2, y2, z2).setUv(u2, v2).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 1, 0);
            c.addVertex(m, x3, y3, z3).setUv(u3, v3).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 1, 0);
            c.addVertex(m, x4, y4, z4).setUv(u4, v4).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 1, 0);
            c.addVertex(m, x4, y4, z4).setUv(u4, v4).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, -1, 0);
            c.addVertex(m, x3, y3, z3).setUv(u3, v3).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, -1, 0);
            c.addVertex(m, x2, y2, z2).setUv(u2, v2).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, -1, 0);
            c.addVertex(m, x1, y1, z1).setUv(u1, v1).setColor(col).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, -1, 0);
        }
    }
}

