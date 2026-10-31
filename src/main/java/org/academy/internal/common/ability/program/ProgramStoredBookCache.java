package org.academy.internal.common.ability.program;

import com.google.gson.JsonElement;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Pure decode successes only; no category validation, graph migration, capabilities or execution. */
final class ProgramStoredBookCache {
    static final int MAX_ENTRIES = 64;
    static final long MAX_WEIGHT = 4L * 1024 * 1024;
    private final int maxEntries;
    private final long maxWeight;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private long weight, hits, misses, bypasses, evictions;

    ProgramStoredBookCache() { this(MAX_ENTRIES, MAX_WEIGHT); }

    ProgramStoredBookCache(int maxEntries, long maxWeight) {
        if (maxEntries < 1 || maxWeight < 1) throw new IllegalArgumentException("Book cache budgets must be positive");
        this.maxEntries = maxEntries; this.maxWeight = maxWeight;
    }

    synchronized ProgramBookCodec.DecodeResult decode(String encoded, Supplier<ProgramBookCodec.DecodeResult> loader) {
        var known = entries.get(encoded);
        if (known != null) {
            hits++;
            ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.BOOK_DECODE_HIT);
            return known.decoded;
        }
        misses++;
        ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.BOOK_DECODE_MISS);
        var decoded = loader.get();
        if (!decoded.valid()) {
            bypass(); return decoded;
        }
        long candidateWeight = entryWeight(encoded, decoded.book(), maxWeight);
        if (candidateWeight > maxWeight) { bypass(); return decoded; }
        while (!entries.isEmpty() && (entries.size() >= maxEntries || weight + candidateWeight > maxWeight)) {
            var iterator = entries.entrySet().iterator();
            weight -= iterator.next().getValue().weight;
            iterator.remove(); evictions++;
            ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.BOOK_DECODE_EVICT);
        }
        entries.put(encoded, new Entry(decoded, candidateWeight)); weight += candidateWeight;
        return decoded;
    }

    private void bypass() {
        bypasses++;
        ProgramAutomaticMetrics.count(ProgramAutomaticMetrics.Counter.BOOK_DECODE_BYPASS);
    }

    /** Conservative retained-payload budget, not an exact JVM heap measurement. Stops once oversized. */
    private static long entryWeight(String encoded, ProgramBook book, long limit) {
        long result = 256L + 4L * encoded.length() + 64L * book.slots().size();
        for (var slot : book.slots()) {
            if (result > limit) return result;
            var program = slot.program();
            if (program == null) continue;
            result += 256L + 4L * (program.name().length() + program.category().toString().length());
            result += 512L * program.graph().nodes().size() + 256L * program.graph().edges().size();
            result += 128L * program.editorLayout().nodePositions().size();
            for (var edge : program.graph().edges()) {
                result += 4L * (edge.from().port().length() + edge.to().port().length());
            }
            for (var node : program.graph().nodes()) {
                if (result > limit) return result;
                result += 4L * node.type().toString().length();
                var pending = new ArrayDeque<JsonElement>();
                pending.add(node.configuration());
                while (!pending.isEmpty()) {
                    var value = pending.removeLast();
                    result += 128L;
                    if (value.isJsonObject()) for (var field : value.getAsJsonObject().entrySet()) {
                        result += 128L + 16L * field.getKey().length(); pending.add(field.getValue());
                    }
                    else if (value.isJsonArray()) value.getAsJsonArray().forEach(pending::add);
                    else if (value.isJsonPrimitive()) result += 16L * value.getAsString().length();
                    if (result > limit) return result;
                }
            }
        }
        return result;
    }

    synchronized void clear() {
        entries.clear(); weight = 0; hits = misses = bypasses = evictions = 0;
    }

    synchronized Stats stats() { return new Stats(entries.size(), weight, hits, misses, bypasses, evictions); }
    record Stats(int entries, long weight, long hits, long misses, long bypasses, long evictions) {}
    private record Entry(ProgramBookCodec.DecodeResult decoded, long weight) {}
}
