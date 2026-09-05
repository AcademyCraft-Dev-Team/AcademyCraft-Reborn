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

/** Durable orders; navigation handles and target claims are reconstructed after loading. */
public final class WorkOrderData extends SavedData {
    public record Entry(String controller, String subject, Identifier source, Identifier dimension,
                        BlockPos minimum, BlockPos maximum, WorkSettings settings, int priority,
                        boolean paused, List<ItemStack> cargo) {
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
                ItemStack.CODEC.listOf().fieldOf("cargo").forGetter(Entry::cargo)
        ).apply(instance, Entry::new));
        public Entry {
            UUID.fromString(controller);
            UUID.fromString(subject);
            java.util.Objects.requireNonNull(source);
            java.util.Objects.requireNonNull(settings);
            new BlockWorkRegion(dimension, minimum, maximum);
            cargo = cargo.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        }
        public BlockWorkRegion region() { return new BlockWorkRegion(dimension, minimum, maximum); }
        public String key() { return controller + "/" + source + "/" + subject; }
        public Entry withPaused(boolean value) {
            return new Entry(controller, subject, source, dimension, minimum, maximum, settings,
                    priority, value, cargo);
        }
    }
    public static final Codec<WorkOrderData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("orders").forGetter(WorkOrderData::entries)
    ).apply(instance, WorkOrderData::new));
    public static final SavedDataType<WorkOrderData> TYPE = new SavedDataType<>(
            AcademyCraft.academy("mental_work_orders"), WorkOrderData::new, CODEC);
    private final Map<String, Entry> orders = new LinkedHashMap<>();
    public WorkOrderData() {}
    private WorkOrderData(List<Entry> entries) { entries.forEach(entry -> orders.put(entry.key(), entry)); }
    public static WorkOrderData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public List<Entry> entries() { return List.copyOf(orders.values()); }
    public void put(Entry entry) { orders.put(entry.key(), entry); setDirty(); }
    public void remove(String key) { if (orders.remove(key) != null) setDirty(); }
}
