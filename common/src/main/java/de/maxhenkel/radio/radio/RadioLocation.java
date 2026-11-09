package de.maxhenkel.radio.radio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.UUID;

/**
 * Where a radio block is placed, and the platform specific (Fabric, Paper) operations a {@link RadioStream} needs to
 * perform on that world.
 */
public interface RadioLocation {

    /**
     * @return the voice chat representation of the level / world the radio is placed in.
     */
    de.maxhenkel.voicechat.api.ServerLevel getVoicechatLevel(VoicechatServerApi api);

    int getX();

    int getY();

    int getZ();

    /**
     * Runs the task on the server's main thread. Can be called from any thread (e.g. the audio thread).
     */
    void runOnServerThread(Runnable task);

    /**
     * Spawns music note particles above the radio. Only called on the server's main thread.
     */
    void spawnMusicParticles();

    /**
     * @return true if the radio with the given id is still placed at this location. Only called on the server's main
     * thread.
     */
    boolean isRadioPresent(UUID radioId);

}
