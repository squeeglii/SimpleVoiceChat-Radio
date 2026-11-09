package de.maxhenkel.radio.config;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.configbuilder.ConfigEntry;

public class ServerConfig {

    public final ConfigEntry<Double> radioRange;
    public final ConfigEntry<Integer> commandPermissionLevel;
    public final ConfigEntry<String> radioSkinUrl;
    public final ConfigEntry<Boolean> showMusicParticles;
    public final ConfigEntry<Long> musicParticleFrequency;

    public ServerConfig(ConfigBuilder builder) {
        radioRange = builder.doubleEntry(
                "radio_range",
                48D,
                1D,
                Double.MAX_VALUE,
                "The audible range of radios"
        );
        commandPermissionLevel = builder.integerEntry(
                "command_permission_level",
                0,
                0,
                Integer.MAX_VALUE,
                "The permission level required to use the radio command"
        );
        radioSkinUrl = builder.stringEntry(
                "radio_skin_url",
                "http://textures.minecraft.net/texture/148a8c55891dec76764449f57ba677be3ee88a06921ca93b6cc7c9611a7af",
                "The skin url for the radio block"
        );
        showMusicParticles = builder.booleanEntry(
                "show_music_particles",
                true,
                "Whether to show music particles"
        );
        musicParticleFrequency = builder.longEntry(
                "music_particle_frequency",
                2000L,
                500L,
                Long.MAX_VALUE,
                "The frequency of the music particles in milliseconds"
        );
    }

}
