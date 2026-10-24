package org.academy.internal.common.world.entity.misaka.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.*;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.*;
import org.academy.internal.server.ability.EntityAbilityRuntime;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import java.util.*;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaAbilityGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("misaka_ability_function");
    @SubscribeEvent
    private static void type(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> AbilityTest.CODEC);
    }
    @SubscribeEvent
    private static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("misaka/abilities"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("misaka_abilities"), new AbilityTest(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 160, 0, true, Rotation.NONE, false, 1, 1, false, 32)));
    }

    public static void verifyApi(ServerLevel level, Vec3 position) {
        var actor = golem(level, position);
        var target = golem(level, position.add(3, 0, 0));
        actor.setTarget(target);
        try {
            EntityAbilityService.assign(actor, EntityAbilityRuntime.sisterProfile());
            var arc = Skills.ARC_GENERATE.get().getKey();
            java.util.function.Consumer<EntityAbilityCastEvent> cancel = event -> { if (event.actor() == actor) event.setCanceled(true); };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
            try {
                require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(arc, target)) == AbilityCastResult.CANCELED, "Cast hook cancels");
                require(EntityAbilityService.snapshot(actor).availableCp() == 100, "Canceled casts cost no CP");
            } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
            float health = target.getHealth();
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(arc, target)) == AbilityCastResult.ACCEPTED, "Other living entity casts arc");
            require(target.getHealth() < health, "Arc deals damage");
            require(EntityAbilityService.inspect(actor).orElseThrow().cp().current() < 100, "Cast reserves CP");
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(arc, target)) == AbilityCastResult.COOLDOWN, "Cooldown rejects a duplicate");
            EntityAbilityService.setEnabled(actor, arc, false);
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(arc, target)) == AbilityCastResult.DISABLED, "Disabled skill cannot cast");

            EntityAbilityService.assign(actor, EntityAbilityRuntime.sisterProfile());
            target.invulnerableTime = 0;
            health = target.getHealth();
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(Skills.THUNDER_LANCE.get().getKey(), target)) == AbilityCastResult.ACCEPTED, "Lance casts");
            require(target.getHealth() < health, "Lance damages");

            EntityAbilityService.assign(actor, EntityAbilityRuntime.sisterProfile());
            var contact = Skills.ELECTRICAL_CONTACT.get().getKey();
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(contact, null)) == AbilityCastResult.ACCEPTED, "Contact starts");
            require(EntityAbilityService.inspect(actor).orElseThrow().cp().current() == 90, "Contact occupies ten CP");
            target.snapTo(position.x + 1, position.y, position.z, 0, 0);
            target.invulnerableTime = 0;
            health = target.getHealth();
            for (int i = 0; i < 20; i++) EntityAbilityService.tick(actor);
            require(target.getHealth() < health, "Contact pulses");
            EntityAbilityService.stop(actor, contact);
            require(EntityAbilityService.inspect(actor).orElseThrow().cp().current() == 100, "Stop releases maintenance");

            EntityAbilityService.assign(actor, EntityAbilityRuntime.sisterProfile());
            target.snapTo(position.x + 3, position.y, position.z, 0, 0);
            target.invulnerableTime = 0;
            health = target.getHealth();
            var nova = Skills.LIGHTNING_NOVA.get().getKey();
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(nova, null)) == AbilityCastResult.ACCEPTED, "Nova starts");
            require(EntityAbilityService.tryCast(actor, AbilityCastRequest.autonomous(nova, null)) == AbilityCastResult.BUSY, "One nova per caster");
            for (int i = 0; i < 201; i++) EntityAbilityService.tick(actor);
            require(target.getHealth() < health, "Nova wave hits");
            require(!EntityAbilityService.isRunning(actor, nova), "Nova ends cleanly");
            require(EntityAbilityService.inspect(actor).orElseThrow().cp().current() == 100, "Timed CP recovers");

            var sister = sister(level, position.add(6, 0, 0), MisakaPersonality.TIMID);
            try {
                require(!EntityAbilityService.mayTarget(sister, sister), "Self protection");
                var other = sister(level, position.add(7, 0, 0), MisakaPersonality.COLD);
                try { require(!EntityAbilityService.mayTarget(sister, other), "Sisters are protected"); }
                finally { discard(other); }
                var record = sister.rosterRecord().orElseThrow();
                record.incapacitated = true;
                MisakaSisterRosterSync.syncFromRecord(sister, record);
                require(EntityAbilityService.tryCast(sister, AbilityCastRequest.autonomous(arc, target)) == AbilityCastResult.INACTIVE, "Downed actors cannot cast");
                record.incapacitated = false;
                record.awakened = false;
                MisakaSisterRosterSync.syncFromRecord(sister, record);
                require(EntityAbilityService.tryCast(sister, AbilityCastRequest.autonomous(arc, target)) == AbilityCastResult.INACTIVE, "Unawakened actors cannot cast");
            } finally { discard(sister); }
        } finally { actor.discard(); target.discard(); }
    }

    public record Scenario(MisakaSisterEntity sister, Mob aggressor, float health, List<net.minecraft.world.level.ChunkPos> forcedChunks) {}
    public static List<Scenario> spawnRetaliation(ServerLevel level, Vec3 origin) {
        var scenarios = new ArrayList<Scenario>();
        var base = net.minecraft.core.BlockPos.containing(origin);
        for (int x = -12; x <= 36; x++) for (int z = -12; z <= 12; z++) {
            level.setBlockAndUpdate(base.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        for (var personality : MisakaPersonality.values()) {
            var position = origin.add(personality.ordinal() * 7, 0, 0);
            var center = new net.minecraft.world.level.ChunkPos((int) Math.floor(position.x) >> 4, (int) Math.floor(position.z) >> 4);
            var forced = new ArrayList<net.minecraft.world.level.ChunkPos>();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var chunk = new net.minecraft.world.level.ChunkPos(center.x() + x, center.z() + z);
                if (level.setChunkForced(chunk.x(), chunk.z(), true)) {
                    forced.add(chunk);
                }
            }
            var sister = sister(level, position, personality);
            var target = golem(level, position.add(3, 0, 0));
            target.setTarget(sister); // Non-monster attacker: targeting alone must trigger defense.
            scenarios.add(new Scenario(sister, target, target.getHealth(), forced));
        }
        return scenarios;
    }
    public static void finishRetaliation(List<Scenario> scenarios) {
        try {
            for (var scenario : scenarios) AcademyCraft.LOGGER.info("Retaliation probe {} ticks={} active={} cp={} health={} sister={} target={} aggro={}",
                    scenario.sister().getPersonality(), scenario.sister().tickCount, EntityAbilityService.canAct(scenario.sister()),
                    EntityAbilityService.snapshot(scenario.sister()), scenario.aggressor().getHealth(), scenario.sister().position(),
                    scenario.aggressor().position(), scenario.aggressor().getTarget() == scenario.sister());
            for (var scenario : scenarios) {
                require(scenario.aggressor().getHealth() < scenario.health(), scenario.sister().getPersonality() + " retaliates when targeted");
                require(EntityAbilityService.inspect(scenario.sister()).orElseThrow().cp().current() < 90,
                        scenario.sister().getPersonality() + " uses an active skill, not just contact");
            }
        } finally { for (var scenario : scenarios) {
            var level = (ServerLevel) scenario.sister().level();
            discard(scenario.sister()); scenario.aggressor().discard();
            for (var chunk : scenario.forcedChunks()) level.setChunkForced(chunk.x(), chunk.z(), false);
        } }
    }
    public static MisakaSisterEntity sister(ServerLevel level, Vec3 position, MisakaPersonality personality) {
        var sister = EntityTypes.MISAKA_SISTER.get().create(level, EntitySpawnReason.COMMAND);
        sister.snapTo(position.x, position.y, position.z, 0, 0);
        level.addFreshEntity(sister);
        var record = sister.rosterRecord().orElseThrow();
        record.awakened = true;
        record.personality = personality;
        record.wanderStyle = WanderStyle.FREE_MOVE;
        sister.getFoodData().setFoodLevel(20);
        MisakaSisterRosterSync.syncFromRecord(sister, record);
        return sister;
    }
    private static IronGolem golem(ServerLevel level, Vec3 position) {
        var golem = net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
        golem.snapTo(position.x, position.y, position.z, 0, 0);
        golem.setNoAi(true);
        level.addFreshEntity(golem);
        return golem;
    }
    public static void discard(MisakaSisterEntity sister) {
        var uuid = sister.getMisakaUuid();
        sister.discard();
        MisakaSisterRoster.get(sister.level().getServer()).release(uuid);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class AbilityTest extends GameTestInstance {
        private static final MapCodec<AbilityTest> CODEC = TestData.CODEC.xmap(AbilityTest::new, AbilityTest::info);
        private AbilityTest(TestData<Holder<TestEnvironmentDefinition<?>>> data) { super(data); }
        public void run(GameTestHelper helper) {
            var origin = helper.absoluteVec(new Vec3(2, 2, 2));
            verifyApi(helper.getLevel(), origin);
            var scenarios = spawnRetaliation(helper.getLevel(), origin);
            helper.runAfterDelay(70, () -> { finishRetaliation(scenarios); helper.succeed(); });
        }
        public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        protected MutableComponent typeDescription() { return Component.literal("Living actor abilities and sister retaliation"); }
    }
}
