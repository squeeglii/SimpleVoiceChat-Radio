package de.maxhenkel.radio.radio;

import com.tianscar.media.sound.AACAudioInputStream;
import com.tianscar.media.sound.DecodedAACAudioInputStream;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.RadioVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.UUID;
import java.util.function.Supplier;

public class RadioStream implements Supplier<short[]> {

    private static final int FRAME_SIZE = 960;
    private static final int AUDIO_FRAMES_PER_SECOND = 50; // 20000000ns -> 0.02s per frame
    private static final AudioFormat SVC_FORMAT = new AudioFormat(48000.0f, 16, 1, true, false);

    private final RadioData radioData;
    private final UUID id;
    private final ServerLevel serverLevel;
    private final BlockPos position;
    @Nullable
    private LocationalAudioChannel channel;
    @Nullable
    private AudioPlayer audioPlayer;
    @Nullable
    private AudioInputStream radioStationStream;
    @Nullable
    private AudioInputStream decodedRadioStationStream;
    @Nullable
    private AudioInputStream convertedStream;

    public RadioStream(RadioData radioData, ServerLevel serverLevel, BlockPos position) {
        this.radioData = radioData;
        this.id = radioData.getId();
        this.serverLevel = serverLevel;
        this.position = position;
    }

    public void init() {
        if (radioData.isOn()) {
            start();
        }
    }

    public void start() {
        new Thread(() -> {
            try {
                this.startInternal();
            } catch (IOException | URISyntaxException e) {
                Radio.LOGGER.error("Failed to start radio stream", e);
            }
        }, "RadioStreamStarter-%s".formatted(id)).start();

    }

    private void startInternal() throws IOException, URISyntaxException {
        if (this.radioData.getUrl() == null) {
            Radio.LOGGER.warn("Radio URL is null");
            return;
        }

        VoicechatServerApi api = RadioVoicechatPlugin.voicechatServerApi;
        if (api == null) {
            Radio.LOGGER.debug("Voice chat API is not yet loaded");
            RadioVoicechatPlugin.runWhenReady(this::start);
            return;
        }

        if (this.channel != null) {
            stop();
        }

        de.maxhenkel.voicechat.api.ServerLevel level = api.fromServerLevel(this.serverLevel);
        Position pos = api.createPosition(this.position.getX() + 0.5D, this.position.getY() + 0.5D, this.position.getZ() + 0.5D);
        this.channel = api.createLocationalAudioChannel(UUID.randomUUID(), level, pos);

        if(this.channel == null) {
            Radio.LOGGER.error("Failed to create locational audio channel for .");
            return;
        }

        this.channel.setDistance(this.getOutputChannelRange());
        this.channel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        this.audioPlayer = api.createAudioPlayer(this.channel, api.createEncoder(OpusEncoderMode.AUDIO), this);

        try {
            URI source = new URI(radioData.getUrl());
            this.radioStationStream = AudioSystem.getAudioInputStream(source.toURL());
            AudioFormat format = this.radioStationStream.getFormat();

            this.decodedRadioStationStream = AudioSystem.getAudioInputStream(
                    new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), format.getSampleSizeInBits(),
                            format.getChannels(), format.getChannels(), format.getFrameRate(),
                            format.isBigEndian(), format.properties()),
                    this.radioStationStream);


            this.convertedStream = AudioSystem.getAudioInputStream(SVC_FORMAT, this.decodedRadioStationStream);

        } catch (UnsupportedAudioFileException err) {
            Radio.LOGGER.error("Unable to stream radio from url '{}' - unsupported audio format! Try a radio station using 'MP3' or 'HE-ACC'.", radioData.getUrl());
            return;
        } catch (IllegalArgumentException err) {
            Radio.LOGGER.error(err);
            return;
        }

        if(audioPlayer == null) {
            Radio.LOGGER.error("Unable to start radio stream player -- audio player is null.");
            return;
        }

        this.audioPlayer.startPlaying();
    }

    public void stop() {
        channel = null;

        if (audioPlayer != null) {
            audioPlayer.stopPlaying();
            audioPlayer = null;
        }

        if (this.radioStationStream != null) {
            try {
                this.radioStationStream.close();
            } catch (Exception e) {
                Radio.LOGGER.warn("Failed to close radio station input stream", e);
            }
            this.radioStationStream = null;
        }

        if (this.decodedRadioStationStream != null) {
            try {
                this.decodedRadioStationStream.close();
            } catch (Exception e) {
                Radio.LOGGER.warn("Failed to close input decoder stream", e);
            }
            this.decodedRadioStationStream = null;
        }

        if (this.convertedStream != null) {
            try {
                this.convertedStream.close();
            } catch (Exception e) {
                Radio.LOGGER.warn("Failed to close input converter stream", e);
            }
            this.convertedStream = null;
        }

        Radio.LOGGER.debug("Stopped radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
    }

    public BlockPos getPosition() {
        return position;
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public RadioData getRadioData() {
        return radioData;
    }

    private int lastSampleCount;

    @Override
    public short[] get() {
        if (channel == null) {
            return null;
        }

        if (this.convertedStream == null || this.radioStationStream == null) {
            throw new IllegalStateException("Radio stream not started");
        }

        checkValid();
        spawnParticle();

        try {
            short[] frame = new short[FRAME_SIZE];
            byte[] readBuf = new byte[FRAME_SIZE*2];

            int bytesCopied = this.convertedStream.read(readBuf);

            if(bytesCopied == -1) {
                Radio.LOGGER.warn("End of converter audio stream!");
                stop();
                return null;
            }

            ShortBuffer shortBuffer = ByteBuffer.wrap(readBuf).asShortBuffer();
            shortBuffer.get(frame);

            return frame;

        } catch (Exception e) {
            Radio.LOGGER.warn("Failed to stream audio from {}", radioData.getUrl(), e);
            stop();
            return null;
        }
    }

    private long lastParticle = 0L;

    public void spawnParticle() {
        if (!Radio.SERVER_CONFIG.showMusicParticles.get()) {
            return;
        }
        long time = System.currentTimeMillis();
        if (time - lastParticle < Radio.SERVER_CONFIG.musicParticleFrequency.get()) {
            return;
        }
        lastParticle = time;
        serverLevel.getServer().execute(() -> {
            Vec3 vec3 = Vec3.atBottomCenterOf(position).add(0D, 1D, 0D);
            serverLevel.players().stream().filter(player -> player.position().distanceTo(position.getCenter()) <= 32D).forEach(player -> {
                float random = (float) serverLevel.getRandom().nextInt(4) / 24F;
                serverLevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, random, 0D, 0D, 1D);
            });
        });
    }

    private long lastCheck;

    private void checkValid() {
        long time = System.currentTimeMillis();
        if (time - lastCheck < 30000L) {
            return;
        }
        lastCheck = time;
        serverLevel.getServer().execute(() -> {
            if (!RadioManager.isValidRadioLocation(id, position, serverLevel)) {
                RadioManager.getInstance().stopStream(id);
                Radio.LOGGER.warn("Stopped radio stream {} as it doesn't exist anymore", id);
            }
        });
    }

    public void close() {
        stop();
    }


    private float getOutputChannelRange() {
        float range = this.radioData.getRange();
        return range > 0
                ? range
                : Radio.SERVER_CONFIG.radioRange.get().floatValue();
    }
}
