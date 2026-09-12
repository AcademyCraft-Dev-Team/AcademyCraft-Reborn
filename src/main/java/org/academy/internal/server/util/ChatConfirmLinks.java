package org.academy.internal.server.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 聊天栏彩色可点击确认组件：生成 [同意][拒绝] 之类的按钮文字，点击后以 RUN_COMMAND 执行命令。
 * 服务端系统消息触发 RUN_COMMAND 不弹玩家确认框，适合令牌式确认流程喵。
 */
public final class ChatConfirmLinks {
    private ChatConfirmLinks() {
    }

    public static void sendChoice(
            ServerPlayer player,
            Component prefix,
            Component acceptLabel,
            String acceptCommand,
            Component rejectLabel,
            String rejectCommand
    ) {
        var message = prefix.copy()
                .append(link(acceptLabel, acceptCommand, ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(link(rejectLabel, rejectCommand, ChatFormatting.RED));
        player.sendSystemMessage(message);
    }

    public static Component link(Component label, String command, ChatFormatting color) {
        return Component.literal("[").withStyle(style -> style.withColor(color))
                .append(label.copy().withStyle(style -> style
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(label))))
                .append(Component.literal("]").withStyle(style -> style.withColor(color)));
    }
}
