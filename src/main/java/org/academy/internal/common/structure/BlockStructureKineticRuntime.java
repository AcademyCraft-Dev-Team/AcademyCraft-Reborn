package org.academy.internal.common.structure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureForceProvider;
import org.academy.api.common.structure.BlockStructureImpact;
import org.academy.api.common.structure.BlockStructureImpactHandler;
import org.academy.api.common.structure.BlockStructureKineticHandle;
import org.academy.api.common.structure.BlockStructureKineticOptions;
import org.academy.api.common.structure.BlockStructureWorldImpact;
import org.academy.api.common.structure.BlockStructureWorldImpactHandler;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-owned lifecycle for transient structure propulsion and impact callbacks. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class BlockStructureKineticRuntime {
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private BlockStructureKineticRuntime() {
    }

    public static BlockStructureKineticHandle start(
            BlockStructure structure,
            Entity controller,
            BlockStructureForceProvider forceProvider,
            BlockStructureKineticOptions options,
            BlockStructureImpactHandler impactHandler
    ) {
        return startSession(
                structure,
                controller,
                forceProvider,
                options,
                impactHandler,
                null,
                false
        );
    }

    public static BlockStructureKineticHandle launch(
            BlockStructure structure,
            Entity controller,
            Vec3 initialVelocity,
            BlockStructureKineticOptions options,
            BlockStructureImpactHandler impactHandler,
            BlockStructureWorldImpactHandler worldImpactHandler
    ) {
        Objects.requireNonNull(initialVelocity, "initialVelocity");
        Objects.requireNonNull(worldImpactHandler, "worldImpactHandler");
        if (!finite(initialVelocity) || initialVelocity.lengthSqr() <= 1.0e-8) {
            throw new IllegalArgumentException("initialVelocity must be finite and non-zero");
        }
        var handle = startSession(
                structure,
                controller,
                (_, _) -> Vec3.ZERO,
                options,
                impactHandler,
                worldImpactHandler,
                true
        );
        structure.setVelocity(clamp(initialVelocity, options.maximumSpeed()));
        return handle;
    }

    private static BlockStructureKineticHandle startSession(
            BlockStructure structure,
            Entity controller,
            BlockStructureForceProvider forceProvider,
            BlockStructureKineticOptions options,
            BlockStructureImpactHandler impactHandler,
            BlockStructureWorldImpactHandler worldImpactHandler,
            boolean gravityEnabledDuring
    ) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(forceProvider, "forceProvider");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(impactHandler, "impactHandler");
        if (!(structure.asEntity() instanceof BlockStructureEntity entity)
                || entity.level().isClientSide()
                || entity.level() != controller.level()
                || entity.isRemoved()
                || controller.isRemoved()) {
            throw new IllegalArgumentException(
                    "Structure and controller must be active in the same server level");
        }
        var previous = ACTIVE.remove(entity.getUUID());
        if (previous != null) previous.finish();
        var session = new Session(
                entity, controller, forceProvider, options, impactHandler,
                worldImpactHandler);
        ACTIVE.put(entity.getUUID(), session);
        entity.setGravityEnabled(gravityEnabledDuring);
        return session;
    }

    public static boolean isActive(BlockStructure structure) {
        return structure != null
                && ACTIVE.get(structure.asEntity().getUUID()) instanceof Session session
                && session.structure == structure.asEntity()
                && session.active;
    }

    public static void stop(BlockStructure structure) {
        if (structure == null) return;
        var session = ACTIVE.remove(structure.asEntity().getUUID());
        if (session != null) session.finish();
    }

    public static void beginTick(BlockStructureEntity structure) {
        var session = session(structure);
        if (session == null) return;
        if (!session.usable()) {
            remove(session);
            return;
        }
        Vec3 force;
        try {
            force = session.forceProvider.force(structure, session.controller);
        } catch (RuntimeException ignored) {
            remove(session);
            return;
        }
        if (!finite(force)) {
            remove(session);
            return;
        }
        var velocity = structure.velocity().add(force.scale(1.0 / structure.mass()));
        session.lastRequestedVelocity = clamp(velocity, session.options.maximumSpeed());
        structure.setVelocity(session.lastRequestedVelocity);
    }

    public static void handleImpact(BlockStructureImpact impact) {
        if (!(impact.structure().asEntity() instanceof BlockStructureEntity structure)) return;
        var session = session(structure);
        if (session == null || impact.controller() != session.controller) return;
        var now = structure.level().getGameTime();
        var previous = session.lastImpactTicks.get(impact.target().getUUID());
        if (previous != null
                && now - previous < session.options.impactCooldownTicks()) return;
        session.lastImpactTicks.put(impact.target().getUUID(), now);
        try {
            session.impactHandler.onImpact(impact);
        } catch (RuntimeException ignored) {
            remove(session);
        }
    }

    public static Entity controller(BlockStructureEntity structure) {
        var session = session(structure);
        return session == null ? null : session.controller;
    }

    public static boolean preventsSettlement(BlockStructureEntity structure) {
        var session = session(structure);
        return session != null && session.options.preventSettlementWhileActive();
    }

    public static void endTick(BlockStructureEntity structure) {
        var session = session(structure);
        if (session == null) return;
        if (session.worldImpactHandler != null
                && (structure.horizontalCollision || structure.verticalCollision)) {
            var impact = new BlockStructureWorldImpact(
                    structure,
                    session.controller,
                    structure.getBoundingBox().getCenter(),
                    session.lastRequestedVelocity,
                    structure.horizontalCollision,
                    structure.verticalCollision
            );
            remove(session);
            try {
                session.worldImpactHandler.onImpact(impact);
            } catch (RuntimeException ignored) {
                // The propulsion session is already closed; a failed optional effect must not
                // leave a launched structure permanently controlled.
            }
            return;
        }
        if (--session.remainingTicks <= 0 || !session.usable()) remove(session);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private static Session session(BlockStructureEntity structure) {
        var session = ACTIVE.get(structure.getUUID());
        return session != null && session.structure == structure && session.active
                ? session
                : null;
    }

    private static void remove(Session session) {
        ACTIVE.remove(session.structure.getUUID(), session);
        session.finish();
    }

    private static Vec3 clamp(Vec3 velocity, double maximumSpeed) {
        if (!finite(velocity)) return Vec3.ZERO;
        var length = velocity.length();
        return length > maximumSpeed
                ? velocity.scale(maximumSpeed / length)
                : velocity;
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static final class Session implements BlockStructureKineticHandle {
        private final BlockStructureEntity structure;
        private final Entity controller;
        private final BlockStructureForceProvider forceProvider;
        private final BlockStructureKineticOptions options;
        private final BlockStructureImpactHandler impactHandler;
        private final BlockStructureWorldImpactHandler worldImpactHandler;
        private final Map<UUID, Long> lastImpactTicks = new HashMap<>();
        private Vec3 lastRequestedVelocity = Vec3.ZERO;
        private int remainingTicks;
        private boolean active = true;

        private Session(
                BlockStructureEntity structure,
                Entity controller,
                BlockStructureForceProvider forceProvider,
                BlockStructureKineticOptions options,
                BlockStructureImpactHandler impactHandler,
                BlockStructureWorldImpactHandler worldImpactHandler
        ) {
            this.structure = structure;
            this.controller = controller;
            this.forceProvider = forceProvider;
            this.options = options;
            this.impactHandler = impactHandler;
            this.worldImpactHandler = worldImpactHandler;
            remainingTicks = options.durationTicks();
        }

        private boolean usable() {
            return active
                    && !structure.isRemoved()
                    && !controller.isRemoved()
                    && controller.isAlive()
                    && structure.level() == controller.level();
        }

        private void finish() {
            if (!active) return;
            active = false;
            if (!structure.isRemoved()) {
                structure.setGravityEnabled(options.gravityAfter());
            }
            lastImpactTicks.clear();
        }

        @Override
        public boolean active() {
            return BlockStructureKineticRuntime.session(structure) == this;
        }

        @Override
        public void close() {
            if (ACTIVE.remove(structure.getUUID(), this)) finish();
        }
    }
}
