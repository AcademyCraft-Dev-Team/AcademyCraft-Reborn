package org.academy.internal.server.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persistent self-service teams created through {@code /teams}. */
public final class AcademyTeamData extends SavedData {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );
    public static final Codec<AcademyTeamData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING, Team.CODEC)
                            .fieldOf("teams")
                            .forGetter(data -> data.teams)
            ).apply(instance, AcademyTeamData::new)
    );
    public static final SavedDataType<AcademyTeamData> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("teams"),
            AcademyTeamData::new,
            CODEC
    );

    private final Map<String, Team> teams;

    public AcademyTeamData() {
        teams = new HashMap<>();
    }

    private AcademyTeamData(Map<String, Team> teams) {
        this.teams = new HashMap<>(teams);
    }

    public static AcademyTeamData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public synchronized Optional<String> teamFor(UUID playerId) {
        return teams.entrySet().stream()
                .filter(entry -> entry.getValue().members.contains(playerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public synchronized boolean areTeammates(UUID first, UUID second) {
        if (first == null || second == null) return false;
        if (first.equals(second)) return true;
        var firstTeam = teamFor(first);
        return firstTeam.isPresent() && firstTeam.equals(teamFor(second));
    }

    public synchronized List<String> teamNames() {
        return teams.keySet().stream().sorted().toList();
    }

    public synchronized Set<UUID> members(String name) {
        var team = teams.get(normalize(name));
        return team == null ? Set.of() : team.members;
    }

    public synchronized Set<String> invitationsFor(UUID playerId) {
        var result = new HashSet<String>();
        teams.forEach((name, team) -> {
            if (team.invitations.contains(playerId)) result.add(name);
        });
        return Set.copyOf(result);
    }

    public synchronized MutationResult create(UUID owner, String requestedName) {
        var name = normalize(requestedName);
        if (!isValidName(requestedName)) return MutationResult.INVALID_NAME;
        if (teamFor(owner).isPresent()) return MutationResult.ALREADY_MEMBER;
        if (teams.containsKey(name)) return MutationResult.NAME_TAKEN;
        teams.put(name, new Team(owner, Set.of(owner), Set.of()));
        setDirty();
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult invite(UUID owner, UUID target) {
        if (owner.equals(target)) return MutationResult.CANNOT_TARGET_SELF;
        var name = teamFor(owner).orElse(null);
        if (name == null) return MutationResult.NOT_IN_TEAM;
        var team = teams.get(name);
        if (!team.owner.equals(owner)) return MutationResult.NOT_OWNER;
        if (teamFor(target).isPresent()) return MutationResult.TARGET_ALREADY_MEMBER;
        if (team.invitations.contains(target)) return MutationResult.ALREADY_INVITED;
        var invitations = new HashSet<>(team.invitations);
        invitations.add(target);
        teams.put(name, new Team(team.owner, team.members, invitations));
        setDirty();
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult accept(UUID playerId, String requestedName) {
        if (teamFor(playerId).isPresent()) return MutationResult.ALREADY_MEMBER;
        var name = normalize(requestedName);
        var team = teams.get(name);
        if (team == null || !team.invitations.contains(playerId)) return MutationResult.NOT_INVITED;
        var members = new HashSet<>(team.members);
        members.add(playerId);
        var invitations = new HashSet<>(team.invitations);
        invitations.remove(playerId);
        teams.put(name, new Team(team.owner, members, invitations));
        setDirty();
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult leave(UUID playerId) {
        var name = teamFor(playerId).orElse(null);
        if (name == null) return MutationResult.NOT_IN_TEAM;
        var team = teams.get(name);
        if (team.owner.equals(playerId)) {
            teams.remove(name);
            setDirty();
            return MutationResult.TEAM_DISBANDED;
        }
        var members = new HashSet<>(team.members);
        members.remove(playerId);
        teams.put(name, new Team(team.owner, members, team.invitations));
        setDirty();
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult kick(UUID owner, UUID target) {
        if (owner.equals(target)) return MutationResult.CANNOT_TARGET_SELF;
        var name = teamFor(owner).orElse(null);
        if (name == null) return MutationResult.NOT_IN_TEAM;
        var team = teams.get(name);
        if (!team.owner.equals(owner)) return MutationResult.NOT_OWNER;
        if (!team.members.contains(target)) return MutationResult.TARGET_NOT_MEMBER;
        var members = new HashSet<>(team.members);
        members.remove(target);
        teams.put(name, new Team(team.owner, members, team.invitations));
        setDirty();
        return MutationResult.SUCCESS;
    }

    public synchronized MutationResult disband(UUID owner) {
        var name = teamFor(owner).orElse(null);
        if (name == null) return MutationResult.NOT_IN_TEAM;
        if (!teams.get(name).owner.equals(owner)) return MutationResult.NOT_OWNER;
        teams.remove(name);
        setDirty();
        return MutationResult.SUCCESS;
    }

    private static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public enum MutationResult {
        SUCCESS,
        TEAM_DISBANDED,
        INVALID_NAME,
        NAME_TAKEN,
        ALREADY_MEMBER,
        NOT_IN_TEAM,
        NOT_OWNER,
        ALREADY_INVITED,
        NOT_INVITED,
        TARGET_ALREADY_MEMBER,
        TARGET_NOT_MEMBER,
        CANNOT_TARGET_SELF
    }

    private record Team(UUID owner, Set<UUID> members, Set<UUID> invitations) {
        private static final Codec<Team> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUID_CODEC.fieldOf("owner").forGetter(Team::owner),
                UUID_CODEC.listOf().fieldOf("members")
                        .forGetter(team -> new ArrayList<>(team.members)),
                UUID_CODEC.listOf().fieldOf("invitations")
                        .forGetter(team -> new ArrayList<>(team.invitations))
        ).apply(instance, (owner, members, invitations) ->
                new Team(owner, Set.copyOf(members), Set.copyOf(invitations))));

        private Team {
            members = Set.copyOf(members);
            invitations = Set.copyOf(invitations);
        }
    }
}
