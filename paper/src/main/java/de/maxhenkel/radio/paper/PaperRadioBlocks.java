package de.maxhenkel.radio.paper;

import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Helpers for placed radios - player head blocks with radio data stored on their block entity.
 */
public final class PaperRadioBlocks {

    private PaperRadioBlocks() {

    }

    public static boolean isPlayerHead(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    public static Optional<RadioData> readRadioData(Block block) {
        if (!isPlayerHead(block.getType())) {
            return Optional.empty();
        }

        // No snapshot needed for reading.
        if (!(block.getState(false) instanceof Skull skull)) {
            return Optional.empty();
        }

        return PaperRadioData.read(skull.getPersistentDataContainer());
    }

    /**
     * @return false if the block isn't a player head.
     */
    public static boolean writeRadioData(Block block, RadioData data) {
        if (!(block.getState(false) instanceof Skull skull)) {
            return false;
        }

        PaperRadioData.write(skull.getPersistentDataContainer(), data, true);
        skull.update(); // marks the block entity as changed, so the data is saved
        return true;
    }

    /**
     * Opens a new stream for the radio, replacing any existing stream with the same id.
     */
    public static void startStream(Plugin plugin, Block block, RadioData data) {
        boolean idAssigned = RadioManager.getInstance().updateRadioStream(data, PaperRadioLocation.of(plugin, block));

        if (idAssigned) {
            writeRadioData(block, data);
        }
    }

    /**
     * Toggles the radio on/off, saves the new state and starts/stops its stream.
     */
    public static void toggle(Plugin plugin, Block block, RadioData data) {
        data.toggleOn();
        writeRadioData(block, data);

        if (RadioManager.getInstance().getRadioStream(data).isPresent()) {
            RadioManager.getInstance().updateHeadOnState(data.getId(), data.isOn());
        } else {
            // e.g. radios pasted in by other plugins never had a stream opened for them
            startStream(plugin, block, data);
        }
    }

    /**
     * Opens streams for all radios within the chunk.
     */
    public static void loadRadios(Plugin plugin, Chunk chunk) {
        for (BlockState state : chunk.getTileEntities(block -> isPlayerHead(block.getType()), false)) {
            if (state instanceof Skull skull) {
                PaperRadioData.read(skull.getPersistentDataContainer())
                        .ifPresent(data -> startStream(plugin, skull.getBlock(), data));
            }
        }
    }
}
