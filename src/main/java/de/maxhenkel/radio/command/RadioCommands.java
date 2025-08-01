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
import de.maxhenkel.radio.utils.IPossibleRadioBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadioCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> literalBuilder = Commands.literal("radio")
                .requires((commandSource) -> commandSource.hasPermission(Radio.SERVER_CONFIG.commandPermissionLevel.get()));

        literalBuilder
                .then(
                    Commands.literal("create")
                            .then(
                                Commands.argument("url", StringArgumentType.string())
                                        .then(
                                            Commands.argument("station_name", StringArgumentType.string())
                                                    .executes(RadioCommands::runCreateWithoutRange)
                                                    .then(
                                                        Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0f))
                                                        .executes(RadioCommands::runCreateWithRange)
                                                    )
                                        )
                            )
                );

        literalBuilder
                .then(
                        Commands.literal("debug")
                                .then(
                                        Commands.argument("location", BlockPosArgument.blockPos())
                                                .executes(RadioCommands::runDebugCommand)
                                )
                );

        dispatcher.register(literalBuilder);
    }


    private static int runCreateWithRange(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String url = StringArgumentType.getString(context, "url");
        String stationName = StringArgumentType.getString(context, "station_name");
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        ServerPlayer player = context.getSource().getPlayerOrException();

        return runCreate(url, stationName, player, soundRadius);
    }

    private static int runCreateWithoutRange(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String url = StringArgumentType.getString(context, "url");
        String stationName = StringArgumentType.getString(context, "station_name");
        ServerPlayer player = context.getSource().getPlayerOrException();

        return RadioCommands.runCreate(url, stationName, player, -1.0f);
    }


    private static int runCreate(String url, String stationName, ServerPlayer player, float range) {
        try {
            player.getInventory().add(RadioItem.newRadio(url, stationName, false, range));
            player.sendSystemMessage(Component.literal("Provided you with a radio for '%s'.".formatted(stationName)));
            return 1;
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal("There was an error while providing you with a radio.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int runDebugCommand(CommandContext<CommandSourceStack> context) {
        BlockPos blockPos = BlockPosArgument.getBlockPos(context, "location");
        ServerLevel level = context.getSource().getLevel();

        BlockEntity blockEntity = level.getBlockEntity(blockPos);

        if(blockEntity instanceof IPossibleRadioBlock radioBlock && radioBlock.radio$isRadio()) {
            RadioData rData = radioBlock.radio$getRadioData();

            MutableComponent message = Component.literal("");

            for(Component component: rData.toMessage(blockPos)) {
                message.append(component);
            }

            context.getSource().sendSuccess(() -> message, false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Block at [%s] is not a radio.".formatted(blockPos.toShortString())));
            return 0;
        }
    }

}
