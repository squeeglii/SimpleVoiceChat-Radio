package de.maxhenkel.radio.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.RadioCommon;
import de.maxhenkel.radio.radio.FabricRadioLocation;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioItem;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.radio.radio.RadioStream;
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
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class RadioCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> literalBuilder = Commands.literal("radio")
                .requires(source -> {
                    Optional<Permission> optMinPermissionLevel = getMinimumPermission();

                    return optMinPermissionLevel.isPresent()
                            ? source.permissions().hasPermission(optMinPermissionLevel.get())
                            : true; // level == 0, always accept.
                } );

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

        literalBuilder
                .then(Commands.literal("change")
                        .then(Commands.literal("held_item")
                                .then(Commands.literal("radius")
                                        .then(
                                                Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0f))
                                                        .executes(RadioCommands::runChangeItemRadius)
                                        )
                                )
                        )
                        .then(Commands.literal("block")
                                .then(Commands.argument("location", BlockPosArgument.blockPos())
                                        .then(Commands.literal("radius")
                                                .then(
                                                        Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0f))
                                                                .executes(RadioCommands::runChangeBlockRadius)
                                                )
                                        )
                                )
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
            LogUtils.getLogger().error(ex.getMessage(), ex);
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

            for(Component component: createDebugMessage(rData, blockPos)) {
                message.append(component);
            }

            context.getSource().sendSuccess(() -> message, false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Block at [%s] is not a radio.".formatted(blockPos.toShortString())));
            return 0;
        }
    }

    private static int runChangeItemRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        ServerPlayer player = context.getSource().getPlayerOrException();

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        Optional<RadioData> optRadioData = RadioItem.readRadioData(heldItem);

        try {
            if (optRadioData.isPresent()) {
                RadioData data = optRadioData.get();

                data.setRange(soundRadius);
                RadioItem.serialiseIntoItemStack(data, heldItem); // replacing the data.

                context.getSource().sendSuccess(() -> Component.literal("Changed radio sound radius to %.1f block(s)".formatted(soundRadius)), false);
                return 1;

            } else {
                context.getSource().sendFailure(Component.literal("There is no radio in your main hand."));
                return 0;
            }

        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal("There was an error while editing your radio.").withStyle(ChatFormatting.RED));
            LogUtils.getLogger().error(ex.getMessage(), ex);
            return 0;
        }
    }

    private static int runChangeBlockRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos blockPos = BlockPosArgument.getBlockPos(context, "location");
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();

        BlockEntity blockEntity = level.getBlockEntity(blockPos);

        try {
            if(blockEntity instanceof IPossibleRadioBlock radioBlock && radioBlock.radio$isRadio()) {
                RadioData data = radioBlock.radio$getRadioData();

                data.setRange(soundRadius);
                RadioManager.getInstance().updateRadioStream(data, new FabricRadioLocation(level, blockPos));
                blockEntity.setChanged(); // ensure the volume change is saved.

                context.getSource().sendSuccess(() -> Component.literal("Changed radio sound radius to %.1f block(s)".formatted(soundRadius)), false);
                return 1;

            } else {
                context.getSource().sendFailure(Component.literal("There is no radio at that location."));
                return 0;
            }

        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal("There was an error while editing your radio.").withStyle(ChatFormatting.RED));
            LogUtils.getLogger().error(ex.getMessage(), ex);
            return 0;
        }
    }

    // 0 (none) --> 4 (owner)
    private static Optional<Permission> getMinimumPermission() {
        int level = RadioCommon.SERVER_CONFIG.commandPermissionLevel.get();

        return Optional.ofNullable(switch (level) {
            case 0 -> null;
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            case 4 -> Permissions.COMMANDS_OWNER;

            // todo: add a way to block using the command entirely

            default -> {
                Radio.LOGGER.warn("Minimum permission level incorrectly configured for svc-radio command! Must be between 0-4");
                yield  Permissions.COMMANDS_GAMEMASTER;
            }
        });
    }

    private static List<Component> createDebugMessage(RadioData data, BlockPos blockPos) {
        ChatFormatting[] body = { ChatFormatting.GRAY, ChatFormatting.ITALIC };
        ChatFormatting[] title = { ChatFormatting.DARK_GRAY, ChatFormatting.UNDERLINE };

        String range = data.isRangeDefault() ? "<default>" : "%.1f block(s)".formatted(data.getRange());

        LinkedList<Component> message = new LinkedList<>(List.of(
                Component.literal("-- Radio Block Data at [%s]:\n".formatted(blockPos.toShortString())).withStyle(title),

                Component.literal("UUID:  %s\n".formatted(data.getId())).withStyle(body),
                Component.literal("Name:  '%s'\n".formatted(data.getStationName())).withStyle(body),
                Component.literal("URL:  '%s'\n".formatted(data.getUrl())).withStyle(body),
                Component.literal("Range:  %s\n".formatted(range)).withStyle(body),
                Component.literal("On?  %s\n\n".formatted(data.isOn())).withStyle(body)
        ));

        Optional<RadioStream> optStream = RadioManager.getInstance().getRadioStream(data);

        message.add(Component.literal("-- Radio Stream Data:\n").withStyle(title));

        if(optStream.isPresent()) {
            RadioStream stream = optStream.get();
            message.addAll(List.of(
                    Component.literal("Channel Id:  %s\n".formatted(stream.getLastKnownChannelId())).withStyle(body),
                    Component.literal("State:  '%s'\n".formatted(stream.getState())).withStyle(body),
                    Component.literal("Range:  %s".formatted(stream.getOutputChannelRange())).withStyle(body)
            ));
        } else {
            message.add(Component.literal("...no radio stream found...").withStyle(body));
        }

        return message;
    }

}
