package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.EffectEnvironmentSample;
import org.academy.api.client.render.vfxgraph.runtime.EffectParameterCache;
import org.academy.api.client.render.vfxgraph.runtime.StableEffectAdmission;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.HumanoidSurfacePose;
import org.academy.api.client.render.vfxgraph.shape.SurfaceDetailSelector;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.academy.api.common.vfx.RepairVisualParts;
import org.academy.internal.common.network.DarkmatterVisualPacket;
import org.academy.internal.common.network.DarkmatterVisualPacket.Kind;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/** One bounded graph per actor/target and kind, with explicit stop, leases and world identity checks. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterVfxClient {
    private record Key(Kind kind, int entity, BlockPos block, long pulse) { }
    private record Pending(DarkmatterVisualPacket packet, long expires) { }
    private static final int MAX_EFFECTS = 64;
    private static final int MAX_STATES = 512;
    private static final StableEffectAdmission<Key> ADMISSION = new StableEffectAdmission<>(MAX_EFFECTS, 2, .85);
    private static final Map<Key, Visual> EFFECTS = new LinkedHashMap<>();
    private static final Map<Key, Pending> PENDING = new LinkedHashMap<>();
    private static ClientLevel world;
    private DarkmatterVfxClient() { }

    public static void accept(DarkmatterVisualPacket packet) {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(() -> accept(packet)); return; }
        if (packet.kind == Kind.INTERFERENCE) { InterferenceFieldClient.accept(packet); return; }
        resetWorld();
        if (world == null || !world.dimension().identifier().equals(packet.dimension)) return;
        if (packet.kind == Kind.WING_STRIKE) DarkmatterSixWingsVfxClient.strike(packet.entityId);
        var key = new Key(packet.kind, packet.entityId, packet.entityId < 0 ? BlockPos.containing(packet.position) : BlockPos.ZERO,
                packet.kind == Kind.CUT || packet.kind == Kind.SHAPING || packet.kind == Kind.WING_STRIKE ? packet.seed : 0);
        if (!packet.active) { remove(key); return; }
        var subject = packet.entityId < 0 ? null : world.getEntity(packet.entityId);
        if (packet.entityId >= 0 && !(subject instanceof LivingEntity)) {
            if (packet.kind == Kind.SUMMON && PENDING.size() < MAX_EFFECTS)
                PENDING.putIfAbsent(key, new Pending(packet, world.getGameTime() + 8));
            return;
        }
        PENDING.remove(key);
        var current = EFFECTS.get(key);
        if (current != null && (current.subject != subject || current.effect != null && current.effect.isStopped())) { remove(key); current = null; }
        if (current == null) {
            if (EFFECTS.size() >= MAX_STATES) {
                var victim = EFFECTS.entrySet().stream().filter(e -> e.getValue().effect == null)
                        .min(java.util.Comparator.comparingInt(e -> e.getValue().priority())).orElse(null);
                if (victim == null) return;
                remove(victim.getKey());
            }
            try {
                current = new Visual(packet, (LivingEntity) subject);
                EFFECTS.put(key, current);
            } catch (RuntimeException failure) {
                AcademyCraft.getLogger().error("Unable to create darkmatter surface effect", failure);
                return;
            }
        }
        current.refresh(packet);
    }

    /** Diagnostics used by the isolated client capture, including continuous-pulse deduplication. */
    public static int activeCount() {
        int active = InterferenceFieldClient.activeCount();
        for (var visual : EFFECTS.values()) if (visual.effect != null) active++;
        return active;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        InterferenceFieldClient.tick();
        resetWorld();
        if (world != null) {
            var pending = PENDING.values().iterator();
            while (pending.hasNext()) {
                var entry = pending.next();
                if (world.getGameTime() > entry.expires) pending.remove();
                else if (world.getEntity(entry.packet.entityId) instanceof LivingEntity) { pending.remove(); accept(entry.packet); }
            }
        }
        var iterator = EFFECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var visual = entry.getValue();
            if (!visual.valid()) { visual.deactivate(); ADMISSION.remove(entry.getKey()); iterator.remove(); }
            else ADMISSION.offer(entry.getKey(), visual.priority(), visual.distanceSquared());
        }
        var selected = ADMISSION.reconcile(world == null ? 0 : world.getGameTime());
        for (var entry : EFFECTS.entrySet()) {
            if (selected.contains(entry.getKey())) entry.getValue().activate();
            else entry.getValue().deactivate();
        }
    }

    private static void resetWorld() {
        var next = Minecraft.getInstance().level;
        if (next == world) return;
        EFFECTS.values().forEach(Visual::deactivate);
        EFFECTS.clear();
        ADMISSION.clear();
        PENDING.clear();
        world = next;
    }
    private static void remove(Key key) { PENDING.remove(key); var previous = EFFECTS.remove(key); ADMISSION.remove(key); if (previous != null) previous.deactivate(); }

    private static final class Visual {
        static final int[] FACE_ORDER = {2, 3, 4, 5, 0, 1};
        static final String[] REPAIR_PROGRESS_KEYS = {"repair_progress_0", "repair_progress_1", "repair_progress_2",
                "repair_progress_3", "repair_progress_4", "repair_progress_5"};
        static final float[][] BODY_MIN = {{-4, 0, -2}, {-4, -8, -4}, {-3, -2, -2}, {-1, -2, -2}, {-2, 0, -2}, {-2, 0, -2}};
        static final float[][] BODY_MAX = {{4, 12, 2}, {4, 0, 4}, {1, 10, 2}, {3, 10, 2}, {2, 12, 2}, {2, 12, 2}};
        final LivingEntity subject;
        ActiveEffect effect;
        final EffectParameterCache params = new EffectParameterCache();
        final EffectEnvironmentSample environment = new EffectEnvironmentSample();
        final Vector3f position = new Vector3f(), viewPosition = new Vector3f(), viewDirection = new Vector3f(), sunlight = new Vector3f();
        final Quaternionf rotation = new Quaternionf(), inverse = new Quaternionf();
        final Matrix4f viewInverse = new Matrix4f(), formationTransform = new Matrix4f(), identity = new Matrix4f();
        final Matrix4f[] posed = new Matrix4f[6], handTransforms = {new Matrix4f(), new Matrix4f()};
        final Vector3f boxMin = new Vector3f(), boxMax = new Vector3f(), formationMin = new Vector3f(), formationMax = new Vector3f();
        final Vector3f[] bodyMin = new Vector3f[6], bodyMax = new Vector3f[6];
        final Vector3f handMin = new Vector3f(-.05f, -.05f, -.02f), handMax = new Vector3f(.05f, .05f, .02f);
        final int[] bodyChoices = new int[6], exposedFaces = new int[6];
        int bodyChoiceCount, exposedCount, frameParts;
        boolean constantsDirty = true;
        float density, ageOffset, retainedAge;
        long suspendedTick;
        final ClientLevel level;
        final long[] partTicks = new long[7];
        final RepairVisualTimeline repairTimeline = new RepairVisualTimeline();
        final SurfaceDetailSelector reconstructionDetail = new SurfaceDetailSelector();
        final Matrix4f surfaceInverse = new Matrix4f();
        final Matrix4f cowSurfaceTransform = new Matrix4f();
        final Vector3f cowSurfaceMin = new Vector3f(), cowSurfaceMax = new Vector3f();
        final Vector3f contactPoint = new Vector3f(), surfaceNormal = new Vector3f();
        final float[] repairProgress = new float[RepairVisualTimeline.PATCH_COUNT];
        final float[] bodyPadding = new float[RepairVisualTimeline.PATCH_COUNT];
        int equippedArmorParts;
        double frameTick;
        boolean contactBound, contactPosed;
        boolean cowSurfaceAvailable, contactCowSurface;
        boolean contactFeetRegion;
        int contactPart, contactFace;
        float contactU = .5f, contactV = .5f;
        DarkmatterVisualPacket state;
        long updated;
        Vec3 origin;
        boolean firstPerson, posedAvailable;
        Matrix4f[] bones;
        Matrix4f modelRoot;
        float progress;

        Visual(DarkmatterVisualPacket packet, LivingEntity subject) {
            this.subject = subject;
            level = world;
            state = packet;
            origin = packet.position;
            suspendedTick = level.getGameTime();
            modelRoot = new Matrix4f();
            for (int i = 0; i < posed.length; i++) { posed[i] = new Matrix4f(); bodyMin[i] = new Vector3f(); bodyMax[i] = new Vector3f(); }
            java.util.Arrays.fill(partTicks, Long.MIN_VALUE / 2);
        }

        void activate() {
            if (effect != null) return;
            effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/" + state.kind.graph), origin.toVector3f());
            ageOffset = retainedAge + (level.getGameTime() - suspendedTick) / 20f;
            effect.setRenderDistance(96);
            effect.bindSurfaceSampler("surface", this::sampleSubject);
            effect.bindFrame(this::frame);
        }
        void deactivate() {
            if (effect == null) return;
            retainedAge = ageOffset + effect.gameAgeSeconds(); suspendedTick = level.getGameTime();
            effect.stop(); effect = null;
        }
        int priority() {
            var mc = Minecraft.getInstance();
            if (subject != null && (subject == mc.player || subject == mc.getCameraEntity())) return 4;
            return switch (state.kind) {
                case CONTACT, CREATURE_CONTACT, SUMMON, DISMISS, DISASSEMBLE -> 3;
                case SHAPING, CUT, WING_STRIKE -> 2;
                default -> 1;
            };
        }
        double distanceSquared() {
            var viewer = Minecraft.getInstance().getCameraEntity();
            return viewer == null ? 0 : viewer.position().distanceToSqr(subject == null ? state.position : subject.position());
        }

        void refresh(DarkmatterVisualPacket packet) {
            constantsDirty |= state.width != packet.width || state.height != packet.height || state.range != packet.range
                    || state.alpha != packet.alpha || state.beta != packet.beta || state.gamma != packet.gamma
                    || state.variant != packet.variant || state.seed != packet.seed || state.finished != packet.finished;
            // Each successful hit owns a fresh contact; its bone-local anchor stays fixed between packets.
            if (packet.kind == Kind.DISASSEMBLE || !java.util.Objects.equals(state.surfaceContact, packet.surfaceContact)) contactBound = false;
            state = packet;
            density = packet.alpha + packet.beta > 0 ? packet.alpha / (packet.alpha + packet.beta) : .5f;
            updated = level.getGameTime();
            for (int i = 0; i < partTicks.length; i++) if ((packet.parts & (1 << i)) != 0) partTicks[i] = updated;
            if (packet.kind == Kind.REPAIR) repairTimeline.pulse(RepairVisualTimeline.patchMask(packet.parts,
                    subject == null || subject.getMainArm() == HumanoidArm.RIGHT), updated);
        }

        boolean valid() {
            if (world != level || effect != null && effect.isStopped() || level.getGameTime() - updated > state.kind.leaseTicks) return false;
            if (subject == null) return true;
            if (subject.level() != world) return false;
            var present = world.getEntity(subject.getId());
            if (present != subject && !(present == null && state.finished)) return false;
            // Terminal erosion may finish on the death pose; maintained fields and repair stop immediately.
            return (!subject.isRemoved() && subject.isAlive()) || state.finished && (state.kind == Kind.DISASSEMBLE || state.kind == Kind.CONTACT || state.kind == Kind.CREATURE_CONTACT);
        }

        boolean frame(ActiveEffect active, GraphCamera camera, float partialTick) {
            if (!valid()) return false;
            var mc = Minecraft.getInstance();
            firstPerson = subject == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            if (subject != null && !subject.isRemoved()) origin = subject.getPosition(partialTick);
            position.set((float) origin.x, (float) origin.y, (float) origin.z);
            active.setPosition(position);
            if (state.kind == Kind.CUT || state.kind == Kind.WING_STRIKE)
                rotation.rotationTo(0, 0, 1, (float) state.direction.x, (float) state.direction.y, (float) state.direction.z);
            else rotation.identity();
            active.setRotation(rotation);
            inverse.set(rotation).conjugate();
            viewPosition.set(camera.position()).sub(position);
            inverse.transform(viewPosition);
            viewInverse.set(camera.viewRotation()).invert();
            viewInverse.transformDirection(viewDirection.set(0, 0, -1));
            inverse.transform(viewDirection).normalize();
            bones = subject == null ? null : HumanoidSurfacePose.get(subject.getId());
            var root = subject == null ? null : WingAvatarRegistry.entries().get(subject.getId());
            posedAvailable = subject instanceof Player && bones != null && root != null;
            if (posedAvailable) {
                modelRoot.set(root);
                modelRoot.m30(modelRoot.m30() + camera.position().x - (float) origin.x);
                modelRoot.m31(modelRoot.m31() + camera.position().y - (float) origin.y);
                modelRoot.m32(modelRoot.m32() + camera.position().z - (float) origin.z);
                for (int i = 0; i < posed.length; i++) posed[i].set(modelRoot).mul(bones[i]);
            }
            float elapsed = (level.getGameTime() - updated + partialTick) / 20f;
            frameTick = level.getGameTime() + partialTick;
            float age = ageOffset + active.gameAgeSeconds();
            boolean maintained = state.kind == Kind.GENERATION || state.kind == Kind.ELIMINATION || state.kind == Kind.PHASE_TUNING;
            progress = maintained ? age % .7f / .7f
                    : Math.clamp(elapsed / (state.kind == Kind.CONTACT || state.kind == Kind.CREATURE_CONTACT ? .18f
                    : state.kind == Kind.REPAIR ? .38f : state.kind == Kind.CUT || state.kind == Kind.WING_STRIKE ? .32f : .65f), 0, 1);
            if (state.kind == Kind.REPAIR) {
                progress = 1;
                for (int i = 0; i < repairProgress.length; i++) {
                    repairProgress[i] = repairTimeline.progress(i, frameTick);
                    if (repairTimeline.active(i, frameTick)) progress = Math.min(progress, repairProgress[i]);
                }
            }
            prepareSurfaces(camera, partialTick);
            environment.update(level, origin.x, origin.y + state.height * .6, origin.z, subject == null ? (int) state.seed : subject.getId());
            var graph = active.effect();
            if (params.begin(graph) || constantsDirty) {
                params.putFloat("height", state.kind == Kind.CUT ? 0 : state.kind == Kind.WING_STRIKE ? state.height * .5f : state.height);
                params.putFloat("range", state.range);
                params.putFloat("seed", (float) (state.seed & 65535));
                params.putFloat("finished", state.finished ? 1 : 0);
                params.putFloat("alpha", state.alpha); params.putFloat("beta", state.beta); params.putFloat("gamma", state.gamma);
                params.putFloat("density", density); params.putFloat("flexibility", 1 - density);
                params.putFloat("edge_activity", .15f + (1 - density) * .7f);
                params.putFloat("shape", state.variant);
                params.putFloat("radius", state.kind == Kind.CUT ? state.range * (.88f + density * .18f) : state.kind == Kind.WING_STRIKE ? state.range : 1);
                params.putFloat("reverse", state.variant < 0 ? 1 : 0);
                params.putFloat("width", state.kind == Kind.CUT ? state.range * (.38f + density * .12f)
                        : state.kind == Kind.WING_STRIKE ? state.range * .42f : state.width);
                params.putFloat("surface_patches", state.kind == Kind.REPAIR || subject == null ? 6 : 1);
                constantsDirty = false;
            }
            params.putFloat("time", age); params.putFloat("progress", progress);
            if (state.kind == Kind.REPAIR) for (int i = 0; i < repairProgress.length; i++)
                params.putFloat(REPAIR_PROGRESS_KEYS[i], repairProgress[i]);
            if (state.kind == Kind.REPAIR || state.kind == Kind.DISASSEMBLE) {
                float featureLength = Math.max(.05f, Math.min(state.width * .64f, state.height * .30f));
                float completeRadius = Math.max(state.width, state.height) + .4f;
                int detail = reconstructionDetail.update(featureLength, completeRadius,
                        viewPosition.length(), camera.projection());
                params.putInt("surface_detail", detail);
                params.putFloat("lod", switch (detail) {
                    case SurfaceDetailSelector.MEDIUM -> .65f;
                    case SurfaceDetailSelector.FAR -> .35f;
                    default -> 1;
                });
            }
            params.putFloat("sheet_scale", firstPerson && state.kind == Kind.REPAIR ? .45f : 1);
            params.putFloat("light", environment.light());
            inverse.transform(sunlight.set(environment.sunlight()));
            params.putVec3("sun_direction", sunlight);
            params.putFloat("strength", state.kind == Kind.WING_STRIKE && firstPerson ? .40f : 1);
            params.putFloat("attack", state.kind == Kind.WING_STRIKE ? (float) Math.sin(progress * Math.PI) : 0);
            params.putFloat("view_first_person", mc.options.getCameraType().isFirstPerson() ? 1 : 0);
            params.putVec3("camera", viewPosition); params.putVec3("camera_forward", viewDirection);
            active.setCullingSphere(position, state.kind == Kind.CUT || state.kind == Kind.WING_STRIKE ? state.range + 2 : Math.max(state.width, state.height) + 1);
            return true;
        }

        /** Per-frame transforms are shared by all surface samples of this effect. */
        void prepareSurfaces(GraphCamera camera, float partialTick) {
            frameParts = parts();
            cowSurfaceAvailable = state.kind == Kind.DISASSEMBLE && subject instanceof Cow;
            if (cowSurfaceAvailable) {
                var cow = (Cow) subject;
                CowReconstructionSurface.prepare(cow.isBaby(),
                        cow.getVariant().value().modelAndTexture().model() == CowVariant.ModelType.COLD,
                        cow.getScale(), Mth.rotLerp(partialTick, cow.yBodyRotO, cow.yBodyRot),
                        cow.deathTime > 0 ? cow.deathTime + partialTick : 0,
                        cowSurfaceMin, cowSurfaceMax, cowSurfaceTransform);
            }
            boolean reconstruction = subject instanceof Player && (state.kind == Kind.REPAIR || state.kind == Kind.DISASSEMBLE);
            equippedArmorParts = 0;
            if (reconstruction) {
                if (HumanoidArmorLayer.shouldRender(subject.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD)) equippedArmorParts |= RepairVisualParts.HEAD;
                if (HumanoidArmorLayer.shouldRender(subject.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)) equippedArmorParts |= RepairVisualParts.CHEST;
                if (HumanoidArmorLayer.shouldRender(subject.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS)) equippedArmorParts |= RepairVisualParts.LEGS;
                if (HumanoidArmorLayer.shouldRender(subject.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET)) equippedArmorParts |= RepairVisualParts.FEET;
            }
            bodyChoiceCount = 0;
            if (state.kind != Kind.REPAIR || (frameParts & (RepairVisualParts.BODY | RepairVisualParts.CHEST)) != 0) bodyChoices[bodyChoiceCount++] = 0;
            if (state.kind != Kind.REPAIR || (frameParts & RepairVisualParts.HEAD) != 0) bodyChoices[bodyChoiceCount++] = 1;
            boolean rightMain = subject == null || subject.getMainArm() == HumanoidArm.RIGHT;
            if (state.kind != Kind.REPAIR || (frameParts & (RepairVisualParts.BODY | (rightMain ? RepairVisualParts.MAIN_HAND : RepairVisualParts.OFF_HAND))) != 0) bodyChoices[bodyChoiceCount++] = 2;
            if (state.kind != Kind.REPAIR || (frameParts & (RepairVisualParts.BODY | (rightMain ? RepairVisualParts.OFF_HAND : RepairVisualParts.MAIN_HAND))) != 0) bodyChoices[bodyChoiceCount++] = 3;
            if (state.kind != Kind.REPAIR || (frameParts & (RepairVisualParts.LEGS | RepairVisualParts.FEET)) != 0) { bodyChoices[bodyChoiceCount++] = 4; bodyChoices[bodyChoiceCount++] = 5; }
            for (int i = 0; i < 6; i++) {
                bodyMin[i].set(BODY_MIN[i][0], BODY_MIN[i][1], BODY_MIN[i][2]);
                bodyMax[i].set(BODY_MAX[i][0], BODY_MAX[i][1], BODY_MAX[i][2]);
                if (i >= 4 && (frameParts & RepairVisualParts.FEET) != 0 && (frameParts & RepairVisualParts.LEGS) == 0) bodyMin[i].y = 8;
                bodyMin[i].div(16); bodyMax[i].div(16);
                boolean feetRegion = state.kind == Kind.REPAIR
                        ? (frameParts & RepairVisualParts.FEET) != 0 && (frameParts & RepairVisualParts.LEGS) == 0
                        : contactBound && contactPosed && contactPart == i && contactFeetRegion;
                bodyPadding[i] = reconstruction ? ReconstructionSurfacePadding.forPart(i, equippedArmorParts, feetRegion) : 0;
                float padding = bodyPadding[i];
                bodyMin[i].sub(padding, padding, padding); bodyMax[i].add(padding, padding, padding);
            }
            exposedCount = 0;
            for (int i = 0; i < 6; i++) if ((state.parts & (1 << i)) != 0) exposedFaces[exposedCount++] = FACE_ORDER[i];
            boxMin.set(-state.width / 2, 0, -state.width / 2); boxMax.set(state.width / 2, state.height, state.width / 2);
            formationMin.set(-.08f); formationMax.set(.08f);
            if (state.kind == Kind.SHAPING) switch (state.variant) {
                case 0 -> { formationMin.set(-.045f, -.22f, -.03f); formationMax.set(.14f, .16f, .03f); }
                case 1, 7 -> { formationMin.set(-.025f, -.25f, -.018f); formationMax.set(.025f, .25f, .018f); }
                case 2, 3 -> { formationMin.set(-.022f, -.34f, -.018f); formationMax.set(.022f, .34f, .018f); }
                case 4, 5 -> { formationMin.set(-.14f, -.20f, -.022f); formationMax.set(.14f, .20f, .022f); }
                case 6 -> { formationMin.set(-.06f, -.24f, -.06f); formationMax.set(.06f, .20f, .06f); }
                case 8 -> { formationMin.set(-.17f, -.20f, -.08f); formationMax.set(.17f, .20f, .08f); }
                case 9 -> { formationMin.set(-.13f, -.13f, -.014f); formationMax.set(.13f, .13f, .014f); }
                default -> { formationMin.set(-.13f); formationMax.set(.13f); }
            } else if (state.kind == Kind.PHASE_TUNING) {
                formationMin.set(-.12f, -.16f, -.025f - density * .05f); formationMax.set(.12f, .16f, .025f + density * .05f);
            }
            if (firstPerson) {
                formationTransform.set(viewInverse).setTranslation(camera.position().x - position.x, camera.position().y - position.y, camera.position().z - position.z);
                formationTransform.translate(rightMain ? .30f : -.30f, -.25f, -.67f);
            } else {
                formationTransform.identity().rotateY(subject == null ? 0 : (float) Math.toRadians(-subject.getYRot()));
                formationTransform.translate(rightMain ? -.30f : .30f, state.height * .62f, .42f);
            }
            float handProjectionScale = firstPerson && state.kind == Kind.REPAIR
                    ? FirstPersonRepairSurface.projectionScale(camera.projection()) : 1;
            for (int i = 0; i < handTransforms.length; i++) {
                handTransforms[i].set(viewInverse).setTranslation(camera.position().x - position.x, camera.position().y - position.y, camera.position().z - position.z);
                // Match the normal hand projection without changing world depth or other skills.
                handTransforms[i].scale(handProjectionScale, handProjectionScale, 1);
                handTransforms[i].translate(i == 0 ? .30f : -.30f, -.23f, -.53f);
            }
        }

        int parts() {
            int result = 0;
            for (int i = 0; i < partTicks.length; i++) if (level.getGameTime() - partTicks[i] <= Kind.REPAIR.leaseTicks) result |= 1 << i;
            return result;
        }

        boolean sampleSubject(int index, float u, float v, Vector3f p, Vector3f n) {
            if (state.kind == Kind.GENERATION || state.kind == Kind.ELIMINATION || state.kind == Kind.SHAPING || state.kind == Kind.PHASE_TUNING)
                return sampleFormation(index, u, v, p, n);
            if (state.kind == Kind.REPAIR) return sampleRepair(index, u, v, p, n);
            if (state.kind == Kind.DISASSEMBLE) return sampleDisassembly(index, u, v, p, n);
            if (posedAvailable) {
                if (bodyChoiceCount == 0) return false;
                int part = bodyChoices[index % bodyChoiceCount];
                SurfaceSampler.box(4 + index / bodyChoiceCount, u, v, bodyMin[part], bodyMax[part], posed[part], p, n);
                return true;
            }
            int face = index % 6;
            if (subject == null && state.parts != 0) {
                // Direction ordinals DOWN, UP, NORTH, SOUTH, WEST, EAST -> box face order.
                if (exposedCount == 0) return false;
                face = exposedFaces[index % exposedCount];
            }
            SurfaceSampler.box(face, u, v, boxMin, boxMax, identity, p, n);
            return true;
        }

        /** Fixed patch identities avoid moving old laminae when another equipment site joins. */
        boolean sampleRepair(int index, float u, float v, Vector3f p, Vector3f n) {
            int part = Math.floorMod(index, RepairVisualTimeline.PATCH_COUNT);
            if (!repairTimeline.active(part, frameTick)) return false;
            if (firstPerson) {
                if (part != 2 && part != 3) return false;
                boolean mainPatch = (part == 2) == (subject.getMainArm() == HumanoidArm.RIGHT);
                if (!mainPatch && subject.getOffhandItem().isEmpty()
                        && !(subject.getMainHandItem().getItem() instanceof net.minecraft.world.item.MapItem)) return false;
                SurfaceSampler.box(5, .18f + u * .64f, .18f + v * .64f,
                        handMin, handMax, handTransforms[part == 2 ? 0 : 1], p, n);
                return true;
            }
            if (posedAvailable) {
                // One small, continuous front patch per actual site. Arms always use the forearm.
                float startV = part == 2 || part == 3 ? .36f : .18f;
                float spanV = part == 2 || part == 3 ? .56f : .64f;
                SurfaceSampler.box(4, .18f + u * .64f, startV + v * spanV,
                        bodyMin[part], bodyMax[part], posed[part], p, n);
            } else {
                // A bounded proxy patch is deliberately conservative for non-humanoid actors.
                float centerX = part == 2 ? .24f : part == 3 ? .76f : part == 4 ? .38f : part == 5 ? .62f : .5f;
                boolean feetOnly = part >= 4 && (frameParts & RepairVisualParts.FEET) != 0 && (frameParts & RepairVisualParts.LEGS) == 0;
                float centerY = part == 1 ? .88f : feetOnly ? .075f : part >= 4 ? .2f : part >= 2 ? .55f : .62f;
                SurfaceSampler.box(4, centerX + (u - .5f) * .2f, centerY + (v - .5f) * (feetOnly ? .1f : .16f),
                        boxMin, boxMax, identity, p, n);
            }
            return true;
        }

        /** Every strand of an entity samples the same anchored patch; removed blocks expose separate faces. */
        boolean sampleDisassembly(int index, float u, float v, Vector3f p, Vector3f n) {
            if (subject == null) {
                int face = Math.floorMod(index, 6);
                if (state.parts != 0 && (state.parts & (1 << directionOrdinal(face))) == 0) return false;
                SurfaceSampler.box(face, .12f + u * .76f, .12f + v * .76f, boxMin, boxMax, identity, p, n);
                return true;
            }
            if (!contactBound || contactPosed != posedAvailable || contactCowSurface != cowSurfaceAvailable) bindDisassemblyContact();
            float sampleU = Math.clamp(contactU + (u - .5f) * .64f, .04f, .96f);
            float sampleV = Math.clamp(contactV + (v - .5f) * .64f, .04f, .96f);
            SurfaceSampler.box(contactFace, sampleU, sampleV,
                    posedAvailable ? bodyMin[contactPart] : cowSurfaceAvailable ? cowSurfaceMin : boxMin,
                    posedAvailable ? bodyMax[contactPart] : cowSurfaceAvailable ? cowSurfaceMax : boxMax,
                    posedAvailable ? posed[contactPart] : cowSurfaceAvailable ? cowSurfaceTransform : identity, p, n);
            return true;
        }

        void bindDisassemblyContact() {
            var contact = state.surfaceContact;
            if (contact == null) {
                contactPoint.set(0, state.height * .68f, -state.width * .5f);
                surfaceNormal.set(0, 0, -1);
            } else {
                contactPoint.set((float) contact.localOffset().x, (float) contact.localOffset().y, (float) contact.localOffset().z);
                surfaceNormal.set((float) contact.normal().x, (float) contact.normal().y, (float) contact.normal().z);
            }
            contactPart = 0;
            if (posedAvailable) {
                // The packet describes an AABB hit, not a mesh hit. Client-specific limb swing
                // must not steal a torso contact before the selected bone-local anchor is locked.
                contactPart = HumanoidProxyContact.select(contactPoint, modelRoot, bodyMin, bodyMax);
                surfaceInverse.set(posed[contactPart]).invert();
                surfaceInverse.transformPosition(contactPoint);
                surfaceInverse.transformDirection(surfaceNormal).normalize();
                contactFeetRegion = contactPart >= 4 && contactPoint.y >= .5f;
                if (contactPart >= 4) {
                    float padding = ReconstructionSurfacePadding.forPart(contactPart, equippedArmorParts, contactFeetRegion);
                    float difference = padding - bodyPadding[contactPart];
                    bodyMin[contactPart].sub(difference, difference, difference);
                    bodyMax[contactPart].add(difference, difference, difference);
                    bodyPadding[contactPart] = padding;
                }
            }
            if (cowSurfaceAvailable) {
                surfaceInverse.set(cowSurfaceTransform).invert();
                surfaceInverse.transformPosition(contactPoint);
                surfaceInverse.transformDirection(surfaceNormal).normalize();
            }
            var min = posedAvailable ? bodyMin[contactPart] : cowSurfaceAvailable ? cowSurfaceMin : boxMin;
            var max = posedAvailable ? bodyMax[contactPart] : cowSurfaceAvailable ? cowSurfaceMax : boxMax;
            contactFace = dominantFace(surfaceNormal);
            contactU = contactFace < 2 ? fraction(contactPoint.z, min.z, max.z) : fraction(contactPoint.x, min.x, max.x);
            contactV = contactFace == 2 || contactFace == 3 ? fraction(contactPoint.z, min.z, max.z) : fraction(contactPoint.y, min.y, max.y);
            // Keep the whole differential-sampled patch on the selected face, even for edge impacts.
            contactU = Math.clamp(contactU, .36f, .64f);
            contactV = Math.clamp(contactV, .36f, .64f);
            contactPosed = posedAvailable;
            contactCowSurface = cowSurfaceAvailable;
            contactBound = true;
        }

        static float fraction(float value, float min, float max) { return (value - min) / Math.max(.001f, max - min); }
        static int dominantFace(Vector3f normal) {
            if (Math.abs(normal.x) >= Math.abs(normal.y) && Math.abs(normal.x) >= Math.abs(normal.z)) return normal.x < 0 ? 0 : 1;
            if (Math.abs(normal.y) >= Math.abs(normal.z)) return normal.y < 0 ? 2 : 3;
            return normal.z < 0 ? 4 : 5;
        }
        static int directionOrdinal(int face) { return switch (face) { case 0 -> 4; case 1 -> 5; case 2 -> 0; case 3 -> 1; case 4 -> 2; default -> 3; }; }

        /** Small posed proxy surfaces show the completed shape without creating a second item entity. */
        boolean sampleFormation(int index, float u, float v, Vector3f p, Vector3f n) {
            SurfaceSampler.box(index, u, v, formationMin, formationMax, identity, p, n);
            if (state.kind == Kind.GENERATION || state.kind == Kind.ELIMINATION) {
                float outward = state.kind == Kind.ELIMINATION ? progress : 1 - progress;
                p.fma(outward * .30f, n);
                p.y += (float) Math.sin(index * 2.4f + progress * 2) * outward * .08f;
            } else if (state.kind == Kind.SHAPING) {
                p.mul(.15f + .85f * progress);
                p.fma((1 - progress) * .17f, n);
            } else p.fma((1 - density) * .05f * (float) Math.sin((ageOffset + effect.gameAgeSeconds()) * 5 + index), n);
            formationTransform.transformPosition(p);
            formationTransform.transformDirection(n).normalize();
            return true;
        }

    }
}
