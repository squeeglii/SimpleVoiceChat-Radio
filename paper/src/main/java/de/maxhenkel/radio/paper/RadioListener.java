package de.maxhenkel.radio.paper;

import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class RadioListener implements Listener {

    private final Plugin plugin;

    public RadioListener(Plugin plugin) {
        this.plugin = plugin;
    }

    // Placing a radio item moves its data onto the placed skull.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!PaperRadioBlocks.isPlayerHead(block.getType())) {
            return;
        }

        Optional<RadioData> optData = PaperRadioItem.readRadioData(event.getItemInHand());
        if (optData.isEmpty()) {
            return;
        }

        RadioData data = optData.get();
        data.rerollId(); // copies of an item (e.g. from pick block) must not share an id once placed.

        if (PaperRadioBlocks.writeRadioData(block, data)) {
            PaperRadioBlocks.startStream(this.plugin, block, data);
        }
    }

    // Right-clicking a radio toggles it.
    // Runs late so protection plugins get the chance to deny using the block first.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Like vanilla, sneaking with an item in either hand doesn't use the clicked block.
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        if (player.isSneaking() && (!inventory.getItemInMainHand().isEmpty() || !inventory.getItemInOffHand().isEmpty())) {
            return;
        }

        Optional<RadioData> optData = PaperRadioBlocks.readRadioData(block);
        if (optData.isEmpty()) {
            return;
        }

        RadioData data = optData.get();
        PaperRadioBlocks.toggle(this.plugin, block, data);
        String displayState = data.isOn() ? "on" : "off";

        block.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1F, 1F);
        player.sendActionBar(Component.text("Toggled radio %s.".formatted(displayState)));

        // Consume the interaction, so the held item isn't used as well (e.g. placing a block).
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    // Radios are toggled by a redstone impulse, like a copper bulb.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        // This fires for every block update on the server - bail out as cheaply as possible.
        if (!PaperRadioBlocks.isPlayerHead(event.getChangedType())) {
            return;
        }

        // The event fires before vanilla updates the skull's "powered" state to match its redstone signal,
        // so an unpowered skull receiving a signal is the rising edge of an impulse.
        if (!(event.getChangedBlockData() instanceof Powerable powerable) || powerable.isPowered()) {
            return;
        }

        Block block = event.getBlock();
        if (!block.isBlockIndirectlyPowered()) {
            return;
        }

        PaperRadioBlocks.readRadioData(block).ifPresent(data -> PaperRadioBlocks.toggle(this.plugin, block, data));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PaperRadioBlocks.readRadioData(event.getBlock())
                .ifPresent(data -> RadioManager.getInstance().onRemoveHead(data.getId()));
    }

    // Broken radios drop as a radio, rather than as a plain player head.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof Skull skull)) {
            return;
        }

        Optional<RadioData> optData = PaperRadioData.read(skull.getPersistentDataContainer());
        if (optData.isEmpty()) {
            return;
        }

        for (Item item : event.getItems()) {
            if (item.getItemStack().getType() == Material.PLAYER_HEAD) {
                item.setItemStack(PaperRadioItem.fromData(optData.get()));
                return;
            }
        }
    }

    // In creative, pick block on a radio always gives the radio with its data, no matter the ctrl modifier.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        Optional<RadioData> optData = PaperRadioBlocks.readRadioData(event.getBlock());
        if (optData.isEmpty()) {
            return;
        }

        // The event doesn't allow changing the picked item, so perform the pick here instead.
        // This mirrors what vanilla does in creative mode (Inventory#addAndPickItem).
        event.setCancelled(true);

        PlayerInventory inventory = player.getInventory();
        int targetSlot = event.getTargetSlot();
        inventory.setHeldItemSlot(targetSlot);

        ItemStack replaced = inventory.getItem(targetSlot);
        if (replaced != null && !replaced.isEmpty()) {
            int freeSlot = inventory.firstEmpty();
            if (freeSlot != -1) {
                inventory.setItem(freeSlot, replaced);
            }
        }

        inventory.setItem(targetSlot, PaperRadioItem.fromData(optData.get()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        PaperRadioBlocks.loadRadios(this.plugin, event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        RadioManager.getInstance().removeStreamsIf(radioStream ->
                radioStream.getLocation() instanceof PaperRadioLocation location && location.isInChunk(chunk));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        RadioManager.getInstance().removeStreamsIf(radioStream ->
                radioStream.getLocation() instanceof PaperRadioLocation location && location.world().equals(world));
    }
}
