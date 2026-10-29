package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureDamage;
import org.academy.api.server.ability.AbilityEffectPolicy;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.StructureCellPayload;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.util.List;
import java.util.Optional;

/** Rechecks an observed hard contact without removing the obstruction or loading content. */
public final class DamageContactWitness {
    private DamageContactWitness() {}

    public static Optional<List<AABB>> resolve(ServerLevel host, ServerPlayer actor, StructureDamage.Cell cell, AABB area) {
        if (!host.getServer().isSameThread() || actor.level() != host || !actor.isAlive() || actor.hasDisconnected()
                || !DestroyBlocksSetting.canDestroyBlocks(actor)
                || AbilityEffectPolicy.blockDestruction(host) == AbilityEffectPolicy.Decision.DENY) return Optional.empty();
        DamageDomain domain = null;
        BlockPos local;
        if (cell.worldPosition().isPresent()) local = cell.worldPosition().orElseThrow();
        else {
            var selected = cell.structurePosition().orElseThrow();
            local = selected.localPosition();
            for (var candidate : StructureBackendRouter.damageDomains(host, area.inflate(2), 256)) {
                if (candidate.handle().reference().equals(selected.structure())) { domain = candidate; break; }
            }
            if (domain == null || !domain.current() || domain.contentRevision() != cell.expectedRevision()) return Optional.empty();
        }
        var level = domain == null ? host : domain.storage();
        var position = domain == null ? local : domain.position(local);
        if (level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null
                || domain != null && domain.chunk(position) == null) return Optional.empty();
        var pose = domain == null ? StructurePose.identity(Vec3.ZERO) : domain.handle().pose();
        var world = BlockPos.containing(pose.localToWorldPoint(Vec3.atCenterOf(local)));
        var payload = StructureCellPayload.read(level, position);
        String digest = Integer.toHexString(payload.state().hashCode()) + ":" + StructureCellPayload.digest(payload);
        if (!digest.equals(cell.expectedStateDigest()) || !host.mayInteract(actor, world)
                || !host.getWorldBorder().isWithinBounds(world) || actor.isSpectator() || !actor.getAbilities().mayBuild) return Optional.empty();
        if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, position, payload.state(), actor)).isCanceled()) return Optional.empty();
        if (!StructureCellPayload.read(level, position).equals(payload) || !host.mayInteract(actor, world)
                || !DestroyBlocksSetting.canDestroyBlocks(actor)) return Optional.empty();
        var shape = payload.state().getCollisionShape(new DamageShapeView(level, domain), position);
        return Optional.of(shape.toAabbs().stream().map(box -> NativeStructureCollisionBoxes.transformBox(box.move(local), pose)).toList());
    }
}
