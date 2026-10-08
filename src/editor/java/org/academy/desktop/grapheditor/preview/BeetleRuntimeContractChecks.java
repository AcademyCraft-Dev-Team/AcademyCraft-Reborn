package org.academy.desktop.grapheditor.preview;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureModuleType;
import org.academy.api.common.ability.darkmatter.DarkmatterCreaturePartType;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Real entity save/load and extension-cache checks before capture warmup; never spawns the probe. */
final class BeetleRuntimeContractChecks {
    private BeetleRuntimeContractChecks() { }

    static List<String> verify(List<ServerPlayer> actors) {
        try {
            var first = actors.getFirst(); var second = actors.get(1); var level = first.level();
            var probe = new DarkmatterBeetle(EntityTypes.DARKMATTER_BEETLE.get(), level);
            var cached = DarkmatterBeetle.class.getDeclaredField("cachedHeadModel"); cached.setAccessible(true);
            var keys = DarkmatterBeetle.class.getDeclaredField("moduleKeys"); keys.setAccessible(true);
            probe.setOwnerUUID(first.getUUID());
            probe.applyBlueprint(blueprint(DarkmatterCreatureRegistries.HEAD_CANNON,
                    List.of(DarkmatterCreatureRegistries.MODULE_FOCUS.toString())), 0, 5, 0, false);
            require(cached.getInt(probe) == 1 && keys.get(probe).equals(List.of(DarkmatterCreatureRegistries.MODULE_FOCUS)), "Applied cannon and module keys are cached");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            probe.saveWithoutId(saved);
            probe.setOwnerUUID(second.getUUID());
            probe.applyBlueprint(blueprint(DarkmatterCreatureRegistries.HEAD_JAW, List.of()), 1, 5, 0, false);
            require(cached.getInt(probe) == 0 && ((List<?>) keys.get(probe)).isEmpty(), "Blueprint change invalidates both derived caches");
            require(probe.getOwnerPlayer() == second && probe.isOwnedBy(second) && !probe.isOwnedBy(first), "Owner change resolves the current player");
            probe.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            require(cached.getInt(probe) == 1 && probe.headModel() == 1 && probe.getOwnerPlayer() == first
                    && keys.get(probe).equals(List.of(DarkmatterCreatureRegistries.MODULE_FOCUS)), "Real entity load rebuilds keys and restores current ownership");

            var lateHead = Identifier.parse("academy:beetle_fixture_late_head");
            var lateModule = Identifier.parse("academy:beetle_fixture_late_module");
            probe.applyBlueprint(blueprint(lateHead, List.of(lateModule.toString())), 2, 5, 0, false);
            require(cached.getInt(probe) < 0, "Unknown extension starts unresolved");
            DarkmatterCreatureRegistries.registerPart(new DarkmatterCreaturePartType(lateHead,
                    DarkmatterCreaturePartType.BodySlot.HEAD, 2, null));
            var registered = DarkmatterBeetle.class.getDeclaredMethod("hasRegisteredHead"); registered.setAccessible(true);
            require((boolean) registered.invoke(probe) && cached.getInt(probe) == 2, "Late head registration remains discoverable");
            var calls = new AtomicInteger();
            DarkmatterCreatureRegistries.registerModule(new DarkmatterCreatureModuleType(lateModule, 1,
                    (_, _, _) -> calls.incrementAndGet()));
            var modules = DarkmatterBeetle.class.getDeclaredMethod("runModules", net.minecraft.server.level.ServerLevel.class, ServerPlayer.class);
            modules.setAccessible(true); modules.invoke(probe, level, first);
            require(calls.get() == 1, "Late module registration still executes its public handler");
            return List.of("blueprint_cache_replacement", "owner_resolution", "entity_save_load_cache_rebuild", "late_head_registry", "late_module_handler");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Real beetle cache contract check failed", e);
        }
    }

    private static DarkmatterCreatureBlueprint blueprint(Identifier head, List<String> modules) {
        return new DarkmatterCreatureBlueprint("Cache probe", 10, head.toString(), DarkmatterCreatureRegistries.TORSO_WALK.toString(),
                DarkmatterCreatureRegistries.LIMBS_GUARD.toString(), DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString(),
                125, 125, 125, 125, modules);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        System.out.println("[beetle-performance] runtime contract: " + message);
    }
}
