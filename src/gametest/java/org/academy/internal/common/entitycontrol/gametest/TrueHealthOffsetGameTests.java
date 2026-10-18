package org.academy.internal.common.entitycontrol.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.LawDetonationDamageSource;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.entitycontrol.*;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;

import java.util.List;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TrueHealthOffsetGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("true_health_offset_function");
    private TrueHealthOffsetGameTests() {}

    @SubscribeEvent
    public static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> OffsetTest.CODEC);
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("true_health_offset"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                460, 0, true, Rotation.NONE, false, 1, 1, false, 16);
        event.registerTest(AcademyCraft.academy("true_health_offset"), new OffsetTest(data));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var cta = new DamageSource(types.getOrThrow(DamageTypes.CTA));
        var dm = new LawDetonationDamageSource(SkillDamageSource.from(
                new DamageSource(types.getOrThrow(DamageTypes.DM_DAMAGE)), Skills.DARKMATTER_CUT.get()));

        var locked = new LockedCow(level);
        locked.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        locked.setHealth(100);
        locked.locked = true;
        locked.setNoAi(true);
        locked.setNoGravity(true);
        locked.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 3)));
        level.addFreshEntity(locked);
        helper.assertTrue(SkillDamageUtil.applyVerifiedTrueHealth(locked, cta, 30), "Rejected setter must use projection");
        close(helper, 100, EntityControlApi.getAuthoritativeHealth(locked), "Raw health remains locked");
        close(helper, 70, locked.getHealth(), "CTA projection");
        locked.tick();
        close(helper, 70, locked.getHealth(), "Subclass rollback after super.tick");
        locked.heal(10);
        close(helper, 80, locked.getHealth(), "Recognized healing releases projection");
        helper.assertTrue(SkillDamageUtil.applyVerifiedTrueHealth(locked, dm, 20), "DM detonation must share projection");
        close(helper, 60, locked.getHealth(), "DM accumulates from effective health");

        var ordinary = helper.spawn(EntityTypes.COW, 5, 2, 3);
        ordinary.setNoAi(true);
        ordinary.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        ordinary.setHealth(100);
        helper.assertTrue(SkillDamageUtil.applyVerifiedTrueHealth(ordinary, cta, 30), "Normal CTA must commit");
        close(helper, 70, ordinary.getHealth(), "Successful write is not subtracted twice");
        ordinary.heal(10);
        close(helper, 80, ordinary.getHealth(), "Vanilla healing writes through");
        ordinary.invulnerableTime = 0;
        ordinary.hurtServer(level, ordinary.damageSources().generic(), 10);
        close(helper, 70, ordinary.getHealth(), "Ordinary damage uses effective health");
        ordinary.setHealth(100);
        close(helper, 70, ordinary.getHealth(), "Rollback cannot undo committed ordinary damage");

        var saved = locked.getPersistentData().copy();
        var restored = new LockedCow(level);
        restored.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        restored.setHealth(100);
        restored.locked = true;
        restored.setNoAi(true);
        restored.setNoGravity(true);
        restored.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(7, 2, 3)));
        restored.getPersistentData().merge(saved);
        level.addFreshEntity(restored);
        close(helper, 60, restored.getHealth(), "Serialized state restores with a new instance mask");

        var lethal = helper.spawn(EntityTypes.COW, 9, 2, 3);
        helper.assertTrue(SkillDamageUtil.applyVerifiedTrueHealth(lethal, cta, 100), "Lethal CTA must commit");
        helper.assertTrue(lethal.isDeadOrDying(), "Projected zero must enter death");
        var lethalState = ((HealthOffsetAccess) lethal).academy$getHealthOffset();
        helper.assertTrue(lethalState != null && lethalState.dead, "Death freezes projection");

        var protectedCow = helper.spawn(EntityTypes.COW, 11, 2, 3);
        protectedCow.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        helper.assertTrue(SkillDamageUtil.applyVerifiedTrueHealth(protectedCow, cta, 100), "Totem hit must commit");
        helper.assertTrue(protectedCow.getHealth() > 0 && !protectedCow.isDeadOrDying(), "Totem must restore life");
        helper.assertTrue(((HealthOffsetAccess) protectedCow).academy$getHealthOffset() == null,
                "Totem must clear zero projection");


        var canceled = helper.spawn(EntityTypes.COW, 12, 2, 5);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> cancelDeath =
                event -> { if (event.getEntity() == canceled) event.setCanceled(true); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancelDeath);
        try {
            SkillDamageUtil.applyVerifiedTrueHealth(canceled, cta, 100);
            helper.assertTrue(canceled.getHealth() > 0 && !canceled.isDeadOrDying(),
                    "Canceled death restores life and releases zero projection");
            helper.assertTrue(((HealthOffsetAccess) canceled).academy$getHealthOffset() == null,
                    "Canceled death clears offset state");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancelDeath);
        }

        long previousHit = ((HealthOffsetAccess) (Object) locked).academy$getHealthOffset().lastHit;
        helper.assertTrue(!SkillDamageUtil.applyVerifiedTrueHealth(locked, cta, 0), "Zero damage is rejected");
        helper.assertTrue(((HealthOffsetAccess) (Object) locked).academy$getHealthOffset().lastHit == previousHit,
                "Invalid damage cannot refresh the deadline");


        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingHealEvent> cancelHeal =
                event -> { if (event.getEntity() == locked) event.setCanceled(true); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancelHeal);
        try {
            locked.heal(50);
            close(helper, 60, locked.getHealth(), "Canceled healing must not release offset");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancelHeal);
        }

        var opaque = new Cow(EntityTypes.COW, level) {
            @Override public float getHealth() { return 100; }
            @Override public void setHealth(float value) {}
        };
        opaque.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        helper.assertTrue(!SkillDamageUtil.applyVerifiedTrueHealth(opaque, cta, 30),
                "An unhooked constant getter must not report projected success");
        helper.assertTrue(((HealthOffsetAccess) opaque).academy$getHealthOffset() == null,
                "Unsupported getter cannot retain a phantom offset");
        helper.assertTrue(java.util.Arrays.stream(net.minecraft.world.entity.LivingEntity.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().contains("academy$inlineOffsetRead")),
                "Runtime getHealth helper must have been removed after bytecode inlining");

        helper.runAfterDelay(199, () -> close(helper, 60, locked.getHealth(), "No release before ten seconds"));
        helper.runAfterDelay(300, () -> {
            close(helper, 80, locked.getHealth(), "Half of the offset releases after fifteen seconds");
            close(helper, 80, restored.getHealth(), "Reload retains the original deadline");
        });
        helper.runAfterDelay(402, () -> {
            close(helper, 100, locked.getHealth(), "Offset clears at twenty seconds");
            helper.assertTrue(((HealthOffsetAccess) (Object) locked).academy$getHealthOffset() == null,
                    "Expired state must be removed");
            locked.discard();
            restored.discard();
            helper.succeed();
        });
    }

    private static void close(GameTestHelper helper, float expected, float actual, String message) {
        helper.assertTrue(Float.isFinite(actual) && Math.abs(expected - actual) < 0.5f,
                message + ": expected " + expected + ", actual " + actual);
    }

    private static final class LockedCow extends Cow {
        private boolean locked;
        private LockedCow(Level level) { super(EntityTypes.COW, level); }
        @Override public void setHealth(float value) {
            if (!locked || value >= 100) super.setHealth(value);
        }
        @Override public void tick() {
            super.tick();
            if (locked) super.setHealth(100);
        }
    }

    private static final class OffsetTest extends GameTestInstance {
        private static final MapCodec<OffsetTest> CODEC = TestData.CODEC.xmap(OffsetTest::new, OffsetTest::info);
        private OffsetTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { verify(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("True health projection"); }
    }
}
