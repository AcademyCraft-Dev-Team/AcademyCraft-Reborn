package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-network governance: first-integration admin, member permissions, reconstruction sister.
 */
public final class MisakaNetworkGovernance extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;

    public static final AtomicReference<@Nullable MisakaNetworkGovernance> TESTING_OVERRIDE = new AtomicReference<>();

    public static final Codec<MisakaNetworkPermission> PERMISSION_CODEC = Codec.STRING.xmap(
            name -> {
                try {
                    return MisakaNetworkPermission.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    return MisakaNetworkPermission.ACCESS;
                }
            },
            Enum::name
    );

    public record AdminEntry(UUID playerUuid, String nameCache) {
        public AdminEntry {
            playerUuid = playerUuid == null ? new UUID(0L, 0L) : playerUuid;
            nameCache = nameCache == null ? "" : nameCache;
        }

        public static final Codec<AdminEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MisakaSavedDataCodecs.UUID_STRING_CODEC.fieldOf("uuid").forGetter(AdminEntry::playerUuid),
                Codec.STRING.optionalFieldOf("name", "").forGetter(AdminEntry::nameCache)
        ).apply(instance, AdminEntry::new));
    }

    public record NetworkGov(
            boolean hasEverIntegrated,
            Map<UUID, AdminEntry> admins,
            Map<UUID, Set<MisakaNetworkPermission>> memberPermissions,
            @Nullable UUID reconstructionUuid
    ) {
        public NetworkGov {
            admins = admins == null ? Map.of() : Map.copyOf(admins);
            memberPermissions = memberPermissions == null ? Map.of() : copyPermMap(memberPermissions);
        }

        private static Map<UUID, Set<MisakaNetworkPermission>> copyPermMap(
                Map<UUID, Set<MisakaNetworkPermission>> source
        ) {
            var out = new HashMap<UUID, Set<MisakaNetworkPermission>>();
            source.forEach((id, perms) -> {
                if (id != null && perms != null && !perms.isEmpty()) {
                    out.put(id, EnumSet.copyOf(perms));
                }
            });
            return Map.copyOf(out);
        }

        public static final Codec<NetworkGov> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("has_ever_integrated", false).forGetter(NetworkGov::hasEverIntegrated),
                Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, AdminEntry.CODEC)
                        .optionalFieldOf("admins", Map.of())
                        .forGetter(NetworkGov::admins),
                Codec.unboundedMap(
                                MisakaSavedDataCodecs.UUID_STRING_CODEC,
                                Codec.list(PERMISSION_CODEC).xmap(
                                        list -> {
                                            var set = EnumSet.noneOf(MisakaNetworkPermission.class);
                                            for (var p : list) {
                                                if (p != null) {
                                                    set.add(p);
                                                }
                                            }
                                            return (Set<MisakaNetworkPermission>) set;
                                        },
                                        set -> set == null
                                                ? java.util.List.<MisakaNetworkPermission>of()
                                                : set.stream().sorted().toList()
                                )
                        )
                        .optionalFieldOf("member_permissions", Map.of())
                        .forGetter(NetworkGov::memberPermissions),
                MisakaSavedDataCodecs.UUID_STRING_CODEC.optionalFieldOf("reconstruction_uuid")
                        .forGetter(g -> Optional.ofNullable(g.reconstructionUuid))
        ).apply(instance, (integrated, admins, members, recon) ->
                new NetworkGov(integrated, admins, members, recon.orElse(null))
        ));
    }

    public static final Codec<MisakaNetworkGovernance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 0).forGetter(g -> g.dataVersion),
            Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, NetworkGov.CODEC)
                    .fieldOf("by_network")
                    .forGetter(g -> snapshot(g.byNetwork))
    ).apply(instance, MisakaNetworkGovernance::fromCodec));

    public static final SavedDataType<MisakaNetworkGovernance> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_network_governance"),
            MisakaNetworkGovernance::new,
            CODEC
    );

    private int dataVersion = CURRENT_DATA_VERSION;
    private final Map<UUID, MutableGov> byNetwork = new HashMap<>();

    public MisakaNetworkGovernance() {
    }

    private static MisakaNetworkGovernance fromCodec(int dataVersion, Map<UUID, NetworkGov> byNetwork) {
        var gov = new MisakaNetworkGovernance();
        gov.dataVersion = Math.max(dataVersion, CURRENT_DATA_VERSION);
        byNetwork.forEach((id, snap) -> gov.byNetwork.put(id, MutableGov.from(snap)));
        return gov;
    }

    private static Map<UUID, NetworkGov> snapshot(Map<UUID, MutableGov> source) {
        var out = new HashMap<UUID, NetworkGov>();
        source.forEach((id, mutable) -> out.put(id, mutable.toImmutable()));
        return out;
    }

    public static void testingInstall(@Nullable MisakaNetworkGovernance governance) {
        TESTING_OVERRIDE.set(governance);
    }

    public static MisakaNetworkGovernance get(MinecraftServer server) {
        var override = TESTING_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public boolean hasEverIntegrated(UUID networkId) {
        if (networkId == null) {
            return false;
        }
        var gov = byNetwork.get(networkId);
        return gov != null && gov.hasEverIntegrated;
    }

    public Optional<UUID> reconstructionUuid(UUID networkId) {
        if (networkId == null) {
            return Optional.empty();
        }
        var gov = byNetwork.get(networkId);
        return gov == null ? Optional.empty() : Optional.ofNullable(gov.reconstructionUuid);
    }

    public void setReconstructionUuid(UUID networkId, @Nullable UUID misakaUuid) {
        if (networkId == null) {
            return;
        }
        var gov = mutable(networkId);
        gov.reconstructionUuid = misakaUuid;
        setDirty();
    }

    /**
     * True when the network has no admins and no granted members yet
     * (first-connection / password-only bind path).
     */
    public boolean hasEmptyAdminList(@Nullable UUID networkId) {
        if (networkId == null) {
            return true;
        }
        var gov = byNetwork.get(networkId);
        if (gov == null) {
            return true;
        }
        return gov.admins.isEmpty() && gov.memberPermissions.isEmpty();
    }

    /**
     * Empty admin/member lists are the first-bind open network: any listed permission
     * passes. Otherwise the player needs at least one of {@code anyOf}.
     */
    public boolean hasPermissionOrOpen(
            @Nullable Player player,
            @Nullable UUID networkId,
            MisakaNetworkPermission... anyOf
    ) {
        if (hasEmptyAdminList(networkId)) {
            return true;
        }
        if (anyOf == null) {
            return false;
        }
        for (var required : anyOf) {
            if (hasPermission(player, networkId, required)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(@Nullable Player player, @Nullable UUID networkId, MisakaNetworkPermission required) {
        if (player == null || networkId == null || required == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer
                && net.minecraft.commands.Commands.hasPermission(
                        net.minecraft.commands.Commands.LEVEL_GAMEMASTERS)
                .test(serverPlayer.createCommandSourceStack())) {
            return true;
        }
        return hasPermission(player.getUUID(), networkId, required);
    }

    public boolean hasPermission(UUID playerUuid, @Nullable UUID networkId, MisakaNetworkPermission required) {
        if (playerUuid == null || networkId == null || required == null) {
            return false;
        }
        var gov = byNetwork.get(networkId);
        if (gov == null) {
            return false;
        }
        if (gov.admins.containsKey(playerUuid)) {
            // Admins have ADMIN (implies all except OWNER).
            if (MisakaNetworkPermission.ADMIN.implies(required)) {
                return true;
            }
        }
        var held = gov.memberPermissions.get(playerUuid);
        return MisakaNetworkPermission.anyImplies(held == null ? Set.of() : held, required);
    }

    public void grant(UUID networkId, UUID playerUuid, @Nullable String nameCache, MisakaNetworkPermission permission) {
        if (networkId == null || playerUuid == null || permission == null) {
            return;
        }
        var gov = mutable(networkId);
        if (permission == MisakaNetworkPermission.ADMIN || permission == MisakaNetworkPermission.OWNER) {
            gov.admins.put(playerUuid, new AdminEntry(playerUuid, nameCache == null ? "" : nameCache));
            if (permission == MisakaNetworkPermission.OWNER) {
                gov.memberPermissions
                        .computeIfAbsent(playerUuid, ignored -> EnumSet.noneOf(MisakaNetworkPermission.class))
                        .add(MisakaNetworkPermission.OWNER);
            }
        } else {
            gov.memberPermissions
                    .computeIfAbsent(playerUuid, ignored -> EnumSet.noneOf(MisakaNetworkPermission.class))
                    .add(permission);
        }
        setDirty();
    }

    public void revoke(UUID networkId, UUID playerUuid, MisakaNetworkPermission permission) {
        if (networkId == null || playerUuid == null || permission == null) {
            return;
        }
        var gov = byNetwork.get(networkId);
        if (gov == null) {
            return;
        }
        if (permission == MisakaNetworkPermission.ADMIN || permission == MisakaNetworkPermission.OWNER) {
            gov.admins.remove(playerUuid);
        }
        var held = gov.memberPermissions.get(playerUuid);
        if (held != null) {
            held.remove(permission);
            if (permission == MisakaNetworkPermission.OWNER) {
                held.remove(MisakaNetworkPermission.OWNER);
            }
            if (held.isEmpty()) {
                gov.memberPermissions.remove(playerUuid);
            }
        }
        setDirty();
    }

    /**
     * First network integration: marks the network integrated and grants the player ADMIN.
     * No-op when already integrated or inputs are null.
     */
    public boolean onFirstIntegration(UUID networkId, UUID playerUuid, @Nullable String nameCache) {
        if (networkId == null || playerUuid == null) {
            return false;
        }
        var gov = mutable(networkId);
        if (gov.hasEverIntegrated) {
            return false;
        }
        gov.hasEverIntegrated = true;
        gov.admins.put(playerUuid, new AdminEntry(playerUuid, nameCache == null ? "" : nameCache));
        setDirty();
        return true;
    }

    public boolean onFirstIntegration(UUID networkId, ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return onFirstIntegration(networkId, player.getUUID(), player.getGameProfile().name());
    }

    /** Display names (or short UUID) for network admins — for manage UI. */
    public List<String> listAdminDisplayNames(@Nullable UUID networkId) {
        if (networkId == null) {
            return List.of();
        }
        var gov = byNetwork.get(networkId);
        if (gov == null || gov.admins.isEmpty()) {
            return List.of();
        }
        var names = new ArrayList<String>(gov.admins.size());
        for (var entry : gov.admins.values()) {
            if (entry.nameCache() != null && !entry.nameCache().isEmpty()) {
                names.add(entry.nameCache());
            } else {
                String id = entry.playerUuid().toString();
                names.add(id.substring(0, Math.min(8, id.length())));
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    /** Member rows for manage UI: admins + explicit permission grants. */
    public List<MemberRow> listMemberRows(@Nullable UUID networkId) {
        if (networkId == null) {
            return List.of();
        }
        var gov = byNetwork.get(networkId);
        if (gov == null) {
            return List.of();
        }
        var byUuid = new HashMap<UUID, MemberRowBuilder>();
        for (var entry : gov.admins.values()) {
            var builder = byUuid.computeIfAbsent(entry.playerUuid(), ignored -> new MemberRowBuilder());
            builder.admin = true;
            if (entry.nameCache() != null && !entry.nameCache().isEmpty()) {
                builder.name = entry.nameCache();
            }
            builder.perms.add(MisakaNetworkPermission.ADMIN);
        }
        gov.memberPermissions.forEach((uuid, perms) -> {
            if (uuid == null || perms == null || perms.isEmpty()) {
                return;
            }
            var builder = byUuid.computeIfAbsent(uuid, ignored -> new MemberRowBuilder());
            builder.perms.addAll(perms);
        });
        var rows = new ArrayList<MemberRow>(byUuid.size());
        byUuid.forEach((uuid, builder) -> {
            String name = builder.name;
            if (name == null || name.isEmpty()) {
                String id = uuid.toString();
                name = id.substring(0, Math.min(8, id.length()));
            }
            var sorted = new ArrayList<>(builder.perms);
            sorted.sort(Enum::compareTo);
            rows.add(new MemberRow(name, builder.admin, List.copyOf(sorted)));
        });
        rows.sort((a, b) -> {
            if (a.admin() != b.admin()) {
                return a.admin() ? -1 : 1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });
        return rows;
    }

    public record MemberRow(String name, boolean admin, List<MisakaNetworkPermission> permissions) {
        public MemberRow {
            name = name == null ? "" : name;
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    private static final class MemberRowBuilder {
        String name = "";
        boolean admin;
        final Set<MisakaNetworkPermission> perms = EnumSet.noneOf(MisakaNetworkPermission.class);
    }

    private MutableGov mutable(UUID networkId) {
        return byNetwork.computeIfAbsent(networkId, ignored -> new MutableGov());
    }

    public void testingClear() {
        byNetwork.clear();
        dataVersion = CURRENT_DATA_VERSION;
    }

    private static final class MutableGov {
        boolean hasEverIntegrated;
        final Map<UUID, AdminEntry> admins = new HashMap<>();
        final Map<UUID, Set<MisakaNetworkPermission>> memberPermissions = new HashMap<>();
        @Nullable UUID reconstructionUuid;

        static MutableGov from(NetworkGov snap) {
            var m = new MutableGov();
            if (snap == null) {
                return m;
            }
            m.hasEverIntegrated = snap.hasEverIntegrated();
            m.admins.putAll(snap.admins());
            snap.memberPermissions().forEach((id, perms) ->
                    m.memberPermissions.put(id, EnumSet.copyOf(perms)));
            m.reconstructionUuid = snap.reconstructionUuid();
            return m;
        }

        NetworkGov toImmutable() {
            return new NetworkGov(hasEverIntegrated, admins, memberPermissions, reconstructionUuid);
        }
    }
}
