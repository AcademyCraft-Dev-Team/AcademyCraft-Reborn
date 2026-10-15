package org.academy.internal.common.structure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe replacement of a small bounded file: the payload is written to a sibling temporary
 * file, forced to disk and then moved over the target. Windows file filters can briefly deny that
 * move with {@link java.nio.file.AccessDeniedException}, so transient file-system failures are
 * retried with a bounded backoff (5 attempts, at most 150 ms) before falling back to a non-atomic
 * delete-and-move. The fallback runs only after every atomic attempt failed and briefly leaves the
 * target absent. Failures are never swallowed: when the fallback already deleted the target the
 * temporary file is retained as the only surviving copy and its path is attached to the raised
 * failure. Orphan temporary files are never treated as committed data.
 */
public final class DurableFile {
    private static final int REPLACE_ATTEMPTS = 5;
    private static final long REPLACE_BACKOFF_MILLIS = 10;
    private static final long REPLACE_BACKOFF_LIMIT_MILLIS = 250;

    /** File-operation seam; tests inject transient replace failures around the atomic move. */
    public interface FileOperations {
        FileOperations DEFAULT = (source, target, atomic) -> {
            if (atomic) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            // Non-atomic fallback: the target is briefly absent between delete and move.
            Files.deleteIfExists(target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        };

        void move(Path source, Path target, boolean atomic) throws IOException;
    }

    private DurableFile() {
    }

    /** Replaces the target with the payload, cleaning up the temporary file on success and failure. */
    public static void writeAtomically(Path target, byte[] data, FileOperations fileOperations) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        IOException failure = null;
        var keepTemporary = false;
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = ByteBuffer.wrap(data);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try {
                replace(temporary, target, fileOperations);
            } catch (IOException replaceFailure) {
                failure = replaceFailure;
                keepTemporary = !Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            }
        } catch (IOException writeFailure) {
            failure = writeFailure;
        }
        if (failure == null) {
            Files.deleteIfExists(temporary);
            return;
        }
        if (keepTemporary) {
            failure.addSuppressed(new IOException("Retained the failed replacement record at " + temporary));
        } else {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        throw failure;
    }

    /**
     * Replaces the target with the temporary file. Transient sharing denials are retried with a
     * bounded exponential backoff (5 attempts, at most 150 ms); once exhausted, or when atomic
     * moves are unsupported, the target is replaced non-atomically (delete, then move). Failures
     * are always rethrown, with the last atomic attempt attached.
     */
    private static void replace(Path temporary, Path target, FileOperations fileOperations) throws IOException {
        IOException last = null;
        long delay = REPLACE_BACKOFF_MILLIS;
        for (var attempt = 0; attempt < REPLACE_ATTEMPTS; attempt++) {
            try {
                fileOperations.move(temporary, target, true);
                return;
            } catch (AtomicMoveNotSupportedException unsupported) {
                last = unsupported;
                break;
            } catch (FileSystemException transientFailure) {
                last = transientFailure;
            }
            if (attempt + 1 == REPLACE_ATTEMPTS) break;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                var failure = new IOException("Interrupted while replacing a durable file", interrupted);
                if (last != null) failure.addSuppressed(last);
                throw failure;
            }
            delay = Math.min(delay * 2, REPLACE_BACKOFF_LIMIT_MILLIS);
        }
        try {
            fileOperations.move(temporary, target, false);
        } catch (IOException fallbackFailure) {
            if (last != null && last != fallbackFailure) fallbackFailure.addSuppressed(last);
            throw fallbackFailure;
        }
    }
}
