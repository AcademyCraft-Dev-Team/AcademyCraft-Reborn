package org.academy.desktop.grapheditor.preview;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.render.vfx.DarkmatterSixWingsVfxClient;
import org.academy.internal.client.render.vfx.DarkmatterVfxClient;
import org.academy.internal.client.render.vfx.InterferenceFieldClient;
import org.academy.internal.client.renderer.entity.DarkmatterBeetleRenderer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterDisassemble;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterGeneration;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterCut;
import org.academy.internal.common.ability.darkmatter.skills.lv2.DarkmatterPhaseTuning;
import org.academy.internal.common.ability.darkmatter.skills.lv3.DarkmatterRadiation;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterRepair;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.skilldata.DarkmatterCreationData;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Isolated opt-in real-skill capture for the approved nine-skill redesign; excluded from mod jars. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DarkmatterConceptClientSmoke {
    private static final String PREFIX = "[darkmatter-concept-smoke] ";
    private static final Set<String> observed = new HashSet<>();
    private static final Set<ActiveEffect> gammaCuts = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean opened, configured;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static volatile UUID walkingId, flyingId, swimmingId;
    private static volatile Vec3 walkingStart;
    private static DarkmatterBeetle swimmingFixture;
    private static long swimmingSpawnTime;
    private static LivingEntity target;
    private static int ticks, total, warmupTicks;
    private static int originalFov;
    private static volatile float yaw, pitch;
    private static boolean sawWingImpulse, sawTrail, sawCreatureContact;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.darkmatterConceptSmoke")) return;
        var mc = Minecraft.getInstance();
        if (serverFailure != null) throw new IllegalStateException("Real skill scenario failed", serverFailure);
        if (++total > 3600) throw new IllegalStateException("Darkmatter concept capture timed out");
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed to main menu")) {
                    button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                    break;
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().openWorld("railgun", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
        if (!configured) {
            configured = true;
            mc.player.setNoGravity(false);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            originalFov = mc.options.fov().get();
            mc.options.fov().set(70);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            server(DarkmatterConceptClientSmoke::configure);
        }
        if (!ready) return;
        // The isolated world can show an existing resource-recovery toast after loading.
        // Give that UI feedback time to close before capturing any approved skill effect.
        if (++warmupTicks <= 200) return;
        ticks++;
        observeGraphs();

        if (ticks == 25) capture("00_before");
        if (ticks == 30) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> {
                refill(p, 20); require(DarkmatterGeneration.Server.beginGeneration(p, DarkmatterGeneration.Mode.CREATE), "Real generation started");
            });
        }
        if (ticks == 34 || ticks == 37 || ticks == 40) capture("motion/generation_" + String.format("%03d", ticks - 30));
        if (ticks == 36) captureGraph("darkmatter_generation", "01_generation");
        if (ticks == 43) server(p -> DarkmatterGeneration.Server.endGeneration(p, DarkmatterGeneration.Mode.CREATE));
        if (ticks == 50) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> {
                refill(p, 50); require(DarkmatterGeneration.Server.beginGeneration(p, DarkmatterGeneration.Mode.ERASE), "Real elimination started");
            });
        }
        if (ticks == 54 || ticks == 57 || ticks == 60) capture("motion/elimination_" + String.format("%03d", ticks - 50));
        if (ticks == 56) captureGraph("darkmatter_elimination", "01_elimination");
        if (ticks == 63) server(p -> DarkmatterGeneration.Server.endGeneration(p, DarkmatterGeneration.Mode.ERASE));
        if (ticks == 70) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            server(p -> {
                refill(p, 100); require(DarkmatterPhaseTuning.Server.beginTuning(p, DarkmatterPhaseTuning.Direction.BETA), "Real phase tuning started");
            });
        }
        if (ticks == 78) captureGraph("darkmatter_phase_tuning", "03_phase_tuning");
        if (ticks == 82) server(p -> DarkmatterPhaseTuning.Server.endTuning(p, DarkmatterPhaseTuning.Direction.BETA));
        if (ticks == 95) server(p -> {
            refill(p, 100); require(DarkmatterShaping.Server.createShaped(p, DarkmatterShape.SWORD, 65, Map.of()), "Real shaping returns an inventory item");
        });
        if (ticks == 100) captureGraph("darkmatter_shaping", "02_shaping");
        if (ticks == 110) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ticks == 115) server(p -> {
            refill(p, 100); resetTarget(p, new Vec3(0, 81, 178));
            require(DarkmatterDisassemble.Server.tryProgramAttack(p, target, 16, .01f, 0), "Real nonterminal disassemble succeeds");
        });
        if (ticks == 119) captureGraph("darkmatter_disassemble", "04_disassemble_surface");
        if (ticks == 135) server(p -> {
            var pos = new BlockPos(0, 82, 178);
            target.setPos(3, 81, 181); target.setDeltaMovement(Vec3.ZERO);
            p.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            require(DarkmatterDisassemble.Server.tryProgramDestroyBlock(p, pos, 16, 0), "Real disassembled block is removed");
            require(p.level().getBlockState(pos).isAir(), "Block output follows actual removal");
        });
        if (ticks == 139) captureGraph("darkmatter_disassemble", "04_disassemble_block");
        if (ticks == 155 || ticks == 165) server(p -> {
            refill(p, 100); require(DarkmatterRadiation.Server.beginChannel(p), "Real interference channel started/refreshed");
        });
        if (ticks == 170) {
            require(InterferenceFieldClient.activeCount() == 1, "One maintained range field uses scene shaders");
            capture("06_interference");
        }
        if (ticks == 175) server(DarkmatterRadiation.Server::endChannel);
        if (ticks == 185) {
            require(InterferenceFieldClient.activeCount() == 0, "Interference stop clears its field");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (ticks == 190) server(p -> {
            refill(p, 100); p.getInventory().clearContent(); p.setHealth(8);
            if (!Skills.DARKMATTER_REPAIR.get().isEnabled(p)) Skills.DARKMATTER_REPAIR.get().toggle(p);
            require(DarkmatterRepair.Server.tryPulse(p), "Real healing pulse changes health");
        });
        if (ticks == 194) captureGraph("darkmatter_repair", "07_repair");
        if (ticks == 205) server(p -> {
            if (Skills.DARKMATTER_REPAIR.get().isEnabled(p)) Skills.DARKMATTER_REPAIR.get().toggle(p);
            p.setHealth(p.getMaxHealth());
        });
        if (ticks == 215) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ticks == 220) server(p -> {
            refill(p, 100); resetTarget(p, p.position().add(0, 0, 3));
            require(DarkmatterCut.Server.tryCast(p, new Vec3(0, 0, 1)), "Real single cut succeeds");
        });
        if (ticks == 223) captureGraph("darkmatter_cut", "05_cut");
        if (ticks == 240) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            server(p -> {
                refill(p, 100);
                resetTarget(p, new Vec3(8, 81, 179));
                Skills.DARKMATTER_SIX_WINGS.get().toggle(p);
            });
        }
        if (ticks == 250) {
            require(DarkmatterSixWingsVfxClient.activeCount() == 1, "One actor owns the six-wing graph");
            captureGraph("darkmatter_six_wings", "09_six_wings_front");
        }
        if (ticks == 254) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ticks == 256) captureGraph("darkmatter_six_wings", "09_six_wings_first_person");
        if (ticks == 258) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (ticks == 260) server(p -> {
            refill(p, 100); require(DarkmatterSixWings.Server.isActive(p), "Authoritative gamma state is active");
            resetTarget(p, p.position().add(0, 0, 1.7));
            p.attack(target); p.swing(InteractionHand.MAIN_HAND);
        });
        if (ticks >= 262 && ticks <= 270 && ticks % 2 == 0) capture("motion/wing_strike_" + ticks);
        if (ticks == 268) capture("09_wing_strike");
        if (ticks == 275) require(sawWingImpulse, "Real AttackEntityEvent drives the wing-strike pose");
        if (ticks == 280) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> {
                refill(p, 100); resetTarget(p, p.position().add(0, 0, 3));
                require(DarkmatterCut.Server.tryCast(p, new Vec3(0, 0, 1)), "Real gamma cut succeeds");
            });
        }
        if (ticks >= 282 && ticks <= 294) capture("motion/cut_gamma_" + ticks);
        if (ticks == 284) capture("05_cut_gamma_primary");
        if (ticks == 292) capture("05_cut_gamma_mirror");
        if (ticks == 300) require(gammaCuts.size() >= 2, "Gamma cut creates a distinct delayed mirror graph");
        if (ticks == 310) server(p -> Skills.DARKMATTER_SIX_WINGS.get().toggle(p));
        if (ticks == 325) {
            require(DarkmatterSixWingsVfxClient.activeCount() == 0, "Six wings close and remove their graph after toggle off");
            capture("09_six_wings_closed");
            mc.options.fov().set(50);
            yaw = -51; pitch = 18;
            server(p -> p.teleportTo(p.level(), -5, 81, 180, Set.of(), yaw, pitch, false));
        }
        if (ticks == 340) server(p -> {
            resetTarget(p, new Vec3(0, 81, 189));
            walkingId = summon(p, 0, 0, 0, 1, new Vec3(0, 81, 184));
            walkingStart = p.level().getEntity(walkingId).position();
        });
        if (ticks == 344) captureGraph("darkmatter_creation_summon", "08_creation_walk_gather");
        if (ticks == 357) { requireCreature(walkingId, 0, 0); capture("08_creation_walk"); }
        if (ticks == 363) server(p -> {
            var entity = p.level().getEntity(walkingId);
            require(entity != null && entity.position().distanceToSqr(walkingStart) > .01, "Walking construct uses real navigation");
        });
        if (ticks == 364) server(p -> focusBeetle(p, walkingId));
        if (ticks == 368) { requireCreature(walkingId, 0, 0); capture("08_creation_walk_close"); }
        if (ticks == 375) server(p -> DarkmatterCreation.Server.discardProgramCreated(p, walkingId));
        if (ticks == 378) capture("08_creation_walk_disassemble");
        if (ticks == 385) server(p -> flyingId = summon(p, 1, 1, 1, 2, new Vec3(0, 82, 184)));
        if (ticks == 389) captureGraph("darkmatter_creation_summon", "08_creation_fly_gather");
        if (ticks == 402) { requireCreature(flyingId, 1, 1); capture("08_creation_fly"); }
        if (ticks >= 406 && ticks <= 410 && ticks % 2 == 0) server(p -> focusBeetle(p, flyingId));
        if (ticks == 410) { requireCreature(flyingId, 1, 1); capture("08_creation_fly_close"); }
        if (ticks >= 394 && ticks <= 420 && ticks % 3 == 0) capture("motion/beetle_fly_" + ticks);
        if (ticks == 420) server(p -> DarkmatterCreation.Server.discardProgramCreated(p, flyingId));
        if (ticks == 423) capture("08_creation_fly_disassemble");
        if (ticks == 425) {
            yaw = -90; pitch = 10;
            server(p -> {
                // A contained tank isolates fluid ticks from the other skill fixtures.
                // Stage block updates before entity spawning so the client has a water fixture first.
                for (var x = -4; x <= 4; x++) for (var z = 181; z <= 194; z++) for (var y = 81; y <= 86; y++) {
                    var wall = x == -4 || x == 4 || z == 181 || z == 194;
                    p.level().setBlock(new BlockPos(x, y, z), (wall ? Blocks.GLASS : Blocks.WATER).defaultBlockState(), 3);
                }
                // FloatGoal is allowed to rise naturally; a glass lid retains it in actual water
                // when screenshot readback makes more server ticks elapse than client scenario ticks.
                for (var x = -4; x <= 4; x++) for (var z = 181; z <= 194; z++) {
                    p.level().setBlock(new BlockPos(x, 87, z), Blocks.GLASS.defaultBlockState(), 3);
                }
                p.teleportTo(p.level(), -5, 82, 185, Set.of(), yaw, pitch, false);
            });
        }
        if (ticks == 430) server(p -> {
            resetTarget(p, new Vec3(0, 82, 189));
            // Run the actual program summon, then arrange the successful entity in the water fixture.
            swimmingId = summon(p, 2, 2, 2, 3, new Vec3(6, 81, 185));
            var swimmer = (DarkmatterBeetle) p.level().getEntity(swimmingId);
            System.out.println(PREFIX + "Successful swimming blueprint initially spawned at " + swimmer.position());
            swimmer.setPos(0, 82, 185);
            swimmer.setDeltaMovement(Vec3.ZERO);
            swimmer.getNavigation().stop();
            swimmingFixture = swimmer;
            swimmingSpawnTime = p.level().getGameTime();
            System.out.println(PREFIX + "Swimming fixture placed the real entity at " + swimmer.position()
                    + " gameTime=" + swimmingSpawnTime + " block=" + p.level().getBlockState(swimmer.blockPosition())
                    + " collisionFree=" + p.level().noCollision(swimmer));
        });
        if (ticks == 434) captureGraph("darkmatter_creation_summon", "08_creation_swim_gather");
        if (ticks == 439) server(p -> {
            var creature = (DarkmatterBeetle) p.level().getEntity(swimmingId);
            require(creature != null && creature.isInWater(), "Server swimming fixture is actual water at "
                    + (creature == null ? "missing" : creature.position() + " entityTicks=" + creature.tickCount
                    + " elapsedServerTicks=" + (p.level().getGameTime() - swimmingSpawnTime)));
        });
        if (ticks == 443) {
            requireCreature(swimmingId, 2, 2);
            var creature = clientCreature(swimmingId);
            require(creature.isInWater(), "Swimming body is actually immersed in water");
            capture("08_creation_swim");
        }
        if (ticks >= 438 && ticks <= 460 && ticks % 3 == 0) capture("motion/beetle_swim_" + ticks);
        if (ticks == 447) server(p -> focusBeetle(p, swimmingId));
        if (ticks == 451) {
            require(clientCreature(swimmingId).isInWater(), "Close swimming view remains immersed");
            capture("08_creation_swim_close");
        }
        if (ticks == 465) server(p -> DarkmatterCreation.Server.discardProgramCreated(p, swimmingId));
        if (ticks == 468) capture("08_creation_swim_disassemble");
        if (ticks == 480) {
            require(sawTrail, "Actual direct/homing cannon projectile receives its graph trail");
            require(sawCreatureContact, "Actual beetle hit receives its contact graph");
            require(observed.contains("darkmatter_creation_dismiss"), "Actual dismantling receives its graph");
            server(p -> {
                require(DarkmatterCreation.Server.owned(p).isEmpty(), "All three real summons release their authoritative records");
                target.discard();
                p.getAbilities().flying = false; p.onUpdateAbilities();
                yaw = -51; pitch = 18;
                p.teleportTo(p.level(), -5, 81, 180, Set.of(), yaw, pitch, false);
            });
        }
        if (ticks == 600) {
            require(DarkmatterVfxClient.activeCount() == 0, "All maintained darkmatter adapters are clear");
            require(DarkmatterSixWingsVfxClient.activeCount() == 0, "Six-wing adapter is clear");
            require(VfxGraphManager.INSTANCE.activeEffects().stream().noneMatch(e -> !e.isStopped() && e.assetKey().contains("darkmatter")), "No live darkmatter graph leaks after all skills stop");
            mc.options.fov().set(originalFov);
            capture("10_cleared");
            result();
            System.out.println(PREFIX + "PASSED");
        }
        if (ticks == 612) mc.stop();
    }

    private static void configure(ServerPlayer p) {
        for (var x = -20; x <= 20; x++) for (var z = 160; z <= 220; z++) {
            p.level().setBlock(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
        // Clear every height used by earlier failed water fixtures in the persistent isolated world.
        for (var x = -10; x <= 10; x++) for (var z = 169; z <= 195; z++) for (var y = 81; y <= 94; y++) {
            p.level().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        p.teleportTo(p.level(), 0, 81, 174.5, Set.of(), 0, 0, false);
        p.setGameMode(GameType.CREATIVE); p.setNoGravity(false); p.getInventory().clearContent(); p.setHealth(p.getMaxHealth());
        learn(p, Skills.DARKMATTER_GENERATION.get(), Skills.DARKMATTER_SHAPING.get(),
                Skills.DARKMATTER_PHASE_TUNING.get(), Skills.DARKMATTER_DISASSEMBLE.get(),
                Skills.DARKMATTER_CUT.get(), Skills.DARKMATTER_RADIATION.get(),
                Skills.DARKMATTER_REPAIR.get(), Skills.DARKMATTER_CREATION.get(), Skills.DARKMATTER_SIX_WINGS.get());
        Skills.DARKMATTER_REPAIR.get().toggle(p);
        Skills.DARKMATTER_SIX_WINGS.get().toggle(p);
        refill(p, 100);
        for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(80))) old.discard();
        for (var old : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(80))) old.discard();
        var cow = new Cow(net.minecraft.world.entity.EntityTypes.COW, p.level());
        cow.setPos(0, 81, 178); cow.setNoAi(true); cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); cow.setHealth(1000);
        p.level().addFreshEntity(cow); target = cow;
        command(p, "time set noon"); command(p, "weather clear");
        ready = true;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.darkmatterConceptSmoke") || swimmingFixture == null
                || swimmingFixture.isRemoved() || swimmingFixture.tickCount > 100) return;
        var creature = swimmingFixture;
        System.out.println(PREFIX + "Water diagnostic tick=" + creature.tickCount
                + " gameTime=" + creature.level().getGameTime()
                + " elapsed=" + (creature.level().getGameTime() - swimmingSpawnTime) + " position=" + creature.position()
                + " velocity=" + creature.getDeltaMovement() + " inWater=" + creature.isInWater()
                + " collision=" + creature.horizontalCollision + "/" + creature.verticalCollision
                + " ground=" + creature.onGround() + " block=" + creature.level().getBlockState(creature.blockPosition())
                + " above=" + creature.level().getBlockState(creature.blockPosition().above())
                + " swimSpeed=" + creature.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED)
                + " wantedY=" + creature.getMoveControl().getWantedY());
    }

    private static UUID summon(ServerPlayer p, int head, int torso, int limbs, int additional, Vec3 at) {
        refill(p, 100);
        var heads = List.of(DarkmatterCreatureRegistries.HEAD_JAW, DarkmatterCreatureRegistries.HEAD_CANNON, DarkmatterCreatureRegistries.HEAD_HOMING);
        var torsos = List.of(DarkmatterCreatureRegistries.TORSO_WALK, DarkmatterCreatureRegistries.TORSO_FLY, DarkmatterCreatureRegistries.TORSO_SWIM);
        var feet = List.of(DarkmatterCreatureRegistries.LIMBS_GUARD, DarkmatterCreatureRegistries.LIMBS_MINER, DarkmatterCreatureRegistries.LIMBS_CARRIER);
        var extras = List.of(DarkmatterCreatureRegistries.ADDITIONAL_NONE, DarkmatterCreatureRegistries.ADDITIONAL_CARAPACE, DarkmatterCreatureRegistries.ADDITIONAL_SENSOR, DarkmatterCreatureRegistries.ADDITIONAL_WEAPON);
        var blueprint = new DarkmatterCreatureBlueprint("Concept " + torso, 10,
                heads.get(head).toString(), torsos.get(torso).toString(), feet.get(limbs).toString(), extras.get(additional).toString(),
                125, 125, 125, 125, List.of(DarkmatterCreatureRegistries.MODULE_FOCUS.toString()));
        require(blueprint.validate(5).isEmpty(), "Real modular blueprint is valid");
        AbilitySystemServer.getSystem(p).updatePlayerSkillData(p.getUUID(), Skills.DARKMATTER_CREATION.get(), DarkmatterCreationData.class, data -> {
            data.setBlueprint(torso, blueprint, 5); data.setSelectedSlot(torso);
        });
        p.setLastHurtMob(target);
        var id = DarkmatterCreation.Server.tryProgramCreate(p, at, 16, 0).orElseThrow(() -> new IllegalStateException("Real summon rejected"));
        require(p.level().getEntity(id) instanceof DarkmatterBeetle, "Real summon creates the registered beetle entity");
        // Remove only the fixture nameplate so a crosshair pick cannot obscure the model close view.
        p.level().getEntity(id).setCustomName(null);
        return id;
    }

    private static void requireCreature(UUID id, int torso, int limbs) {
        var creature = clientCreature(id);
        require(creature.torsoModel() == torso && creature.limbsModel() == limbs, "Client receives the selected torso and six-foot module");
        require(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(creature) instanceof DarkmatterBeetleRenderer,
                "Client uses the real Blockbench beetle renderer");
    }

    private static DarkmatterBeetle clientCreature(UUID id) {
        var mc = Minecraft.getInstance();
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof DarkmatterBeetle beetle && beetle.getUUID().equals(id)) return beetle;
        throw new IllegalStateException("Tracked beetle is missing: " + id);
    }

    private static void focusBeetle(ServerPlayer p, UUID id) {
        var creature = p.level().getEntity(id);
        require(creature instanceof DarkmatterBeetle, "Close camera has a real tracked construct");
        var position = creature.position();
        var feet = position.add(-1.7, 0, -.8);
        var look = position.add(0, .55, 0).subtract(feet.add(0, p.getEyeHeight(), 0));
        yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        pitch = (float) Math.toDegrees(Math.atan2(-look.y, Math.sqrt(look.horizontalDistanceSqr())));
        p.getAbilities().flying = true; p.onUpdateAbilities();
        p.teleportTo(p.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, false);
    }

    private static void resetTarget(ServerPlayer p, Vec3 position) {
        target.setPos(position); target.setDeltaMovement(Vec3.ZERO); target.setHealth(target.getMaxHealth()); target.invulnerableTime = 0;
    }

    private static void refill(ServerPlayer p, float matter) {
        var manager = AbilitySystemServer.getSystem(p).getDarkmatterResourceManager();
        manager.reconcile(p);
        require(manager.debugSetPools(p, matter, 0, 0, 0), "Matter ledger initialized for a real skill call");
        manager.setAlphaPoints(p, 180);
    }

    private static void observeGraphs() {
        for (var effect : VfxGraphManager.INSTANCE.activeEffects()) {
            if (effect.isStopped() || !effect.assetKey().contains("darkmatter")) continue;
            var key = effect.assetKey().substring(effect.assetKey().lastIndexOf('/') + 1);
            observed.add(key);
            if (ticks >= 280 && ticks <= 300 && key.equals("darkmatter_cut")) gammaCuts.add(effect);
            if (ticks >= 260 && ticks <= 275 && key.equals("darkmatter_six_wings")) sawWingImpulse |= effect.effect().floatParameter("attack", 0) > .05f;
            if (key.equals("darkmatter_creation_trail") && !sawTrail) { sawTrail = true; capture("08_creation_cannon_trail"); }
            if (key.equals("darkmatter_creation_contact") && !sawCreatureContact) { sawCreatureContact = true; capture("08_creation_contact"); }
        }
    }

    private static void learn(ServerPlayer player, Skill... skills) {
        var system = AbilitySystemServer.getSystem(player); var id = player.getUUID();
        system.setPlayerAbilityCategory(id, skills[0].getCategory()); system.setPlayerLevel(id, 5);
        for (var skill : skills) {
            system.addPlayerSkill(player, skill.getKeyString());
            if (!skill.isEnabled(player)) skill.toggle(player);
            system.setPlayerSkillProficiency(id, skill, 0);
        }
        var data = system.getPlayerData(id);
        data.setAcademyMaxCp(1000); data.getCpData().setMaxCP(1000); data.getCpData().setAvailableCP(1000);
        for (var kind : List.of(SyncTypes.CP_DATA, SyncTypes.SKILL_DATA, SyncTypes.ABILITY_CATEGORY)) system.schedulePlayerSync(id, kind);
    }

    private static void command(ServerPlayer p, String command) {
        var server = p.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void server(Consumer<ServerPlayer> task) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void captureGraph(String key, String name) {
        require(VfxGraphManager.INSTANCE.activeEffects().stream().anyMatch(e -> !e.isStopped() && e.assetKey().endsWith(key)), "Rendered graph " + key);
        capture(name);
    }

    private static Path output() {
        return Path.of(System.getProperty("academy.darkmatterConceptOutput", "docs/vfx/darkmatter_concept_v2/runtime"));
    }

    private static void capture(String name) {
        var output = output().resolve(name + ".png");
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) { Files.createDirectories(output.getParent()); image.writeToFile(output); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    private static void result() {
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("smoke_result.txt"), "PASSED\nObserved real graphs: " + observed + "\nGamma cut instances: " + gammaCuts.size()
                    + "\nWing strike impulse: " + sawWingImpulse + "\nCannon trail: " + sawTrail + "\nCreature contact: " + sawCreatureContact + "\nNo live darkmatter graphs after cleanup.\n");
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        System.out.println(PREFIX + message);
    }
}
