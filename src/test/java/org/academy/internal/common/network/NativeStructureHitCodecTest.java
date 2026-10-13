package org.academy.internal.common.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureHitCodecTest {
    @Test
    void sharedValueCodecLeavesEnvelopeBytesToItsCallerAndPreservesLocalAxes() {
        var hit = hit();
        var buffer = Unpooled.buffer();
        try {
            NativeStructureHitCodec.CODEC.encode(buffer, hit);
            buffer.writeLong(0x123456789abcdefL);
            var copy = NativeStructureHitCodec.CODEC.decode(buffer);
            assertEquals(hit.block(), copy.block());
            assertEquals(hit.runtimeId(), copy.runtimeId());
            assertEquals(hit.geometryRevision(), copy.geometryRevision());
            assertEquals(hit.localFace(), copy.localFace());
            assertEquals(hit.localPoint(), copy.localPoint());
            assertEquals(hit.worldPoint(), copy.worldPoint());
            assertTrue(copy.pose().localToWorldPoint(copy.localPoint()).distanceTo(copy.worldPoint()) < 1e-10);
            assertEquals(0x123456789abcdefL, buffer.readLong());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test
    void miningContainsTheValueInsteadOfEmbeddingAUseRequestOrItsHand() {
        var value = Unpooled.buffer();
        var mine = Unpooled.buffer();
        var use = Unpooled.buffer();
        try {
            var hit = hit();
            NativeStructureHitCodec.CODEC.encode(value, hit);
            NativeStructureMinePacket.CODEC.encode(mine, new NativeStructureMinePacket(NativeStructureMinePacket.Action.HOLD, 4, hit));
            NativeStructureUsePacket.CODEC.encode(use, new NativeStructureUsePacket(5, hit, InteractionHand.OFF_HAND));
            assertEquals(value.readableBytes() + 9, mine.readableBytes());
            assertEquals(value.readableBytes() + 9, use.readableBytes());
            assertEquals(value, mine.slice(9, value.readableBytes()));
            assertEquals(value, use.slice(8, value.readableBytes()));
            assertEquals(1, use.getUnsignedByte(use.writerIndex() - 1));
        } finally { value.release(); mine.release(); use.release(); }
    }

    private static StructureHit hit() {
        var reference = new StructureRef(new UUID(1, 2), Identifier.withDefaultNamespace("overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 7);
        var pose = new StructurePose(new Vec3(18, 80, -12), new Quaterniond().rotationXYZ(.41, -.73, .22));
        var point = new Vec3(-2, 81.25, 1.5);
        return new StructureHit(new SpaceBlockRef(reference, new BlockPos(-2, 81, 1)), new UUID(3, 4),
                23, pose, Direction.WEST, point, pose.localToWorldPoint(point), false);
    }
}
