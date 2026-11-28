package de.maxhenkel.radio;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.radio.command.RadioCommands;
import de.maxhenkel.radio.config.ServerConfig;
import de.maxhenkel.radio.events.LifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Radio.MODID)
public class Radio {

    public static final String MODID = "radio";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static ServerConfig SERVER_CONFIG;

    public Radio(IEventBus modBus) {
        SERVER_CONFIG = ConfigBuilder.build(FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("radio.properties"), ServerConfig::new);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Simple Voice Chat Radio initialized");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LifecycleEvents.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && event.getChunk() instanceof LevelChunk levelChunk) {
            LifecycleEvents.onChunkUnload(serverLevel, levelChunk);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RadioCommands.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }
}
