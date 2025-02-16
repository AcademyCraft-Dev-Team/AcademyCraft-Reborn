package org.academy.internal.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;
import org.academy.internal.client.renderer.entity.ThrownCoinRenderer;
import org.apache.logging.log4j.Logger;

import java.util.Collections;

public class AcademyCraftCommand {
    private static final Logger log = AcademyCraft.LOGGER;

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(Commands.literal("academy").requires(commandSourceStack -> commandSourceStack.hasPermission(2)).then(Commands.literal("info").then(Commands.literal("get").executes(commandContext -> showInfo(commandContext.getSource()))).then(Commands.literal("set").then(Commands.argument("value", IntegerArgumentType.integer()).executes(commandContext -> {
            int value = IntegerArgumentType.getInteger(commandContext, "value");
/*            Esper esper = (Esper) commandContext.getSource().getEntity();
            esper.academyCraft$setSP(value);
            log.info("Setting AbilitySystem.sp to: {}", esper.academyCraft$getSP());*/
            return 0;
        })))).then(Commands.literal("render").then(Commands.literal("a").then(Commands.argument("a", FloatArgumentType.floatArg()).executes(context -> {
            ThrownCoinRenderer.a = FloatArgumentType.getFloat(context, "a");
            return 0;
        }))).then(Commands.literal("b").then(Commands.argument("b", FloatArgumentType.floatArg()).executes(context -> {
            ThrownCoinRenderer.b = FloatArgumentType.getFloat(context, "b");
            return 0;
        }))).then(Commands.literal("c").then(Commands.argument("c", FloatArgumentType.floatArg()).executes(context -> {
            ThrownCoinRenderer.c = FloatArgumentType.getFloat(context, "c");
            return 0;
        })))).then(Commands.literal("key").then(Commands.literal("add").then(Commands.argument("key", IntegerArgumentType.integer(0)).executes(commandContext -> {
            int key = IntegerArgumentType.getInteger(commandContext, "key");
            InputSystem.addKeyRelease(Collections.singletonList(key), () -> log.info("Key down: " + key));
            log.info("Key added: " + key);
            return 1;
        }))).then(Commands.literal("remove").then(Commands.argument("key", IntegerArgumentType.integer(0)).executes(commandContext -> {
            int key = IntegerArgumentType.getInteger(commandContext, "key");
            InputSystem.removeKeyRelease(Collections.singletonList(key));
            log.info("Key removed: " + key);
            return 1;
        })))));
    }

    private static int showInfo(CommandSourceStack source) {
/*        Esper mixinTarget = (Esper) source.getEntity();
        AcademyCraft.LOGGER.info(mixinTarget.academyCraft$getSP());
        return mixinTarget.academyCraft$getSP();*/
        return 0;
    }
}
