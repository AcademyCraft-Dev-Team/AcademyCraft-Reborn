package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.ChunkLeapCost;
import org.academy.internal.common.ability.teleport.ChunkLeapPackets;
import org.academy.internal.common.ability.teleport.ChunkMapClientState;

import java.util.List;

/**
 * 区块跃迁 (Chunk Leap) — a level-5 teleport skill that opens a zoomable, draggable chunk map.
 *
 * <p>The map can load chunks on demand (same or other dimensions), shows living entities, and lets
 * the player box-select equal-sized chunk regions to swap between two places — carrying their
 * contents, entities and the player along with them.
 *
 * <p>Pricing scales with the exchanged volume and the number of relocated entities, so large-scale
 * terrain rearrangement is self-limiting. The client may only *request*; every region is re-validated
 * and clamped server-side before anything moves.
 */
public final class ChunkLeap extends Skill {
    public ChunkLeap() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(ChunkLeapCost.BASE)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.AREA_TELEPORT_SELECT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
                .devCondition(new DevCondition.DependencyCondition(
                        "Area Teleport", "academy:area_teleport_select"))
        );
    }

    /**
     * Charges CP for a region swap and runs {@code action} only if the charge succeeded.
     *
     * <p>{@code executeActive} is protected, so skill logic outside this class goes through here.
     */
    public boolean executeSwap(ServerPlayer player, long chunkCount, long entityCount, Runnable action) {
        var cost = ChunkLeapCost.forSwap(chunkCount, entityCount);
        return executeActive(player, context -> cost, (context, actualCost) -> action.run());
    }

    /** Charges CP for a single-entity teleport and runs {@code action} only if it succeeded. */
    public boolean executeEntityTeleport(ServerPlayer player, Runnable action) {
        var cost = ChunkLeapCost.forEntityTeleport();
        return executeActive(player, context -> cost, (context, actualCost) -> action.run());
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key,
                org.academy.internal.client.ability.teleport.ChunkLeapClientConfig.Action.INSTANCE);
        var config = AcademyCraftClient.Config.INSTANCE
                .<org.academy.internal.client.ability.teleport.ChunkLeapClientConfig>getConfig(key);
        config.registerSettings(this);
        ChunkLeapPackets.initClient();
        InputSystem.addKeyBinding(Client.KEY_NAME_OPEN, config.getKeyBinding(
                Client.KEY_NAME_OPEN,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_C,
                        InputConstants.PRESS, InputConstants.MOD_ALT)
        ), ctx -> Client.openMap());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        ChunkLeapPackets.initServer();
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.TELEPORT.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.CHUNK_LEAP.get(),
                        List.of(org.academy.internal.common.ability.teleport.skills.lv4.AreaTeleportSelect
                                .Client.SKILL_INFO),
                        R.textures.chunk_leap_icon,
                        190,
                        60
                )
        );
        public static final String KEY_NAME_OPEN = SkillNames.CHUNK_LEAP + "_open";

        /** Opens the map, or closes it when the map is already on screen. */
        private static void openMap() {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            if (minecraft.gui.screen() instanceof
                    org.academy.internal.client.gui.screen.ChunkLeapScreen) {
                minecraft.gui.setScreen(null);
                return;
            }
            if (ClientUtil.hasScreen()) return;
            if (!AbilitySystemClient.canUseSkill(Skills.CHUNK_LEAP.get())) return;
            ChunkMapClientState.clear();
            minecraft.gui.setScreen(new org.academy.internal.client.gui.screen.ChunkLeapScreen());
        }
    }
}
