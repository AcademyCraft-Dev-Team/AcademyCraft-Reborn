package org.academy.internal.common.ability.darkmatter.skills.lv1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterDisassembleTest {
    @Test
    void alphaPowerExpandsTheConnectedSameBlockTargetBudget() {
        assertEquals(1, DarkmatterDisassemble.Server.alphaChainTargetLimit(0.0f));
        assertEquals(3, DarkmatterDisassemble.Server.alphaChainTargetLimit(1.0f));
        assertEquals(7, DarkmatterDisassemble.Server.alphaChainTargetLimit(3.0f));
        assertEquals(11, DarkmatterDisassemble.Server.alphaChainTargetLimit(5.0f));
    }

    @Test
    void connectedTraversalOnlyFollowsFaceAdjacentMatchingBlocks() {
        var origin = BlockPos.ZERO;
        var matching = Set.of(
                origin,
                origin.east(),
                origin.east(2),
                origin.above(),
                origin.offset(1, 1, 0),
                origin.offset(4, 0, 0)
        );

        var targets = DarkmatterDisassemble.Server.connectedTargets(
                origin,
                11,
                matching::contains
        );

        assertEquals(5, targets.size());
        assertEquals(origin, targets.getFirst());
    }

    @Test
    void hitFaceMakesVisiblePlaneWinBeforeHiddenDepth() {
        assertEquals(
                List.of(
                        Direction.NORTH,
                        Direction.SOUTH,
                        Direction.WEST,
                        Direction.EAST,
                        Direction.UP,
                        Direction.DOWN),
                DarkmatterDisassemble.Server.prioritizedDirections(
                        Direction.UP));
        var targets = DarkmatterDisassemble.Server.connectedTargets(
                BlockPos.ZERO,
                5,
                DarkmatterDisassemble.Server.prioritizedDirections(
                        Direction.UP),
                ignored -> true);
        assertEquals(List.of(
                BlockPos.ZERO,
                BlockPos.ZERO.north(),
                BlockPos.ZERO.south(),
                BlockPos.ZERO.west(),
                BlockPos.ZERO.east()), targets);
    }

    @Test
    void castRangeAndGammaFieldUseTheNewFixedContract() {
        assertEquals(32.0, DarkmatterDisassemble.Server.maximumRange(0.0f, 0), 0.0001);
        assertEquals(32.0, DarkmatterDisassemble.Server.maximumRange(5.0f, 3), 0.0001);
        assertEquals(2.0, DarkmatterDisassemble.Server.gammaRadius(1.0f, 1, 1), 0.0001);
        assertEquals(2.0, DarkmatterDisassemble.Server.gammaRadius(1.0f, 3, 1), 0.0001);
        assertEquals(2.0, DarkmatterDisassemble.Server.gammaRadius(1.0f, 3, 2), 0.0001);
    }

    @Test
    void alphaAndBetaControlTheirExactCombatAndBlockFormulas() {
        assertEquals(0.10f, DarkmatterDisassemble.Server.penetration(1.0f, 0), 0.0001f);
        assertEquals(0.50f, DarkmatterDisassemble.Server.penetration(5.0f, 3), 0.0001f);
        assertEquals(0.0f, DarkmatterDisassemble.Server.penetration(0.0f, 3), 0.0001f);
        assertEquals(60, DarkmatterDisassemble.Server.corrosionTicks(1.0f, 2));
        assertEquals(3.0, DarkmatterDisassemble.Server.damageRadius(1.0f), 0.0001);
        assertEquals(7.0, DarkmatterDisassemble.Server.damageRadius(3.0f), 0.0001);
        assertEquals(11.0, DarkmatterDisassemble.Server.damageRadius(5.0f), 0.0001);
        assertEquals(6.0f, DarkmatterDisassemble.Server.damage(1.0f), 0.0001f);
        assertEquals(10.0f, DarkmatterDisassemble.Server.damage(3.0f), 0.0001f);
        assertEquals(14.0f, DarkmatterDisassemble.Server.damage(5.0f), 0.0001f);
        assertEquals(1, DarkmatterDisassemble.Server.fortuneLevel(1.0f));
        assertEquals(3, DarkmatterDisassemble.Server.fortuneLevel(3.0f));
        assertEquals(5, DarkmatterDisassemble.Server.fortuneLevel(5.0f));
        assertEquals(30.0f, DarkmatterDisassemble.Server.maximumHardness(2.0f, 2), 0.0001f);
    }
}
