package org.academy.internal.common.music;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueSnapshotTest {
    @Test
    void codecRoundTrip() {
        var entry = new SharedTrackEntry("qq", "ABC123", "晴天", "周杰伦", 269, true, "https://example.com/pic.jpg");
        var snapshot = new QueueSnapshot(List.of(new QueueSnapshot.QueueEntry(entry, "Steve")), 7);
        var buf = Unpooled.buffer();
        QueueSnapshot.CODEC.encode(buf, snapshot);
        var decoded = QueueSnapshot.CODEC.decode(buf);
        assertEquals(snapshot, decoded);
        buf.release();
    }

    @Test
    void emptyQueueRoundTrip() {
        var snapshot = new QueueSnapshot(List.of(), 3);
        var buf = Unpooled.buffer();
        QueueSnapshot.CODEC.encode(buf, snapshot);
        assertEquals(snapshot, QueueSnapshot.CODEC.decode(buf));
        assertTrue(snapshot.isEmpty());
        buf.release();
    }

    @Test
    void oversizedListTruncated() {
        var entry = new SharedTrackEntry("qq", "X", "t", "a", 1, false, "");
        var entries = new ArrayList<QueueSnapshot.QueueEntry>();
        for (var index = 0; index < 300; index++) {
            entries.add(new QueueSnapshot.QueueEntry(entry, "p" + index));
        }
        assertEquals(256, new QueueSnapshot(entries, 1).entries().size());
    }

    @Test
    void nullRequesterSanitized() {
        var entry = new SharedTrackEntry("netease", "1", "t", "a", 1, false, "");
        var queueEntry = new QueueSnapshot.QueueEntry(entry, null);
        assertEquals("", queueEntry.requesterName());
    }
}
