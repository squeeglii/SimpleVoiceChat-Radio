package de.maxhenkel.radio.paper;

import de.maxhenkel.radio.radio.RadioLocation;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public record PaperRadioLocation(Plugin plugin, World world, int x, int y, int z) implements RadioLocation {

    private static final double PARTICLE_PLAYER_RANGE_SQUARED = 32D * 32D;

    public static PaperRadioLocation of(Plugin plugin, Block block) {
        return new PaperRadioLocation(plugin, block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @Override
    public de.maxhenkel.voicechat.api.ServerLevel getVoicechatLevel(VoicechatServerApi api) {
        return api.fromServerLevel(this.world);
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getZ() {
        return this.z;
    }

    @Override
    public void runOnServerThread(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(this.plugin, task);
        } catch (IllegalPluginAccessException e) {
            // The audio thread can outlive the plugin while the server shuts down - nothing left to do then.
        }
    }

    @Override
    public void spawnMusicParticles() {
        Location center = new Location(this.world, this.x + 0.5D, this.y + 0.5D, this.z + 0.5D);
        boolean playerNearby = this.world.getPlayers().stream()
                .anyMatch(player -> player.getLocation().distanceSquared(center) <= PARTICLE_PLAYER_RANGE_SQUARED);
        if (!playerNearby) {
            return;
        }

        // With a count of 0, the x offset is used as the note's colour.
        double noteColour = ThreadLocalRandom.current().nextInt(4) / 24D;
        this.world.spawnParticle(Particle.NOTE, this.x + 0.5D, this.y + 1D, this.z + 0.5D, 0, noteColour, 0D, 0D, 1D);
    }

    @Override
    public boolean isRadioPresent(UUID radioId) {
        if (!this.world.isChunkLoaded(this.x >> 4, this.z >> 4)) {
            return false;
        }

        return PaperRadioBlocks.readRadioData(this.world.getBlockAt(this.x, this.y, this.z))
                .map(data -> data.getId().equals(radioId))
                .orElse(false);
    }

    public boolean isInChunk(Chunk chunk) {
        return this.world.equals(chunk.getWorld()) && (this.x >> 4) == chunk.getX() && (this.z >> 4) == chunk.getZ();
    }
}
