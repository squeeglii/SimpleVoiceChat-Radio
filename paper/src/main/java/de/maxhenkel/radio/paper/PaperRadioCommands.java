package de.maxhenkel.radio.paper;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.maxhenkel.radio.RadioCommon;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import de.maxhenkel.radio.radio.RadioStream;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public final class PaperRadioCommands {

    public static final String PERMISSION = "radio.command";

    private PaperRadioCommands() {

    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin) {
        return Commands.literal("radio")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("create")
                        .then(Commands.argument("url", StringArgumentType.string())
                                .then(Commands.argument("station_name", StringArgumentType.string())
                                        .executes(context -> runCreate(context, -1.0F))
                                        .then(Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0F))
                                                .executes(context -> runCreate(context, FloatArgumentType.getFloat(context, "sound_radius")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("debug")
                        .then(Commands.argument("location", ArgumentTypes.blockPosition())
                                .executes(PaperRadioCommands::runDebug)
                        )
                )
                .then(Commands.literal("change")
                        .then(Commands.literal("held_item")
                                .then(Commands.literal("radius")
                                        .then(Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0F))
                                                .executes(PaperRadioCommands::runChangeItemRadius)
                                        )
                                )
                        )
                        .then(Commands.literal("block")
                                .then(Commands.argument("location", ArgumentTypes.blockPosition())
                                        .then(Commands.literal("radius")
                                                .then(Commands.argument("sound_radius", FloatArgumentType.floatArg(0.0F))
                                                        .executes(context -> runChangeBlockRadius(plugin, context))
                                                )
                                        )
                                )
                        )
                )
                .build();
    }

    private static int runCreate(CommandContext<CommandSourceStack> context, float range) throws CommandSyntaxException {
        String url = StringArgumentType.getString(context, "url");
        String stationName = StringArgumentType.getString(context, "station_name");
        Player player = context.getSource().getPlayerOrThrow();

        try {
            ItemStack radio = PaperRadioItem.newRadio(url, stationName, false, range);
            // Like /give, drop the radio at the player's feet if their inventory is full.
            player.getInventory().addItem(radio).values()
                    .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));

            player.sendMessage(Component.text("Provided you with a radio for '%s'.".formatted(stationName)));
            return 1;
        } catch (Exception ex) {
            player.sendMessage(Component.text("There was an error while providing you with a radio.", NamedTextColor.RED));
            RadioCommon.LOGGER.error(ex.getMessage(), ex);
            return 0;
        }
    }

    private static int runDebug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Block block = getBlock(context);
        Optional<RadioData> optData = PaperRadioBlocks.readRadioData(block);

        if (optData.isEmpty()) {
            context.getSource().sendFailure(Component.text("Block at [%s] is not a radio.".formatted(toShortString(block))));
            return 0;
        }

        context.getSource().sendSuccess(createDebugMessage(optData.get(), block), false);
        return 1;
    }

    private static int runChangeItemRadius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        Player player = context.getSource().getPlayerOrThrow();

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Optional<RadioData> optData = PaperRadioItem.readRadioData(heldItem);

        if (optData.isEmpty()) {
            context.getSource().sendFailure(Component.text("There is no radio in your main hand."));
            return 0;
        }

        try {
            RadioData data = optData.get();
            data.setRange(soundRadius);
            PaperRadioItem.writeRadioData(heldItem, data); // replacing the data.
            player.getInventory().setItemInMainHand(heldItem);

            context.getSource().sendSuccess(Component.text("Changed radio sound radius to %.1f block(s)".formatted(soundRadius)), false);
            return 1;
        } catch (Exception ex) {
            player.sendMessage(Component.text("There was an error while editing your radio.", NamedTextColor.RED));
            RadioCommon.LOGGER.error(ex.getMessage(), ex);
            return 0;
        }
    }

    private static int runChangeBlockRadius(Plugin plugin, CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        Block block = getBlock(context);
        Optional<RadioData> optData = PaperRadioBlocks.readRadioData(block);

        if (optData.isEmpty()) {
            context.getSource().sendFailure(Component.text("There is no radio at that location."));
            return 0;
        }

        try {
            RadioData data = optData.get();
            data.setRange(soundRadius);
            PaperRadioBlocks.writeRadioData(block, data); // ensure the volume change is saved.
            PaperRadioBlocks.startStream(plugin, block, data); // reopen the stream with the new range.

            context.getSource().sendSuccess(Component.text("Changed radio sound radius to %.1f block(s)".formatted(soundRadius)), false);
            return 1;
        } catch (Exception ex) {
            context.getSource().getSender().sendMessage(Component.text("There was an error while editing your radio.", NamedTextColor.RED));
            RadioCommon.LOGGER.error(ex.getMessage(), ex);
            return 0;
        }
    }

    private static Block getBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPosition position = context.getArgument("location", BlockPositionResolver.class).resolve(context.getSource());
        return context.getSource().getLocation().getWorld().getBlockAt(position.blockX(), position.blockY(), position.blockZ());
    }

    private static String toShortString(Block block) {
        return "%d, %d, %d".formatted(block.getX(), block.getY(), block.getZ());
    }

    private static Component createDebugMessage(RadioData data, Block block) {
        Style body = Style.style(NamedTextColor.GRAY, TextDecoration.ITALIC);
        Style title = Style.style(NamedTextColor.DARK_GRAY, TextDecoration.UNDERLINED);

        String range = data.isRangeDefault() ? "<default>" : "%.1f block(s)".formatted(data.getRange());

        TextComponent.Builder message = Component.text()
                .append(Component.text("-- Radio Block Data at [%s]:\n".formatted(toShortString(block)), title))
                .append(Component.text("UUID:  %s\n".formatted(data.getId()), body))
                .append(Component.text("Name:  '%s'\n".formatted(data.getStationName()), body))
                .append(Component.text("URL:  '%s'\n".formatted(data.getUrl()), body))
                .append(Component.text("Range:  %s\n".formatted(range), body))
                .append(Component.text("On?  %s\n\n".formatted(data.isOn()), body))
                .append(Component.text("-- Radio Stream Data:\n", title));

        Optional<RadioStream> optStream = RadioManager.getInstance().getRadioStream(data);

        if (optStream.isPresent()) {
            RadioStream stream = optStream.get();
            message.append(Component.text("Channel Id:  %s\n".formatted(stream.getLastKnownChannelId()), body))
                    .append(Component.text("State:  '%s'\n".formatted(stream.getState()), body))
                    .append(Component.text("Range:  %s".formatted(stream.getOutputChannelRange()), body));
        } else {
            message.append(Component.text("...no radio stream found...", body));
        }

        return message.build();
    }
}
