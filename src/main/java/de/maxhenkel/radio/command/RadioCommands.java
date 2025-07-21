package de.maxhenkel.radio.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioItem;
import de.maxhenkel.voicechat.api.Player;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class RadioCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> literalBuilder = Commands.literal("radio")
                .requires((commandSource) -> commandSource.hasPermission(Radio.SERVER_CONFIG.commandPermissionLevel.get()));

        literalBuilder.then(
            Commands.literal("create")
                    .then(
                        Commands.argument("url", StringArgumentType.string())
                                .then(
                                    Commands.argument("station_name", StringArgumentType.string())
                                            .executes(RadioCommands::runWithoutRange)
                                            .then(
                                                Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0f))
                                                .executes(RadioCommands::runWithRange)
                                            )
                                )
                    )
        );

        dispatcher.register(literalBuilder);
    }


    private static int runWithRange(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String url = StringArgumentType.getString(context, "url");
        String stationName = StringArgumentType.getString(context, "station_name");
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        ServerPlayer player = context.getSource().getPlayerOrException();

        return runCommand(url, stationName, player, soundRadius);
    }

    private static int runWithoutRange(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String url = StringArgumentType.getString(context, "url");
        String stationName = StringArgumentType.getString(context, "station_name");
        ServerPlayer player = context.getSource().getPlayerOrException();

        return RadioCommands.runCommand(url, stationName, player, -1.0f);
    }


    private static int runCommand(String url, String stationName, ServerPlayer player, float range) {
        try {
            player.getInventory().add(RadioItem.newRadio(url, stationName, false, range));
            player.sendSystemMessage(Component.literal("Provided you with a radio for '%s'.".formatted(stationName)));
            return 1;
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal("There was an error while providing you with a radio.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

}
