package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.RadioCommon;
import de.maxhenkel.radio.RadioVoicechatPlugin;
import de.maxhenkel.radio.utils.RadioStreamState;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.function.Supplier;

public class RadioStream implements Supplier<short[]> {

    private final RadioData radioData;
    private final UUID id;
    private final RadioLocation location;

    private UUID lastKnownChannelId;
    private RadioStreamState state;

    private long lastValidityCheck;

    @Nullable
    private LocationalAudioChannel channel;
    @Nullable
    private AudioPlayer audioPlayer;
    @Nullable
    private Bitstream bitstream;
    @Nullable
    private Decoder decoder;
    @Nullable
    private StreamConverter streamConverter;

    public RadioStream(RadioData radioData, RadioLocation location) {
        this.radioData = radioData;
        this.id = radioData.getId();
        this.location = location;

        this.lastKnownChannelId = RadioData.NIL_UUID;
        this.state = RadioStreamState.FRESH;
    }

    public void init() {
        if(!this.radioData.isOn()) return;

        if (this.state.canBeStarted()) {
            this.start();
        } else {
            RadioCommon.LOGGER.warn("Tried to start pre-used radio station in state [{}]", this.state);
        }
    }

    public void start() {
        final Throwable trace = new Throwable();

        RadioVoicechatPlugin.runWhenReady(() -> {
            try {
                if(!this.preStartInternal(trace)) {
                    this.state = RadioStreamState.ERRORED_PRE_INIT;
                    return;
                }
            } catch (IOException | URISyntaxException e) {
                this.state = RadioStreamState.ERRORED_PRE_INIT;
                RadioCommon.LOGGER.error("Failed to setup radio stream", e);
                return;
            }

            new Thread(() -> {
                try {
                    this.state = this.startInternal(trace)
                            ? RadioStreamState.ACTIVE
                            : RadioStreamState.ERRORED;

                } catch (IOException | URISyntaxException e) {
                    this.state = RadioStreamState.ERRORED;
                    RadioCommon.LOGGER.error("Failed to start radio stream", e);
                }
            }, "RadioStreamStarter-%s".formatted(id)).start();
        });

    }

    private boolean preStartInternal(Throwable trace) throws IOException, URISyntaxException {
        if (this.radioData.getUrl() == null) {
            RadioCommon.LOGGER.warn("Radio URL is null");
            return false;
        }

        VoicechatServerApi api = RadioVoicechatPlugin.voicechatServerApi;
        if (api == null) {
            RadioCommon.LOGGER.error("Voice chat API is not yet loaded");
            //RadioVoicechatPlugin.runWhenReady(this::start); -- #start() should account for this.
            return false;
        }

        if (this.channel != null) {
            RadioCommon.LOGGER.warn("Voice channel exists already. Ignoring.");
            return false;
            //stop();
        }

        if(this.location == null) {
            RadioCommon.LOGGER.error("Radio location is null while trying to create radio channel");
            return false;
        }

        de.maxhenkel.voicechat.api.ServerLevel level = this.location.getVoicechatLevel(api);
        Position pos = api.createPosition(this.location.getX() + 0.5D, this.location.getY() + 0.5D, this.location.getZ() + 0.5D);
        this.lastKnownChannelId = UUID.randomUUID();
        this.channel = api.createLocationalAudioChannel(this.lastKnownChannelId, level, pos);

        if(this.channel == null) {
            RadioCommon.LOGGER.error("Failed to create locational audio channel.", trace);
            return false;
        }

        this.channel.setDistance(this.getOutputChannelRange());
        this.channel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        this.audioPlayer = api.createAudioPlayer(this.channel, api.createEncoder(OpusEncoderMode.AUDIO), this);

        if(this.audioPlayer == null) {
            RadioCommon.LOGGER.error("Could not initialise radio stream player -- audio player is null.", trace);
            return false;
        }

        return true;
    }

    private boolean startInternal(Throwable trace) throws IOException, URISyntaxException {
        if(this.audioPlayer == null) {
            RadioCommon.LOGGER.debug("Unable to start radio stream player -- was the player halted too quickly?", trace);
            return false;
        }

        InputStream input = new URI(this.radioData.getUrl()).toURL().openStream();
        Bitstream bitstream = new Bitstream(new BufferedInputStream(input));

        // Connecting can take a while, so the stream may have been stopped in the meantime
        // (e.g. its chunk was unloaded again). Synchronised with stop() so this check can't race it.
        synchronized (this) {
            if (this.audioPlayer == null) {
                RadioCommon.LOGGER.debug("Radio stream was stopped while connecting", trace);
                try {
                    bitstream.close();
                } catch (Exception e) {
                    RadioCommon.LOGGER.warn("Failed to close bitstream", e);
                }
                return false;
            }

            this.bitstream = bitstream;
            this.decoder = new Decoder();

            this.audioPlayer.startPlaying();
            this.state = RadioStreamState.ACTIVE;
            return true;
        }
    }

    public synchronized void stop() {
        RadioCommon.LOGGER.debug("Stopping radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        channel = null;
        if (audioPlayer != null) {
            audioPlayer.stopPlaying();
            audioPlayer = null;
        }
        if (bitstream != null) {
            try {
                bitstream.close();
            } catch (Exception e) {
                RadioCommon.LOGGER.warn("Failed to close bitstream", e);
            }
            bitstream = null;
        }
        decoder = null;
        streamConverter = null;

        // update state only if it hasn't yet been updated.
        if(this.state.isActive()) {
            this.state = RadioStreamState.STOPPED;
        }
        RadioCommon.LOGGER.debug("Stopped radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
    }

    public RadioLocation getLocation() {
        return location;
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
        if (bitstream == null || decoder == null) {
            throw new IllegalStateException("Radio stream not started");
        }
        checkValid();
        spawnParticle();
        try {
            if (streamConverter != null) {
                if (!streamConverter.canAdd(lastSampleCount)) {
                    return streamConverter.getFrame();
                }
            }

            Header frameHeader = bitstream.readFrame();
            if (frameHeader == null) {
                this.state = RadioStreamState.ERRORED_NO_CLEANUP;
                throw new IOException("End of stream");
            }

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
            short[] samples = output.getBuffer();
            lastSampleCount = output.getBufferLength();
            bitstream.closeFrame();

            if (streamConverter == null) {
                streamConverter = new StreamConverter(decoder.getOutputFrequency(), decoder.getOutputChannels());
            }

            streamConverter.add(samples, 0, output.getBufferLength());
            return streamConverter.getFrame();
        } catch (Exception e) {
            RadioCommon.LOGGER.warn("Failed to stream audio from {}", radioData.getUrl(), e);
            stop();
            this.state = RadioStreamState.ERRORED;
            return null;
        }
    }

    private long lastParticle = 0L;

    public void spawnParticle() {
        if (!RadioCommon.SERVER_CONFIG.showMusicParticles.get()) {
            return;
        }
        long time = System.currentTimeMillis();
        if (time - lastParticle < RadioCommon.SERVER_CONFIG.musicParticleFrequency.get()) {
            return;
        }
        lastParticle = time;
        this.location.runOnServerThread(this.location::spawnMusicParticles);
    }

    private void checkValid() {
        long time = System.currentTimeMillis();
        if (time - this.lastValidityCheck < 30000L) {
            return;
        }

        this.lastValidityCheck = time;

        this.location.runOnServerThread(() -> {
            if (!this.location.isRadioPresent(this.id)) {
                RadioManager.getInstance().stopStream(this.id);
                RadioCommon.LOGGER.warn("Stopped radio stream {} as it doesn't exist anymore", this.id);
            }
        });
    }

    public void close() {
        stop();
    }


    public float getOutputChannelRange() {
        float range = this.radioData.getRange();
        return range > 0
                ? range
                : RadioCommon.SERVER_CONFIG.radioRange.get().floatValue();
    }

    public UUID getLastKnownChannelId() {
        return this.lastKnownChannelId;
    }

    public RadioStreamState getState() {
        return this.state;
    }
}
