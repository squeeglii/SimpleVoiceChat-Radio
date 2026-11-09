package de.maxhenkel.radio;

import de.maxhenkel.radio.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State shared by every platform (the Fabric mod and the Paper plugin).
 */
public final class RadioCommon {

    public static final String MODID = "radio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    /** Loaded by the platform's entrypoint when it initialises. */
    public static ServerConfig SERVER_CONFIG;

    private RadioCommon() {

    }
}
