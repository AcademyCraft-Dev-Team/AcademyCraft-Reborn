package org.academy.internal.common.ability.mentalout.control;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.BlockWorkRegion;
import org.academy.api.common.entitycontrol.WorkSettings;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Durable orders; navigation handles and target claims are reconstructed after loading. */
public final class WorkOrderData extends SavedData {
    public record Entry(String controller, String subject, Identifier source, Identifier dimension,
                        BlockPos minimum, BlockPos maximum, WorkSettings settings, int priority,
                        boolean paused, List<ItemStack> cargo, String orderId, long revision, boolean returning, boolean faulted) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("controller").forGetter(Entry::controller),
                Codec.STRING.fieldOf("subject").forGetter(Entry::subject),
                Identifier.CODEC.fieldOf("source").forGetter(Entry::source),
                Identifier.CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("minimum").forGetter(Entry::minimum),
                BlockPos.CODEC.fieldOf("maximum").forGetter(Entry::maximum),
                WorkSettings.CODEC.fieldOf("settings").forGetter(Entry::settings),
                Codec.INT.fieldOf("priority").forGetter(Entry::priority),
                Codec.BOOL.fieldOf("paused").forGetter(Entry::paused),
                ItemStack.CODEC.listOf().fieldOf("cargo").forGetter(Entry::cargo),
                Codec.STRING.optionalFieldOf("order_id", "").forGetter(Entry::orderId),
                Codec.LONG.optionalFieldOf("revision", 0L).forGetter(Entry::revision),
                Codec.BOOL.optionalFieldOf("returning", false).forGetter(Entry::returning),
                Codec.BOOL.optionalFieldOf("faulted", false).forGetter(Entry::faulted)
        ).apply(instance, Entry::new));
        public Entry {
            UUID.fromString(controller);
            UUID.fromString(subject);
            Objects.requireNonNull(source);
            Objects.requireNonNull(settings);
            new BlockWorkRegion(dimension, minimum, maximum);
            cargo = copyNonEmpty(cargo);
            if (orderId.isEmpty()) orderId = UUID.nameUUIDFromBytes((controller + "/" + source + "/"
                    + dimension + "/" + minimum + "/" + maximum + "/" + settings + "/" + priority)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            UUID.fromString(orderId);
            if (revision < 0) throw new IllegalArgumentException("Negative order revision");
        }
        public Entry(String controller, String subject, Identifier source, Identifier dimension,
                     BlockPos minimum, BlockPos maximum, WorkSettings settings, int priority,
                     boolean paused, List<ItemStack> cargo) {
            this(controller, subject, source, dimension, minimum, maximum, settings, priority, paused, cargo, "", 0, false, false);
        }
        @Override
        public List<ItemStack> cargo() { return copyNonEmpty(cargo); }
        private static List<ItemStack> copyNonEmpty(List<ItemStack> stacks) {
            return stacks.stream().filter(stack -> stack != null && !stack.isEmpty())
                    .map(ItemStack::copy).toList();
        }
        public BlockWorkRegion region() { return new BlockWorkRegion(dimension, minimum, maximum); }
        public String key() { return controller + "/" + source + "/" + subject + (returning ? "/return/" + orderId : ""); }
        public Entry withPaused(boolean value) {
            return new Entry(controller, subject, source, dimension, minimum, maximum, settings,
                    priority, value, cargo, orderId, revision + 1, returning, value && faulted);
        }
        public Entry withCargo(List<ItemStack> value) {
            boolean unchanged = value.size() == cargo.size();
            for (int i = 0; unchanged && i < value.size(); i++) {
                var a = value.get(i); var b = cargo.get(i);
                unchanged = a != null && a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
            }
            if (unchanged) return this;
            return new Entry(controller, subject, source, dimension, minimum, maximum, settings,
                    priority, paused, value, orderId, revision + 1, returning, faulted);
        }
        public Entry asReturning() {
            return new Entry(controller, subject, source, dimension, minimum, maximum, settings,
                    priority, true, cargo, orderId, revision + 1, true, false);
        }
        public Entry withFault() {
            return new Entry(controller, subject, source, dimension, minimum, maximum, settings,
                    priority, true, cargo, orderId, revision + 1, returning, true);
        }
    }
    public static final Codec<WorkOrderData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("orders").forGetter(WorkOrderData::entries),
            Codec.INT.optionalFieldOf("version", 1).forGetter(data -> 2)
    ).apply(instance, (entries, version) -> {
        if (version < 1 || version > 2) throw new IllegalArgumentException("Unsupported work order version: " + version);
        return new WorkOrderData(entries);
    }));
    public static final SavedDataType<WorkOrderData> TYPE = new SavedDataType<>(
            AcademyCraft.academy("mental_work_orders"), WorkOrderData::new, CODEC);
    private final LinkedHashMap<String, Entry> orders = new LinkedHashMap<>();
    private final Map<String, Map<UUID, LinkedHashSet<String>>> byController = new HashMap<>();
    public WorkOrderData() {}
    private WorkOrderData(List<Entry> entries) { entries.forEach(this::put); }
    public static WorkOrderData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public List<Entry> entries() { return List.copyOf(orders.values()); }
    public int size() { return orders.size(); }
    public List<Entry> selected(UUID controller, Collection<UUID> subjects) {
        var indexed = byController.get(controller.toString());
        if (indexed == null) return List.of();
        var result = new ArrayList<Entry>();
        for (var subject : subjects) for (var key : indexed.getOrDefault(subject, new LinkedHashSet<>())) {
            var entry = orders.get(key);
            if (entry != null) result.add(entry);
        }
        return List.copyOf(result);
    }
    /** Bounded persistent cursor; newly appended and recently updated orders cannot starve old entries. */
    public List<Entry> recoveryBatch(int maximum) {
        var result = new ArrayList<Entry>();
        for (int i = Math.min(maximum, orders.size()); i > 0; i--) {
            var next = orders.pollFirstEntry();
            result.add(next.getValue());
            orders.put(next.getKey(), next.getValue());
        }
        return result;
    }
    public void put(Entry entry) {
        var old = orders.get(entry.key());
        if (old != null && same(old, entry)) return;
        orders.put(entry.key(), entry);
        byController.computeIfAbsent(entry.controller(), ignored -> new HashMap<>())
                .computeIfAbsent(UUID.fromString(entry.subject()), ignored -> new LinkedHashSet<>()).add(entry.key());
        setDirty();
    }
    private static boolean same(Entry first, Entry second) {
        if (!first.orderId.equals(second.orderId) || first.revision != second.revision
                || first.paused != second.paused || first.returning != second.returning || first.faulted != second.faulted
                || first.priority != second.priority || !first.settings.equals(second.settings)
                || !first.region().equals(second.region()) || first.cargo.size() != second.cargo.size()) return false;
        for (int i = 0; i < first.cargo.size(); i++) {
            var a = first.cargo.get(i); var b = second.cargo.get(i);
            if (a.getCount() != b.getCount() || !ItemStack.isSameItemSameComponents(a, b)) return false;
        }
        return true;
    }
    public void remove(String key) {
        var old = orders.remove(key);
        if (old == null) return;
        var indexed = byController.get(old.controller());
        var subject = UUID.fromString(old.subject());
        var keys = indexed.get(subject);
        keys.remove(key);
        if (keys.isEmpty()) indexed.remove(subject);
        if (indexed.isEmpty()) byController.remove(old.controller());
        setDirty();
    }
}
