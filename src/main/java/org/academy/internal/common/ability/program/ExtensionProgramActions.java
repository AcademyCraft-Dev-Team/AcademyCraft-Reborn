package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.LearningHelper;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.program.ProgramActionContext;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Bound to one invocation. Every addon action checks the combined outstanding addon CP budget. */
final class ExtensionProgramActions {
    private final ProgramExecutionFrame frame;
    private final ServerPlayer player;
    private final net.minecraft.resources.Identifier category;
    private final double range;
    private final List<PendingCost> costs = new ArrayList<>();

    ExtensionProgramActions(ProgramExecutionFrame frame) {
        this.frame = frame;
        var resolver = frame.environment(ProgramTargetResolver.class)
                .orElseThrow(() -> new IllegalStateException("Missing target resolver"));
        if (!(resolver.caster() instanceof ServerPlayer caster)) throw new IllegalStateException("A server player is required");
        player = caster;
        category = AbilitySystemServer.getSystem(player).getPlayerAbilityCategory(player.getUUID()).getKey();
        range = AbilityProgramDefinitions.require(category).spatialLimits().actionRange();
    }

    void stage(int nodeId, ProgramNodeScope scope, ProgramAction action) {
        Objects.requireNonNull(action);
        var key = Objects.requireNonNull(action.requiredSkill());
        if (!scope.allowsCategory(category) || !scope.requiredCapabilities().contains(key.identifier())) {
            throw new IllegalArgumentException("Action skill must be declared in the node's required capabilities: " + key.identifier());
        }
        var skill = Registries.SKILLS.get(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown action skill " + key.identifier())).value();
        var targets = List.copyOf(action.targets());
        if (targets.size() > 256) throw new IllegalArgumentException("Too many action targets");
        var rawCost = action.cpCost();
        if (!Float.isFinite(rawCost) || rawCost < 0) throw new IllegalArgumentException("Invalid action CP cost");
        var system = AbilitySystemServer.getSystem(player);
        var context = new ProgramActionContext(player, category, skill, nodeId, targets);
        var pending = new PendingCost(() -> system.quoteTimedOccupation(player, rawCost, skill));
        frame.transaction().stage(nodeId, new ProgramActionTransaction.ProgramAction() {
            @Override
            public void validate() throws Exception {
                if (!player.isAlive() || player.hasDisconnected() || player.isSpectator()
                        || !system.getPlayerAbilityCategory(player.getUUID()).getKey().equals(category)
                        || !LearningHelper.isSkillAvailableForCategory(system.getPlayerAbilityCategory(player.getUUID()), skill)
                        || !skill.isEnabled(player)) {
                    throw new IllegalStateException("Action skill is unavailable: " + key.identifier());
                }
                for (var target : targets) {
                    if (!target.isAlive() || target.isRemoved() || target.level() != player.level()
                            || target.distanceToSqr(player) > range * range
                            || target != player && (PvpSetting.shouldPrevent(player, target)
                            || FriendlyFireSetting.shouldPrevent(player, target))) {
                        throw new IllegalArgumentException("Action target is unavailable or outside category bounds");
                    }
                }
                var total = costs.stream().filter(cost -> !cost.paid).mapToDouble(cost -> cost.quote.getAsDouble()).sum();
                if (!Double.isFinite(total) || system.getPlayerAvailableCP(player.getUUID()) + 1.0E-4 < total) {
                    throw new IllegalStateException("Insufficient CP for staged addon actions");
                }
                action.validate(context);
            }

            @Override
            public ProgramActionTransaction.Undo apply() throws Exception {
                validate();
                if (!system.tryTimedOccupation(player, rawCost, skill)) throw new IllegalStateException("Insufficient CP");
                pending.paid = true;
                var effect = Objects.requireNonNull(action.apply(context), "Action effect");
                var key = frame.invocation().map(invocation -> new ServerProgramScheduler.SessionKey(
                        player.getUUID(), category, invocation.programId(), invocation.slot())).orElse(null);
                return ExtensionProgramEffects.track(player, targets, effect, key, category, skill);
            }
        });
        costs.add(pending);
    }

    private static final class PendingCost {
        private final DoubleSupplier quote;
        private boolean paid;
        private PendingCost(DoubleSupplier quote) { this.quote = quote; }
    }
}
