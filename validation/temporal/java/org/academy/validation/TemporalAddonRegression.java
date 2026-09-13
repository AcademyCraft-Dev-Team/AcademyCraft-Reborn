package org.academy.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalImmunityLease;
import org.academy.internal.client.time.TemporalClientRuntime;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.VectorReflectionRuntime;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only sample driver. Never compiled into ordinary development/release artifacts. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class TemporalAddonRegression {
    private static volatile int phase;
    private static volatile String failure;
    private static int ticks;
    private static int phaseTicks;
    private static int keyHits;
    private static int mouseHits;
    private static boolean opened;
    private static Cow immuneCow;
    private static Cow normalCow;
    private static TemporalImmunityLease cowLease;
    private static LivingEntity boss;
    private static int beforeImmune;
    private static int beforeNormal;
    private static int startServerTick;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (mc.gui.screen() instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
                for (var widget : warning.children()) {
                    if (widget instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getString().equals("Proceed to main menu")) {
                        button.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                                new net.minecraft.client.input.MouseButtonInfo(0, 0)));
                        break;
                    }
                }
            }
            if (!opened && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().openWorld("verification", mc::stop);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        if (++ticks < 40) return;
        if (failure != null || ticks > 1200) throw new IllegalStateException("TEMPORAL_REGRESSION_FAILED " + failure);
        try {
            if (phase == 0) {
                phase = -1;
                InputSystem.addKeyBinding("temporal_regression_key",
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, 321, 1, -1), c -> keyHits++);
                InputSystem.addKeyBinding("temporal_regression_mouse",
                        InputSystem.combo(InputSystem.InputType.MOUSE, 4, 1, -1), c -> mouseHits++);
                onServer(mc, player -> {
                    var system = AbilitySystemServer.getSystem(player);
                    var id = player.getUUID();
                    player.setGameMode(GameType.SURVIVAL);
                    system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get());
                    system.setPlayerLevel(id, 5);
                    system.setPlayerBaseMaxCP(id, 1000000);
                    system.setPlayerAvailableCP(id, 1000000);
                    system.addPlayerSkill(player, Skills.VECTOR_REFLECTION.get().getKeyString());
                    enableReflection(player);
                    immuneCow = cow(player, 2);
                    normalCow = cow(player, 4);
                    cowLease = TemporalApi.get(player).acquireTimeStopImmunity(immuneCow);
                    boss = (LivingEntity) BuiltInRegistries.ENTITY_TYPE
                            .getValue(Identifier.parse("shadowmaster:shadow_boss"))
                            .create(player.level(), EntitySpawnReason.COMMAND);
                    check(boss != null, "boss sample type");
                    boss.setPos(player.position().add(6, 0, 0));
                    phase = 1;
                });
            } else if (phase == 1 && TemporalClientRuntime.isExternallyImmune(mc.player)) {
                phase = -2;
                onServer(mc, player -> {
                    check((Boolean) Class.forName("cn.shadowmaster.ability.ShadowStep")
                            .getMethod("castByEntityTimestop", LivingEntity.class).invoke(null, boss), "boss cast");
                    sample("tick", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, player.level());
                    // The addon captured its freeze anchors. Owned writes must remain legal.
                    var destination = immuneCow.position().add(1, 0, 0);
                    immuneCow.setPos(destination);
                    immuneCow.setYRot(37);
                    immuneCow.setXRot(12);
                    immuneCow.setYHeadRot(37);
                    immuneCow.setYBodyRot(37);
                    var normalAnchor = normalCow.position();
                    normalCow.setPos(normalAnchor.add(1, 0, 0));
                    var playerDestination = player.position().add(0, 0, 1);
                    player.connection.teleport(playerDestination.x, playerDestination.y, playerDestination.z, 27, 9);
                    sample("tick", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, player.level());
                    check(immuneCow.position().distanceToSqr(destination) < 0.0001, "immune entity lock position");
                    check(Math.abs(immuneCow.getYRot() - 37) < 0.001, "immune yaw");
                    check(Math.abs(immuneCow.getXRot() - 12) < 0.001, "immune pitch");
                    check(Math.abs(immuneCow.getYHeadRot() - 37) < 0.001, "immune head");
                    check(normalCow.position().distanceToSqr(normalAnchor) < 0.0001, "normal entity still locked");
                    check(player.position().distanceToSqr(playerDestination) < 0.0001, "player teleport lock");
                    check(Math.abs(player.getYRot() - 27) < 0.001, "player rotation lock");
                    beforeImmune = immuneCow.tickCount;
                    beforeNormal = normalCow.tickCount;
                    startServerTick = player.level().getServer().getTickCount();
                    phase = 2;
                });
            } else if (phase == 2 && sampleActive()
                    && (Boolean) Class.forName("cn.shadowmaster.client.fx.BindLockClient").getMethod("isLocked").invoke(null)
                    && mc.getSingleplayerServer().getTickCount() > startServerTick + 12) {
                check(InputSystem.isKeyBindingEnabled("temporal_regression_key"), "protected binding enabled");
                checkInput(mc, true);
                phase = -3;
                onServer(mc, player -> {
                    check(immuneCow.tickCount > beforeImmune + 5, "immune deep tick cancellation");
                    check(normalCow.tickCount == beforeNormal, "normal tick cancellation preserved");
                    check(VectorReflection.Server.isActive(player), "reflection survived external locks");
                    VectorReflection.Server.forceDeactivate(player);
                    cowLease.close();
                    phase = 3;
                });
            } else if (phase == 3 && !TemporalClientRuntime.isExternallyImmune(mc.player)) {
                check(sampleActive(), "sample must still be active at immunity expiry");
                check(!InputSystem.isKeyBindingEnabled("temporal_regression_key"), "pending binding lock reapplied");
                checkInput(mc, false);
                phase = -4;
                onServer(mc, player -> { enableReflection(player); phase = 4; });
            } else if (phase == 4 && TemporalClientRuntime.isExternallyImmune(mc.player)) {
                check(sampleActive(), "sample must still be active at immunity reacquisition");
                check(InputSystem.isKeyBindingEnabled("temporal_regression_key"), "binding restored on reacquisition");
                checkInput(mc, true);
                // Owned GUI/HUD-style binding suppression must still work while immune.
                InputSystem.setKeyBindingEnabled("temporal_regression_key", false);
                check(!InputSystem.isKeyBindingEnabled("temporal_regression_key"), "owned disable preserved");
                InputSystem.setKeyBindingEnabled("temporal_regression_key", true);
                phase = -5;
                onServer(mc, player -> {
                    sample("end", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, player.level());
                    VectorReflection.Server.forceDeactivate(player);
                    immuneCow.discard();
                    normalCow.discard();
                    phase = 5;
                });
            } else if (phase == 5 && !sampleActive() && !TemporalClientRuntime.isExternallyImmune(mc.player)) {
                if (++phaseTicks < 5) return;
                checkInput(mc, true);
                AcademyCraft.getLogger().info("TEMPORAL_ADDON_REGRESSION_PASSED input keyboard mouse bindings position rotation tick immunity-expiry reacquire cleanup");
                mc.stop();
                phase = 6;
            }
        } catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.getLogger().error("TEMPORAL_REGRESSION_ERROR phase=" + phase, error);
            throw new IllegalStateException("TEMPORAL_REGRESSION_FAILED", error);
        }
    }

    private static void checkInput(Minecraft mc, boolean allowed) throws Exception {
        int keys = keyHits;
        int mouse = mouseHits;
        InputSystem.handleKey(1, new KeyEvent(321, 0, 0), new CallbackInfo("keyPress", true));
        InputSystem.handleKey(0, new KeyEvent(321, 0, 0), new CallbackInfo("keyPress", true));
        InputSystem.handleMouseButton(4, 1, 0, new CallbackInfo("onButton", true));
        InputSystem.handleMouseButton(4, 0, 0, new CallbackInfo("onButton", true));
        check(keyHits == keys + (allowed ? 1 : 0), "skill key input");
        check(mouseHits == mouse + (allowed ? 1 : 0), "skill mouse input");
        var keyPress = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        keyPress.setAccessible(true);
        keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), 1, new KeyEvent(87, 0, 0));
        check(mc.options.keyUp.isDown() == allowed, "vanilla movement key input");
        keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), 0, new KeyEvent(87, 0, 0));
        check(!mc.options.keyUp.isDown(), "movement release");
        var mouseClass = net.minecraft.client.MouseHandler.class;
        var dx = mouseClass.getDeclaredField("accumulatedDX");
        dx.setAccessible(true);
        dx.setDouble(mc.mouseHandler, 40);
        float beforeYaw = mc.player.getYRot();
        var turn = mouseClass.getDeclaredMethod("turnPlayer", double.class);
        turn.setAccessible(true);
        turn.invoke(mc.mouseHandler, 0.05);
        check((Math.abs(mc.player.getYRot() - beforeYaw) > 0.01) == allowed, "vanilla mouse turn");
        dx.setDouble(mc.mouseHandler, 0);
        checkUnknownEventOwner();
    }

    private static void checkUnknownEventOwner() throws Exception {
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(61, 1, "fixture/UnknownInputOwner", null, "java/lang/Object", null);
        var method = writer.visitMethod(9, "consume", "(Lorg/academy/api/client/input/InputControlEvent;)V", null, null);
        method.visitCode();
        method.visitVarInsn(25, 0);
        method.visitInsn(4);
        method.visitMethodInsn(182, "org/academy/api/client/input/InputControlEvent", "setCanceled", "(Z)V", false);
        method.visitInsn(177);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        var foreign = new FixtureLoader().define(writer.toByteArray()).getMethod("consume",
                org.academy.api.client.input.InputControlEvent.class);
        var input = new org.academy.api.client.input.KeyInputEvent(321, 0, 1, 0);
        foreign.invoke(null, input);
        check(input.isCanceled() != TemporalClientRuntime.isLocalExternallyImmune(), "unknown external event cancellation");
        var uiInput = new org.academy.api.client.input.KeyInputEvent(321, 0, 1, 0);
        org.academy.api.client.input.UiInputContext.run(() -> {
            try { foreign.invoke(null, uiInput); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        check(uiInput.isCanceled(), "GUI/HUD event cancellation preserved");
    }

    private static final class FixtureLoader extends ClassLoader {
        private FixtureLoader() { super(TemporalAddonRegression.class.getClassLoader()); }
        private Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }

    private static Cow cow(ServerPlayer player, int offset) {
        var cow = new Cow(EntityTypes.COW, player.level());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPersistenceRequired();
        cow.setPos(player.position().add(offset, 0, 0));
        player.level().addFreshEntity(cow);
        return cow;
    }

    private static void enableReflection(ServerPlayer player) {
        if (!Skills.VECTOR_REFLECTION.get().isEnabled(player)) Skills.VECTOR_REFLECTION.get().toggle(player);
        VectorReflectionRuntime.maintain(player);
        check(VectorReflection.Server.isActive(player), "reflection enabled");
        check(TemporalApi.get(player).isTimeStopImmune(player), "reflection immunity lease");
    }

    private static Object sample(String method, Class<?>[] types, Object... args) throws Exception {
        var entry = Class.forName("cn.shadowmaster.ability.TimeStopManager").getDeclaredMethod(method, types);
        entry.setAccessible(true);
        return entry.invoke(null, args);
    }

    private static boolean sampleActive() throws Exception {
        return (Boolean) Class.forName("cn.shadowmaster.client.fx.TimeStopScreenFx").getMethod("active").invoke(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void onServer(Minecraft mc, ServerAction action) {
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.run(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable error) {
                failure = error.toString();
                AcademyCraft.getLogger().error("TEMPORAL_REGRESSION_SERVER_ERROR phase=" + phase, error);
            }
        });
    }

    private interface ServerAction { void run(ServerPlayer player) throws Exception; }
}
