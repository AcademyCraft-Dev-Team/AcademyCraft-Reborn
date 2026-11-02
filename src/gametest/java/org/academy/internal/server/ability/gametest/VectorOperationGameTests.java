package org.academy.internal.server.ability.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.vector.EffectHandle;
import org.academy.api.common.ability.vector.EffectOperation;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityResourceAccount;
import org.academy.api.server.ability.vector.VectorOperationAuthority;
import org.academy.api.server.ability.vector.VectorOperationRequest;
import org.academy.api.server.ability.vector.VectorOperationResult;
import org.academy.api.server.ability.vector.VectorOperationService;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.academy.internal.server.ability.gametest.HostileTargetGameTests.check;

/** Actual loaded entities, owner/arrow payload, opaque handles and competing authoritative actors. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorOperationGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("vector_operation_test_function");
    private static final AbilityResourceAccount ALREADY_PAID = new AbilityResourceAccount() {
        public double current() { throw new AssertionError("Effect service must not read CP"); }
        public double capacity() { throw new AssertionError("Effect service must not read CP"); }
        public boolean tryConsume(double amount) { throw new AssertionError("Effect service charged CP twice"); }
        public double recover(double amount) { throw new AssertionError("Effect service must not refund CP"); }
    };

    private VectorOperationGameTests() {}

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("vector_operation"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("vector_operation"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 80, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void run(GameTestHelper helper) {
        var subject = helper.spawn(EntityTypes.ZOMBIE, 3, 3, 3);
        var competitor = helper.spawn(EntityTypes.SKELETON, 3, 3, 5);
        subject.setNoAi(true);
        competitor.setNoAi(true);
        var arrow = helper.spawn(EntityTypes.ARROW, 5, 4, 5);
        arrow.setOwner(competitor);
        arrow.setBaseDamage(13.25);
        arrow.setDeltaMovement(new Vec3(0, 0, 1));
        var arrowId = arrow.getUUID();
        var context = new AbilityActorContext(subject, Skills.VECTOR_REFLECTION.get(), 1, 1, 0, ALREADY_PAID);
        var other = new AbilityActorContext(competitor, Skills.VECTOR_REFLECTION.get(), 1, 1, 0, ALREADY_PAID);
        var authorized = new AtomicBoolean(true);
        VectorOperationAuthority authority = (actor, effect, request) -> authorized.get();
        var sample = VectorOperationService.observe(arrow);
        check(helper, sample.descriptor().supports(EffectOperation.CONTROL_EXISTING), "Vanilla arrow has no flight binding");
        check(helper, sample.ownerId().orElseThrow().equals(competitor.getUUID()), "Original owner was not observed");
        var request = VectorOperationRequest.control(new Vec3(.25, 0, 0), 32, 20);
        var revoked = VectorOperationService.prepare(context, authority, sample.handle(), request).plan().orElseThrow();
        authorized.set(false);
        check(helper, !VectorOperationService.apply(context, revoked).applied(), "Revoked prepare still applied");
        check(helper, arrow.getOwner() == competitor, "Rejected action changed owner");
        check(helper, !VectorProjectileRedirects.isRedirected(arrow), "Rejected action marked defense handled");
        authorized.set(true);

        var first = VectorOperationService.prepare(context, authority, sample.handle(),
                VectorOperationRequest.control(request.velocity(), 32, 20)).plan().orElseThrow();
        var second = VectorOperationService.prepare(other, authority, sample.handle(),
                VectorOperationRequest.control(new Vec3(0, .25, 0), 32, 20)).plan().orElseThrow();
        check(helper, VectorOperationService.apply(other, first).status() == VectorOperationResult.Status.STALE,
                "Another actor could use a prepared token");
        var freshCallerContext = new AbilityActorContext(subject, Skills.VECTOR_REFLECTION.get(), 2, 3, 3, ALREADY_PAID);
        var applied = VectorOperationService.apply(freshCallerContext, first);
        check(helper, applied.status() == VectorOperationResult.Status.APPLIED, "Prepared action did not apply: " + applied.reason());
        check(helper, arrow.getUUID().equals(arrowId) && baseDamage(arrow) == 13.25,
                "Redirect replaced the projectile or payload");
        check(helper, arrow.getOwner() == subject, "Owner and damage attribution did not transfer");
        check(helper, arrow.getDeltaMovement().equals(request.velocity()), "Native entity did not accept velocity");
        check(helper, VectorProjectileRedirects.isRedirected(arrow), "Native defense cannot see the claim");
        check(helper, !VectorOperationService.mayAffect(arrow, subject), "Public impact admission allowed a self hit");
        check(helper, VectorOperationService.mayAffect(arrow, competitor), "Public impact admission rejected an unrelated target");
        check(helper, VectorOperationService.apply(context, first).status() == VectorOperationResult.Status.ALREADY_APPLIED,
                "Reusing an invocation applied twice");
        check(helper, !VectorOperationService.apply(other, second).applied(), "Competing actor took an already claimed effect");
        var forged = new EffectHandle(UUID.randomUUID(), sample.handle().dimension(), arrowId, sample.handle().generation());
        check(helper, !VectorOperationService.prepare(context, authority, forged, request).prepared(),
                "A foreign server session token resolved an effect");

        var control = applied.handle().orElseThrow();
        authorized.set(false);
        check(helper, !control.tick(), "Loss of authority did not terminate control");
        control.stop();
        arrow.setDeltaMovement(new Vec3(0, 0, .25));
        check(helper, !control.tick() && arrow.getDeltaMovement().equals(new Vec3(0, 0, .25)),
                "Canceled handle resumed mutation");
        check(helper, arrow.getOwner() == subject, "Cancel unexpectedly restored prior ownership");
        authorized.set(true);
        check(helper, !VectorOperationService.prepare(context, authority, sample.handle(),
                VectorOperationRequest.control(Vec3.ZERO, 32, 10)).prepared(),
                "Vanilla flight without a motion hook promised stationary control");
        var resumed = VectorOperationService.prepare(context, authority, sample.handle(),
                VectorOperationRequest.control(new Vec3(0, .5, 0), 32, 10));
        check(helper, resumed.prepared(), "Completed own lease permanently locked the effect: " + resumed.reason());
        var resumedResult = VectorOperationService.apply(context, resumed.plan().orElseThrow());
        check(helper, resumedResult.applied() && arrow.getDeltaMovement().equals(new Vec3(0, .5, 0)),
                "Authorized owner could not resume control");
        resumedResult.handle().orElseThrow().stop();
        arrow.discard();
        check(helper, !VectorOperationService.prepare(context, authority, sample.handle(), request).prepared(),
                "Removed effect handle remained usable");
        subject.discard();
        competitor.discard();
        helper.succeed();
    }

    private static double baseDamage(AbstractArrow arrow) {
        try {
            var field = AbstractArrow.class.getDeclaredField("baseDamage");
            field.setAccessible(true);
            return field.getDouble(arrow);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot verify the native arrow payload", failure);
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { VectorOperationGameTests.run(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Public vector operation contracts"); }
    }
}
