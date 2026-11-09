package de.maxhenkel.radio.events;

import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.radio.FabricRadioLocation;
import de.maxhenkel.radio.radio.RadioManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public class LifecycleEvents {

    public static void onServerStopping(MinecraftServer server) {
        Radio.LOGGER.info("Server stopping...");
        RadioManager.getInstance().clear();
    }

    public static void onChunkUnload(ServerLevel serverLevel, LevelChunk levelChunk) {
        ChunkPos chunkPos = levelChunk.getPos();
        RadioManager.getInstance().removeStreamsIf(radioStream ->
                radioStream.getLocation() instanceof FabricRadioLocation location && location.isInChunk(serverLevel, chunkPos));
    }
}
