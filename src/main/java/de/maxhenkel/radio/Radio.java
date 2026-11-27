package de.maxhenkel.radio;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.radio.command.RadioCommands;
import de.maxhenkel.radio.config.ServerConfig;
import de.maxhenkel.radio.events.LifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Radio.MODID)
public class Radio {

    public static final String MODID = "radio";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static ServerConfig SERVER_CONFIG;

    public Radio() {
        SERVER_CONFIG = ConfigBuilder.build(FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("radio.properties"), ServerConfig::new);
        ServerStoppingEvent.BUS.addListener(Radio::onServerStopping);
        ChunkEvent.Unload.BUS.addListener(Radio::onChunkUnload);
        RegisterCommandsEvent.BUS.addListener(Radio::onRegisterCommands);
        LOGGER.info("Simple Voice Chat Radio initialized");
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        LifecycleEvents.onServerStopping(event.getServer());
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && event.getChunk() instanceof LevelChunk levelChunk) {
            LifecycleEvents.onChunkUnload(serverLevel, levelChunk);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        RadioCommands.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }
}
