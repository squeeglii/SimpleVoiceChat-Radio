package de.maxhenkel.radio;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.radio.command.RadioCommands;
import de.maxhenkel.radio.config.ServerConfig;
import de.maxhenkel.radio.events.LifecycleEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Radio implements ModInitializer {

    public static final String MODID = "radio";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static ServerConfig SERVER_CONFIG;

    @Override
    public void onInitialize() {
        SERVER_CONFIG = ConfigBuilder.build(FabricLoader.getInstance().getConfigDir().resolve(MODID).resolve("radio.properties"), ServerConfig::new);

        ServerLifecycleEvents.SERVER_STOPPING.register(LifecycleEvents::onServerStopping);
        CommandRegistrationCallback.EVENT.register(RadioCommands::register);
        ServerChunkEvents.CHUNK_UNLOAD.register(LifecycleEvents::onChunkUnload);
    }
}
