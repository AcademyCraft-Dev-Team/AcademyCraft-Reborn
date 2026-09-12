package org.academy.internal.common.music;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 共享播放队列快照（音乐室队列/全服点播队列），revision 随每次变更自增，客户端据此刷新UI喵。
 */
public record QueueSnapshot(List<QueueEntry> entries, int revision) {
    private static final int MAX_ENTRIES = 256;

    public QueueSnapshot {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            entries = entries.subList(0, MAX_ENTRIES);
        }
    }

    public record QueueEntry(SharedTrackEntry entry, String requesterName) {
        public QueueEntry {
            Objects.requireNonNull(entry, "entry");
            requesterName = requesterName == null ? "" : requesterName;
        }

        public static final StreamCodec<ByteBuf, QueueEntry> CODEC = StreamCodec.of(
                (buf, queueEntry) -> {
                    SharedTrackEntry.CODEC.encode(buf, queueEntry.entry);
                    ByteBufCodecs.STRING_UTF8.encode(buf, queueEntry.requesterName);
                },
                buf -> new QueueEntry(
                        SharedTrackEntry.CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public QueueEntry first() {
        return entries.get(0);
    }

    public static final StreamCodec<ByteBuf, QueueSnapshot> CODEC = StreamCodec.of(
            (buf, snapshot) -> {
                ByteBufCodecs.VAR_INT.encode(buf, snapshot.revision);
                ByteBufCodecs.VAR_INT.encode(buf, snapshot.entries.size());
                for (var queueEntry : snapshot.entries) {
                    QueueEntry.CODEC.encode(buf, queueEntry);
                }
            },
            buf -> {
                var revision = ByteBufCodecs.VAR_INT.decode(buf);
                var size = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_ENTRIES);
                var entries = new ArrayList<QueueEntry>(size);
                for (var index = 0; index < size; index++) {
                    entries.add(QueueEntry.CODEC.decode(buf));
                }
                return new QueueSnapshot(entries, revision);
            }
    );
}
