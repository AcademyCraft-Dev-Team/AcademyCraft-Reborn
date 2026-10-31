package org.academy.internal.common.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramStoredBookCacheTest {
    private static final Identifier CATEGORY = AcceleratorProgramNodeCatalog.ACCELERATOR;

    @Test void exactStoredContentHitsAcrossStringInstancesAndChangedRevisionOrConfigurationMisses() {
        var cache = new ProgramStoredBookCache();
        var loads = new AtomicInteger();
        String first = encoded(book(1, "9007199254740992"));
        String second = encoded(book(1, "9007199254740993"));
        var a = decode(cache, first, loads);
        assertSame(a, decode(cache, new String(first), loads));
        var b = decode(cache, second, loads);
        assertEquals(0, configuration(a).getAsJsonObject().get("value").getAsInt());
        assertEquals(1, configuration(b).getAsJsonObject().get("value").getAsInt());
        assertNotSame(a, b, "Gson's numeric equality collision must never merge different encoded books");
        assertEquals(2, decode(cache, encoded(book(2, "9007199254740993")), loads).book().revision());
        assertEquals(3, loads.get()); assertEquals(1, cache.stats().hits());
    }

    @Test void returnedNodeConfigurationAndLayoutCannotModifyTheRetainedDecodedBook() {
        var cache = new ProgramStoredBookCache(); var loads = new AtomicInteger();
        String source = encoded(book(1, "7"));
        var decoded = decode(cache, source, loads);
        configuration(decoded).getAsJsonObject().addProperty("value", -50);
        assertThrows(UnsupportedOperationException.class, () -> decoded.book().slot(0).program().editorLayout().nodePositions().clear());
        assertEquals(7, configuration(decode(cache, source, loads)).getAsJsonObject().get("value").getAsInt());
        assertEquals(1, loads.get());
    }

    @Test void failuresAndOversizedEntriesAreNotRetainedAndEntryAndWeightLimitsEvictAndClear() {
        assertThrows(IllegalArgumentException.class, () -> new ProgramStoredBookCache(0, 10));
        var invalid = new ProgramStoredBookCache();
        var badLoads = new AtomicInteger();
        for (int i = 0; i < 2; i++) assertFalse(invalid.decode("invalid", () -> {
            badLoads.incrementAndGet(); return ProgramBookCodec.decode(new byte[]{99});
        }).valid());
        assertEquals(2, badLoads.get()); assertEquals(0, invalid.stats().entries());
        var sources = List.of(encoded(book(1, "7")), encoded(book(2, "7")), encoded(book(3, "7")));
        var loads = new AtomicInteger(); var entries = new ProgramStoredBookCache(2, Long.MAX_VALUE);
        decode(entries, sources.get(0), loads); decode(entries, sources.get(1), loads);
        decode(entries, sources.get(0), loads); decode(entries, sources.get(2), loads);
        decode(entries, sources.get(1), loads);
        assertEquals(4, loads.get()); assertEquals(2, entries.stats().entries()); assertEquals(2, entries.stats().evictions());
        var probe = new ProgramStoredBookCache(); decode(probe, sources.get(0), new AtomicInteger());
        long oneWeight = probe.stats().weight();
        var byWeight = new ProgramStoredBookCache(64, oneWeight + 1);
        decode(byWeight, sources.get(0), loads); decode(byWeight, sources.get(1), loads);
        assertEquals(1, byWeight.stats().entries()); assertTrue(byWeight.stats().weight() <= oneWeight + 1);
        var oversized = new ProgramStoredBookCache(64, oneWeight - 1);
        decode(oversized, sources.get(0), loads); decode(oversized, sources.get(0), loads);
        assertEquals(0, oversized.stats().entries()); assertEquals(2, oversized.stats().bypasses());
        entries.clear(); assertEquals(new ProgramStoredBookCache.Stats(0, 0, 0, 0, 0, 0), entries.stats());
    }

    @Test void rawBase64SpellingRemainsTheExactKeyEvenWhenDecodedPayloadsAreEqual() {
        var cache = new ProgramStoredBookCache(); var loads = new AtomicInteger();
        String padded = encoded(book(1, "7"));
        String unpadded = padded.replace("=", "");
        if (padded.equals(unpadded)) {
            padded = encoded(book(1, "77")); unpadded = padded.replace("=", "");
        }
        assertNotEquals(padded, unpadded);
        assertEquals(decode(cache, padded, loads).book(), decode(cache, unpadded, loads).book());
        assertEquals(2, loads.get()); assertEquals(2, cache.stats().entries());
    }

    @Test void managerRechecksCategoryAndEnvelopeAndStillMigratesLegacyOnEveryWarmHit() {
        AbilityProgramManager.clear();
        try {
            var legacy = book(4, "7");
            String source = encoded(legacy);
            var first = AbilityProgramManager.decodeStoredBook(CATEGORY, source);
            var second = AbilityProgramManager.decodeStoredBook(CATEGORY, source);
            assertEquals(CommonProgramNodeIds.INTEGER_CONSTANT, first.slot(0).program().graph().nodes().getFirst().type());
            assertEquals(first, second); assertTrue(AbilityProgramManager.storedBookCacheStats().hits() > 0);
            var foreign = AbilityProgramManager.decodeStoredBook(Identifier.fromNamespaceAndPath("academy", "teleport"), source);
            assertTrue(foreign.slots().stream().allMatch(ProgramBook.Slot::empty));
            var future = new ProgramBook(ProgramBook.CURRENT_SCHEMA_VERSION + 1, 99, legacy.selectedSlot(), legacy.slots());
            for (int i = 0; i < 2; i++) assertEquals(0, AbilityProgramManager.decodeStoredBook(CATEGORY, encoded(future)).revision());
            AbilityProgramManager.clear();
            assertEquals(0, AbilityProgramManager.storedBookCacheStats().entries());
            assertEquals(0, AbilityProgramManager.storedBookCacheStats().weight());
        } finally { AbilityProgramManager.clear(); }
    }

    @Test void cachedRawBookStillUsesTheCurrentExtensionLookupOnEachMigration() throws Exception {
        var field = ProgramNodeExtensionIndex.class.getDeclaredField("frozenSnapshots"); field.setAccessible(true);
        Object original = field.get(null);
        var version = new AtomicInteger(1); var reads = new AtomicInteger();
        var id = Identifier.fromNamespaceAndPath("book_cache_fixture", "dynamic");
        var extension = new DynamicExtension(version, reads);
        var snapshot = ProgramNodeExtensionIndex.build(CATEGORY, Map.of(id, extension));
        var node = new ProgramGraph.Node(1, id, 1, JsonParser.parseString("{\"value\":\"x\"}"));
        var program = new AbilityProgram(1, UUID.randomUUID(), "Dynamic extension", CATEGORY,
                new ProgramGraph(List.of(node), List.of()), ProgramEditorLayout.EMPTY);
        String source = encoded(ProgramBook.empty(10).replaceSlot(0, program));
        AbilityProgramManager.clear();
        try {
            field.set(null, Map.of(CATEGORY, snapshot));
            AbilityProgramManager.decodeStoredBook(CATEGORY, source);
            int firstReads = reads.get(); version.set(2);
            AbilityProgramManager.decodeStoredBook(CATEGORY, source);
            assertTrue(reads.get() > firstReads, "warm raw decode must not skip dynamic extension schema lookup");
            assertEquals(1, AbilityProgramManager.storedBookCacheStats().hits());
        } finally { field.set(null, original); AbilityProgramManager.clear(); }
    }

    @Test void thirtyTwoDifferentPlayerBooksWithFourModernGraphsFitTheUnchangedDefaultBudget() {
        var cache = new ProgramStoredBookCache(); var sources = new ArrayList<String>(); var loads = new AtomicInteger();
        for (int player = 0; player < 32; player++) {
            var book = ProgramBook.empty(10);
            for (int slot = 0; slot < 4; slot++) {
                var base = ProgramAutomaticBenchmarkGameTests.program(slot, 4);
                var id = UUID.nameUUIDFromBytes(("book-owner-" + player + "-slot-" + slot).getBytes(StandardCharsets.UTF_8));
                book = book.replaceSlot(slot, new AbilityProgram(base.schemaVersion(), id, "Player " + player + " slot " + slot,
                        base.category(), base.graph(), base.editorLayout()));
            }
            sources.add(encoded(book)); decode(cache, sources.getLast(), loads);
        }
        long retainedWeight = cache.stats().weight();
        assertEquals(32, cache.stats().entries()); assertTrue(retainedWeight <= ProgramStoredBookCache.MAX_WEIGHT);
        for (var source : sources) decode(cache, source, loads);
        assertEquals(32, loads.get()); assertEquals(32, cache.stats().hits()); assertEquals(0, cache.stats().evictions());
        System.out.println("STORED_BOOK_CACHE_32_DISTINCT_FOUR_GRAPH_WEIGHT_ESTIMATE=" + retainedWeight
                + " budget=" + ProgramStoredBookCache.MAX_WEIGHT + " entries=" + cache.stats().entries());
    }

    @Test void concurrentRequestsRetainOnePureDecodePerExactKeyWithinBothBounds() throws Exception {
        var cache = new ProgramStoredBookCache(); var loads = new AtomicInteger();
        var sources = java.util.stream.IntStream.range(0, 16).mapToObj(i -> encoded(book(i, "7"))).toList();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int thread = 0; thread < 4; thread++) jobs.add(() -> {
                for (int repeat = 0; repeat < 8; repeat++) for (var source : sources) assertTrue(decode(cache, source, loads).valid());
                return null;
            });
            for (var future : pool.invokeAll(jobs)) future.get();
        }
        assertEquals(16, loads.get()); assertEquals(16, cache.stats().entries());
        assertTrue(cache.stats().weight() <= ProgramStoredBookCache.MAX_WEIGHT);
    }

    private static ProgramBookCodec.DecodeResult decode(ProgramStoredBookCache cache, String source, AtomicInteger loads) {
        return cache.decode(source, () -> { loads.incrementAndGet(); return ProgramBookCodec.decode(Base64.getDecoder().decode(source)); });
    }
    private static com.google.gson.JsonElement configuration(ProgramBookCodec.DecodeResult result) {
        return result.book().slot(0).program().graph().nodes().getFirst().configuration();
    }
    private static String encoded(ProgramBook book) { return Base64.getEncoder().encodeToString(ProgramBookCodec.encode(book)); }
    private static ProgramBook book(long revision, String literal) {
        var graph = new ProgramGraph(List.of(new ProgramGraph.Node(1, CommonProgramNodeIds.INTEGER_CONSTANT, 1,
                JsonParser.parseString("{\"value\":" + literal + "}"))), List.of());
        var program = new AbilityProgram(1, new UUID(1, 2), "Stored book", CATEGORY, graph, ProgramEditorLayout.EMPTY);
        return new ProgramBook(1, revision, 0, List.of(new ProgramBook.Slot(program)));
    }

    private record DynamicExtension(AtomicInteger version, AtomicInteger reads) implements ProgramNodeExtension<String> {
        @Override public Codec<String> configurationCodec() { return Codec.STRING.fieldOf("value").codec(); }
        @Override public int schemaVersion() { reads.incrementAndGet(); return version.get(); }
        @Override public ProgramNodeSchema schema(String configuration) { return ProgramNodeSchema.EMPTY; }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.QUERY; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.PURE; }
        @Override public ProgramNodeScope scope() { return ProgramNodeScope.category(CATEGORY); }
        @Override public ProgramNodeExecution<String> execution() { return (_, _, _) -> ProgramNodeStep.data(Map.of()); }
        @Override public ProgramNodeEditorMetadata editorMetadata() {
            return new ProgramNodeEditorMetadata(JsonParser.parseString("{\"value\":\"x\"}"), ProgramNodeEditorMetadata.Group.VALUE,
                    "fixture.book_cache", "fixture.book_cache.port.");
        }
    }
}
