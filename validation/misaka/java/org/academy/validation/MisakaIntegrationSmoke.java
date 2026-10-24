package org.academy.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.screen.RenderRoot;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.client.app.misaka.MisakaDeviceQuotaApp;
import org.academy.internal.client.gui.screen.MisakaNetworkPanelScreen;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.MultiBlock;
import org.academy.internal.server.misaka.MisakaPanelSupport;

import java.nio.file.Files;
import java.util.UUID;
import java.util.function.Consumer;

/** Opt-in real-client probe. Only loaded by validation/misaka/client.init.gradle. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MisakaIntegrationSmoke {
    private static final BlockPos[] DEVICES = {
            new BlockPos(-4, 80, 0), new BlockPos(0, 80, 0), new BlockPos(4, 80, 0)};
    private static final String[] SCREENS = {
            "energy_laser_tower_screen", "aerospace_signal_cabin_screen", "satellite_launch_pad_screen"};
    private static final String[] CLASSES = {
            "EnergyLaserTowerScreen", "AerospaceSignalCabinScreen", "SatelliteLaunchPadScreen"};
    private static volatile UUID sisterId;
    private static volatile boolean prepared;
    private static volatile String failure;
    private static boolean started;
    private static boolean finished;
    private static int ticks;
    private static int phase;
    private static int captured;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.misakaIntegrationSmoke") || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (failure != null) throw new IllegalStateException(failure);
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (!started) {
                started = true;
                var directory = mc.gameDirectory.toPath().toRealPath();
                var world = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toRealPath();
                require(directory.getFileName().toString().equals("misaka-integration-smoke")
                        && world.startsWith(directory.resolve("saves")), "Isolated save required");
                server(player -> {
                    var level = player.level();
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(level, 0.5, 80, 7.5, java.util.Set.of(), 180, 10, true);
                    for (int x = -12; x <= 12; x++) {
                        for (int z = -6; z <= 12; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 79, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                            for (int y = 80; y < 87; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        }
                    }
                    var blocks = new net.minecraft.world.level.block.Block[]{Blocks.ENERGY_LASER_TOWER.get(), Blocks.AEROSPACE_SIGNAL_CABIN.get(), Blocks.SATELLITE_LAUNCH_PAD.get()};
                    for (int i = 0; i < blocks.length; i++) {
                        var state = blocks[i].defaultBlockState();
                        level.setBlockAndUpdate(DEVICES[i], state);
                        blocks[i].setPlacedBy(level, DEVICES[i], state, player, ItemStack.EMPTY);
                    }
                    var node = new BlockPos(8, 80, 0);
                    level.setBlockAndUpdate(node, Blocks.WIRELESS_NODE.get().defaultBlockState());
                    var wireless = org.academy.internal.server.world.level.storage.WirelessNetworkData.get(level);
                    if (wireless.getNodeConfig(node) == null) wireless.registerNode(node, "smoke-network", "", 32, 16);
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                            new ItemStack(org.academy.internal.common.world.item.Items.LASER_DESIGNATOR.get()));
                    var sister = EntityTypes.MISAKA_SISTER.get().create(level, EntitySpawnReason.COMMAND);
                    require(sister != null, "Sister factory");
                    sister.setPos(1.5, 80, 3.5);
                    sister.setNoAi(true);
                    level.addFreshEntity(sister);
                    sisterId = sister.getUUID();
                    prepared = true;
                });
                return;
            }
            if (!prepared || ++ticks < 80) return;
            ticks = 0;
            switch (phase++) {
                case 0 -> screenshot(mc, "world");
                case 1 -> server(player -> {
                    var sister = (MisakaSisterEntity) player.level().getEntity(sisterId);
                    require(sister != null, "Sister persisted in level");
                    var record = sister.rosterRecord().orElseThrow();
                    record.awakened = true;
                    record.perception = 100;
                    record.favorByPlayerName.put(player.getGameProfile().name(), 5);
                    record.lastInteractedBenevolentPlayerName = player.getGameProfile().name();
                    sister.bindToRecord(record);
                    require(org.academy.api.common.misaka.MisakaNAT.get().bindSisterToNode(
                            player.level(), record.misakaUuid, new BlockPos(8, 80, 0)), "Bind sister to real node");
                    MisakaPanelSupport.sendPanel(player, sister);
                });
                case 2 -> {
                    require(mc.gui.screen() instanceof MisakaNetworkPanelScreen, "Real panel packet opens screen");
                    screenshot(mc, "sister-panel");
                    ((MisakaNetworkPanelScreen) mc.gui.screen()).showManagePage();
                }
                case 3 -> {
                    require(((MisakaNetworkPanelScreen) mc.gui.screen()).getManageTotalCount() > 0, "Network management received sister data");
                    screenshot(mc, "network-management"); mc.gui.setScreen(null);
                }
                case 4 -> openDevice(0);
                case 5 -> { verifyScreen(mc, 0); screenshot(mc, "laser-inventory"); select(mc, "wireless"); }
                case 6 -> { screenshot(mc, "laser-wireless"); select(mc, "asset"); }
                case 7 -> { screenshot(mc, "laser-ownership"); mc.player.closeContainer(); }
                case 8 -> openDevice(1);
                case 9 -> { verifyScreen(mc, 1); select(mc, "ops"); }
                case 10 -> { screenshot(mc, "aerospace-operations"); mc.player.closeContainer(); }
                case 11 -> openDevice(2);
                case 12 -> { verifyScreen(mc, 2); screenshot(mc, "launch-inventory"); select(mc, "launch"); }
                case 13 -> { screenshot(mc, "launch-controls"); mc.player.closeContainer(); }
                case 14 -> mc.gui.setScreen(new UiScreen(Component.literal("Misaka quota smoke")) {
                    @Override protected void onInit() {
                        getRoot().addChild("quota", MisakaDeviceQuotaApp.INSTANCE.createContext().get());
                    }
                });
                case 15 -> screenshot(mc, "device-quota");
                case 16 -> { mc.gui.setScreen(null); server(player -> MisakaPanelSupport.sendPanel(player,
                        (MisakaSisterEntity) player.level().getEntity(sisterId))); }
                case 17 -> {
                    var panel = (MisakaNetworkPanelScreen) mc.gui.screen();
                    panel.showManagePage();
                    panel.showManageTab(org.academy.internal.client.gui.screen.MisakaPanelManageTab.ALLOC);
                }
                case 18 -> {
                    screenshot(mc, "network-allocation");
                    ((MisakaNetworkPanelScreen) mc.gui.screen()).showManageTab(org.academy.internal.client.gui.screen.MisakaPanelManageTab.MEMBERS);
                }
                case 19 -> screenshot(mc, "network-members");
                case 20 -> {
                    require(captured == 12, "All twelve screenshots saved, got " + captured);
                    AcademyCraft.LOGGER.info("MISAKA_INTEGRATION_SMOKE_PASSED screenshots={} real_panel_packet=true real_menus=true", captured);
                    finished = true;
                    mc.stop();
                }
            }
        } catch (Throwable error) {
            finished = true;
            AcademyCraft.LOGGER.error("MISAKA_INTEGRATION_SMOKE_FAILED", error);
            mc.stop();
        }
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable error) { failure = error.toString(); AcademyCraft.LOGGER.error("Misaka smoke server failure", error); }
        });
    }

    private static void openDevice(int index) {
        server(player -> {
            var pos = DEVICES[index];
            player.teleportTo(player.level(), pos.getX() + .5, 80, 3.5, java.util.Set.of(), 180, 10, true);
            var state = player.level().getBlockState(pos);
            var provider = state.getMenuProvider(player.level(), pos);
            require(provider != null, "Menu provider " + index);
            ServerPlayerUtil.openMenuScreen(player, provider, SCREENS[index], buffer -> buffer.writeBlockPos(pos));
        });
    }

    private static void verifyScreen(Minecraft mc, int index) {
        require(mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals(CLASSES[index]), "Expected " + CLASSES[index]);
    }

    private static void select(Minecraft mc, String name) {
        require(mc.gui.screen() instanceof RenderRoot, "Widget screen required");
        require(selectIn(((RenderRoot) mc.gui.screen()).getRoot(), name), "Missing page " + name);
    }

    private static boolean selectIn(WidgetContainer parent, String name) {
        var button = parent.getChildren().get(name);
        if (parent instanceof RadioGroupWidget group && button instanceof RadioButtonWidget radio) {
            group.selectButton(radio);
            return true;
        }
        for (var child : parent.getChildren().values()) {
            if (child instanceof WidgetContainer container && selectIn(container, name)) return true;
        }
        return false;
    }

    private static void screenshot(Minecraft mc, String label) {
        String name = "misaka-" + label + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name))) failure = "Screenshot missing: " + label;
            else { captured++; AcademyCraft.LOGGER.info("MISAKA_SMOKE_SCREENSHOT {}", label); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
