package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.resources.Identifier;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.api.common.entitycontrol.ControlBinding;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.common.entitycontrol.ControlContext;
import org.academy.api.common.entitycontrol.ControlDirective;
import org.academy.api.common.entitycontrol.ControlDomain;
import org.academy.api.common.entitycontrol.ControlRejectionReason;
import org.academy.api.common.entitycontrol.ControlSupport;
import org.academy.api.common.entitycontrol.MentalControlAdapter;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentalControlRuntimeTest {
    private static final Identifier SOURCE_A = Identifier.fromNamespaceAndPath("academy", "test_a");
    private static final Identifier SOURCE_B = Identifier.fromNamespaceAndPath("academy", "test_b");
    private static final Identifier DIMENSION = Identifier.withDefaultNamespace("overworld");

    @Test
    void higherPriorityAndNewestTieWin() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var firstTarget = UUID.randomUUID();
        var highTarget = UUID.randomUUID();
        var newestTarget = UUID.randomUUID();

        table.add(input(controller, subject, SOURCE_A, 1, 100, target(firstTarget)));
        table.add(input(UUID.randomUUID(), subject, SOURCE_B, 2, 100, target(highTarget)));
        assertTrue(highTarget.equals(table.forcedTarget(subject, 0)));

        table.add(input(UUID.randomUUID(), subject, Identifier.withDefaultNamespace("test_c"), 2, 100, target(newestTarget)));
        assertTrue(newestTarget.equals(table.forcedTarget(subject, 0)));
    }

    @Test
    void sameControllerSourceAndDomainReplacePreviousLease() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var first = table.add(input(controller, subject, SOURCE_A, 1, 100, target(UUID.randomUUID())));
        var replacementTarget = UUID.randomUUID();
        var replacement = table.add(input(controller, subject, SOURCE_A, 1, 100, target(replacementTarget)));

        assertFalse(table.isActive(first));
        assertTrue(table.isActive(replacement));
        assertTrue(replacementTarget.equals(table.forcedTarget(subject, 0)));
    }

    @Test
    void freezeRequiresBothEffectiveDomainsAndCloseIsIdempotent() {
        var table = new MentalControlRuntime.LeaseTable();
        var subject = UUID.randomUUID();
        var freeze = table.add(input(UUID.randomUUID(), subject, SOURCE_A, 1, 100, freeze()));

        assertTrue(table.isFrozen(subject, 0));
        table.remove(freeze);
        table.remove(freeze);
        assertFalse(table.isFrozen(subject, 0));
    }

    @Test
    void expiredLeaseNoLongerWinsAndIsRemovedByTickCleanup() {
        var table = new MentalControlRuntime.LeaseTable();
        var subject = UUID.randomUUID();
        var lease = table.add(input(UUID.randomUUID(), subject, SOURCE_A, 1, 20, target(UUID.randomUUID())));

        assertNull(table.forcedTarget(subject, 20));
        table.expire(20);
        assertFalse(table.isActive(lease));
    }

    @Test
    void cachedWinnerFallsBackAfterExpiryCleanup() {
        var table = new MentalControlRuntime.LeaseTable();
        var subject = UUID.randomUUID();
        var fallbackTarget = UUID.randomUUID();
        var expiringTarget = UUID.randomUUID();
        table.add(input(UUID.randomUUID(), subject, SOURCE_A, 1, 100, target(fallbackTarget)));
        table.add(input(UUID.randomUUID(), subject, SOURCE_B, 2, 20, target(expiringTarget)));

        assertTrue(expiringTarget.equals(table.forcedTarget(subject, 0)));
        table.expire(20);
        assertTrue(fallbackTarget.equals(table.forcedTarget(subject, 20)));
    }

    @Test
    void controllerAndSubjectCleanupRemoveOnlyMatchingLeases() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var otherController = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var otherSubject = UUID.randomUUID();
        var controllerLease = table.add(input(controller, subject, SOURCE_A, 1, 100, target(UUID.randomUUID())));
        var otherSubjectLease = table.add(input(controller, otherSubject, SOURCE_A, 1, 100, freeze()));
        var survivingLease = table.add(input(otherController, subject, SOURCE_B, 1, 100, freeze()));

        table.removeByController(controller);
        assertFalse(table.isActive(controllerLease));
        assertFalse(table.isActive(otherSubjectLease));
        assertTrue(table.isActive(survivingLease));

        table.removeBySubject(subject);
        assertFalse(table.isActive(survivingLease));
    }

    @Test
    void restoringSnapshotRecoversLeaseReplacedDuringFailedActivation() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var originalTarget = UUID.randomUUID();
        var original = table.add(input(controller, subject, SOURCE_A, 1, 100, target(originalTarget)));
        var snapshot = table.snapshotState();

        var replacement = table.add(input(
                controller,
                subject,
                SOURCE_A,
                1,
                100,
                target(UUID.randomUUID())
        ));
        assertFalse(table.isActive(original));
        assertTrue(table.isActive(replacement));

        table.restore(snapshot);

        assertTrue(table.isActive(original));
        assertFalse(table.isActive(replacement));
        assertEquals(originalTarget, table.forcedTarget(subject, 0));
    }

    @Test
    void partiallyReplacedMultiDomainLeaseRemainsOpenForItsOtherDomains() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var directives = freeze();
        directives.put(ControlDomain.TARGET, new ControlDirective.ForceTarget(UUID.randomUUID()));
        var original = table.add(input(controller, subject, SOURCE_A, 1, 100, directives));
        var replacementTarget = UUID.randomUUID();

        var targetReplacement = table.add(input(
                controller,
                subject,
                SOURCE_A,
                1,
                100,
                target(replacementTarget)
        ));

        assertTrue(table.isActive(original));
        assertTrue(table.isActive(targetReplacement));
        assertTrue(table.isFrozen(subject, 0));
        assertEquals(replacementTarget, table.forcedTarget(subject, 0));

        table.remove(original);

        assertFalse(table.isActive(original));
        assertTrue(table.isActive(targetReplacement));
        assertFalse(table.isFrozen(subject, 0));
        assertEquals(replacementTarget, table.forcedTarget(subject, 0));
    }

    @Test
    void capabilitySelectionFallsBackFromAnUnsupportedSpecializedAdapter() {
        var specialized = registration("specialized", 100);
        var generic = registration("generic", Integer.MIN_VALUE);

        var resolution = MentalControlRuntime.selectAdapter(
                ControlCapability.FREEZE_AI,
                List.of(
                        candidate(specialized, ControlSupport.UNSUPPORTED),
                        candidate(generic, ControlSupport.BEST_EFFORT)
                ),
                List.of()
        );

        assertTrue(resolution.evaluation().supported());
        assertEquals(generic.id(), resolution.evaluation().adapterId().orElseThrow());
    }

    @Test
    void equallyPreferredCapabilityAdaptersAreRejectedAsAmbiguous() {
        var first = registration("first", 10);
        var second = registration("second", 10);

        var resolution = MentalControlRuntime.selectAdapter(
                ControlCapability.RELATION_CONTROL,
                List.of(
                        candidate(first, ControlSupport.FULL),
                        candidate(second, ControlSupport.BEST_EFFORT)
                ),
                List.of()
        );

        assertFalse(resolution.evaluation().supported());
        assertEquals(ControlRejectionReason.AMBIGUOUS_ADAPTER, resolution.evaluation().reason());
    }

    @Test
    void adapterFailureAtTheWinningPriorityPreventsUntrustedFallback() {
        var broken = registration("broken", 100);
        var generic = registration("generic", 0);

        var resolution = MentalControlRuntime.selectAdapter(
                ControlCapability.FORCE_TARGET,
                List.of(candidate(generic, ControlSupport.BEST_EFFORT)),
                List.of(broken)
        );

        assertFalse(resolution.evaluation().supported());
        assertEquals(ControlRejectionReason.ADAPTER_ERROR, resolution.evaluation().reason());
    }

    @Test
    void impressionGuardUsesOnlyTheEffectiveAllianceController() {
        var table = new MentalControlRuntime.LeaseTable();
        var subject = UUID.randomUUID();
        var firstController = UUID.randomUUID();
        var winningController = UUID.randomUUID();
        table.add(input(firstController, subject, SOURCE_A, 10, 100, impression()));
        table.add(input(winningController, subject, SOURCE_B, 20, 100, impression()));

        assertFalse(MentalControlRuntime.canAssignGuardianTarget(
                table,
                firstController,
                subject,
                0
        ));
        assertTrue(MentalControlRuntime.canAssignGuardianTarget(
                table,
                winningController,
                subject,
                0
        ));
    }

    @Test
    void explicitForceTargetAlwaysBlocksImpressionGuardRedirection() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        table.add(input(controller, subject, SOURCE_A, 100, 100, impression()));
        table.add(input(controller, subject, SOURCE_B, -100, 100, target(UUID.randomUUID())));

        assertFalse(MentalControlRuntime.canAssignGuardianTarget(
                table,
                controller,
                subject,
                0
        ));
    }

    @Test
    void explicitTargetOutranksDerivedImpressionGuardLease() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var guardTarget = UUID.randomUUID();
        var explicitTarget = UUID.randomUUID();
        table.add(input(
                controller,
                subject,
                MentalControlRuntime.IMPRESSION_GUARD_SOURCE,
                MentalControlRuntime.IMPRESSION_GUARD_PRIORITY,
                100,
                target(guardTarget)
        ));
        table.add(input(controller, subject, SOURCE_A, -100, 100, target(explicitTarget)));

        assertEquals(explicitTarget, table.forcedTarget(subject, 0));
    }

    @Test
    void impressionPacificationDoesNotTurnOutsidersIntoAllies() {
        var table = new MentalControlRuntime.LeaseTable();
        var whitelist = new MentalControlRuntime.TargetWhitelist();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var outsider = UUID.randomUUID();
        table.add(input(controller, subject, SOURCE_A, 100, 200, impression()));

        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, outsider, 0)
        );
        assertEquals(
                AttackDecision.PASS,
                MentalControlRuntime.relationDecision(table, whitelist, subject, outsider, 0)
        );
        assertEquals(
                AttackDecision.PASS,
                MentalControlRuntime.relationDecision(table, whitelist, outsider, subject, 0)
        );
    }

    @Test
    void retaliationAuthorizationIsExactAndExpires() {
        var table = new MentalControlRuntime.LeaseTable();
        var whitelist = new MentalControlRuntime.TargetWhitelist();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var aggressor = UUID.randomUUID();
        var unrelated = UUID.randomUUID();
        var relationLease = table.add(input(controller, subject, SOURCE_A, 100, 200, impression()));
        whitelist.authorize(subject, aggressor, relationLease, 100);

        assertEquals(
                AttackDecision.ALLOW,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, aggressor, 0)
        );
        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, unrelated, 0)
        );
        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, aggressor, 100)
        );
    }

    @Test
    void forceTargetPreemptsAuthorizedRetaliation() {
        var table = new MentalControlRuntime.LeaseTable();
        var whitelist = new MentalControlRuntime.TargetWhitelist();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var aggressor = UUID.randomUUID();
        var forcedTarget = UUID.randomUUID();
        var relationLease = table.add(input(controller, subject, SOURCE_A, 100, 200, impression()));
        whitelist.authorize(subject, aggressor, relationLease, 100);
        table.add(input(controller, subject, SOURCE_B, 10, 200, target(forcedTarget)));

        assertEquals(
                AttackDecision.ALLOW,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, forcedTarget, 0)
        );
        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, aggressor, 0)
        );
    }

    @Test
    void pathControlDeniesAutonomousTargetsButExplicitTargetStillWins() {
        var table = new MentalControlRuntime.LeaseTable();
        var whitelist = new MentalControlRuntime.TargetWhitelist();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var outsider = UUID.randomUUID();
        var explicitTarget = UUID.randomUUID();
        table.add(input(controller, subject, SOURCE_A, 100, 200, path(UUID.randomUUID())));

        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, outsider, 0)
        );

        table.add(input(controller, subject, SOURCE_B, 100, 200, target(explicitTarget)));
        assertEquals(
                AttackDecision.ALLOW,
                MentalControlRuntime.controlledAttackDecision(
                        table, whitelist, subject, explicitTarget, 0)
        );
        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, outsider, 0)
        );
    }

    @Test
    void replacingAllianceInvalidatesOldRetaliationAuthorization() {
        var table = new MentalControlRuntime.LeaseTable();
        var whitelist = new MentalControlRuntime.TargetWhitelist();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var aggressor = UUID.randomUUID();
        var original = table.add(input(controller, subject, SOURCE_A, 100, 200, impression()));
        whitelist.authorize(subject, aggressor, original, 100);
        table.add(input(controller, subject, SOURCE_A, 100, 200, impression()));

        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(table, whitelist, subject, aggressor, 0)
        );
    }

    @Test
    void replacingOrClosingAllianceRemovesItsDerivedGuardianTarget() {
        var table = new MentalControlRuntime.LeaseTable();
        var controller = UUID.randomUUID();
        var subject = UUID.randomUUID();
        var aggressor = UUID.randomUUID();
        var originalRelation = table.add(input(
                controller,
                subject,
                SOURCE_A,
                100,
                200,
                impression()
        ));
        var originalGuard = table.add(guardianInput(
                controller,
                subject,
                aggressor,
                originalRelation
        ));

        var replacementRelation = table.add(input(
                controller,
                subject,
                SOURCE_A,
                100,
                200,
                impression()
        ));
        table.removeInvalidImpressionGuards(java.util.Set.of(subject), 0);

        assertFalse(table.isActive(originalGuard));
        assertNull(table.forcedTarget(subject, 0));

        var replacementGuard = table.add(guardianInput(
                controller,
                subject,
                aggressor,
                replacementRelation
        ));
        table.remove(replacementRelation);
        table.removeInvalidImpressionGuards(java.util.Set.of(subject), 0);

        assertFalse(table.isActive(replacementGuard));
        assertNull(table.forcedTarget(subject, 0));
    }

    @Test
    void explicitRosterMisidentificationCanTriggerOtherGuardians() {
        var table = new MentalControlRuntime.LeaseTable();
        var whitelist = new MentalControlRuntime.TargetWhitelist();
        var controller = UUID.randomUUID();
        var aggressor = UUID.randomUUID();
        var guardian = UUID.randomUUID();
        table.add(input(controller, aggressor, SOURCE_A, 100, 200, impression()));
        table.add(input(controller, guardian, SOURCE_A, 100, 200, impression()));

        assertEquals(
                AttackDecision.DENY,
                MentalControlRuntime.controlledAttackDecision(
                        table,
                        whitelist,
                        aggressor,
                        controller,
                        0
                )
        );

        table.add(input(controller, aggressor, SOURCE_B, 100, 200, target(controller)));

        assertEquals(
                AttackDecision.ALLOW,
                MentalControlRuntime.controlledAttackDecision(
                        table,
                        whitelist,
                        aggressor,
                        controller,
                        0
                )
        );
        assertTrue(MentalControlRuntime.canAssignGuardianTarget(
                table,
                controller,
                guardian,
                0
        ));
    }

    private static MentalControlRuntime.LeaseInput input(
            UUID controller,
            UUID subject,
            Identifier source,
            int priority,
            long expiresAt,
            EnumMap<ControlDomain, ControlDirective> directives
    ) {
        return new MentalControlRuntime.LeaseInput(
                controller,
                subject,
                source,
                priority,
                expiresAt,
                DIMENSION,
                DIMENSION,
                directives
        );
    }

    private static EnumMap<ControlDomain, ControlDirective> target(UUID target) {
        var directives = new EnumMap<ControlDomain, ControlDirective>(ControlDomain.class);
        directives.put(ControlDomain.TARGET, new ControlDirective.ForceTarget(target));
        return directives;
    }

    private static MentalControlRuntime.LeaseInput guardianInput(
            UUID controller,
            UUID subject,
            UUID target,
            UUID relationLeaseId
    ) {
        return new MentalControlRuntime.LeaseInput(
                controller,
                subject,
                MentalControlRuntime.IMPRESSION_GUARD_SOURCE,
                MentalControlRuntime.IMPRESSION_GUARD_PRIORITY,
                100,
                DIMENSION,
                DIMENSION,
                target(target),
                relationLeaseId
        );
    }

    private static EnumMap<ControlDomain, ControlDirective> freeze() {
        var directives = new EnumMap<ControlDomain, ControlDirective>(ControlDomain.class);
        var freeze = new ControlDirective.FreezeAi();
        directives.put(ControlDomain.MOVEMENT, freeze);
        directives.put(ControlDomain.ACTION, freeze);
        return directives;
    }

    private static EnumMap<ControlDomain, ControlDirective> path(UUID target) {
        var directives = new EnumMap<ControlDomain, ControlDirective>(ControlDomain.class);
        var moveTo = new ControlDirective.MoveTo(target);
        directives.put(ControlDomain.MOVEMENT, moveTo);
        directives.put(ControlDomain.ACTION, moveTo);
        return directives;
    }

    private static EnumMap<ControlDomain, ControlDirective> impression() {
        var directives = new EnumMap<ControlDomain, ControlDirective>(ControlDomain.class);
        directives.put(ControlDomain.RELATION, new ControlDirective.ImpressionAlliance());
        return directives;
    }

    private static MentalControlRuntime.AdapterRegistration registration(String path, int priority) {
        return new MentalControlRuntime.AdapterRegistration(
                Identifier.fromNamespaceAndPath("academy", path),
                priority,
                new MentalControlAdapter() {
                    @Override
                    public boolean matches(net.minecraft.world.entity.LivingEntity subject) {
                        return true;
                    }

                    @Override
                    public ControlSupport support(
                            net.minecraft.world.entity.LivingEntity subject,
                            ControlCapability capability
                    ) {
                        return ControlSupport.UNSUPPORTED;
                    }

                    @Override
                    public ControlBinding activate(ControlContext context, ControlDirective directive) {
                        return ControlBinding.noop();
                    }
                }
        );
    }

    private static MentalControlRuntime.CapabilityCandidate candidate(
            MentalControlRuntime.AdapterRegistration registration,
            ControlSupport support
    ) {
        return new MentalControlRuntime.CapabilityCandidate(
                registration,
                support,
                support.isSupported()
                        ? ControlRejectionReason.SUPPORTED
                        : ControlRejectionReason.UNSUPPORTED_CAPABILITY
        );
    }
}
