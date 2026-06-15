package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.Radio;
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
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.function.Supplier;

public class RadioStream implements Supplier<short[]> {

    private final RadioData radioData;
    private final UUID id;
    private final ServerLevel serverLevel;
    private final BlockPos position;

    private UUID lastKnownChannelId;
    private volatile RadioStreamState state;

    private long lastValidityCheck;

    @Nullable
    private volatile LocationalAudioChannel channel;
    @Nullable
    private AudioPlayer audioPlayer;
    @Nullable
    private volatile Bitstream bitstream;
    @Nullable
    private volatile Decoder decoder;
    @Nullable
    private volatile StreamConverter streamConverter;
    private volatile boolean reconnecting;

    public RadioStream(RadioData radioData, ServerLevel serverLevel, BlockPos position) {
        this.radioData = radioData;
        this.id = radioData.getId();
        this.serverLevel = serverLevel;
        this.position = position;

        this.lastKnownChannelId = Util.NIL_UUID;
        this.state = RadioStreamState.FRESH;
    }

    public void init() {
        if(!this.radioData.isOn()) return;

        if (this.state.canBeStarted()) {
            this.start();
        } else {
            Radio.LOGGER.warn("Tried to start pre-used radio station in state [{}]", this.state);
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
                Radio.LOGGER.error("Failed to setup radio stream", e);
                return;
            }

            new Thread(() -> {
                try {
                    this.state = this.startInternal(trace)
                            ? RadioStreamState.ACTIVE
                            : RadioStreamState.ERRORED;

                } catch (IOException | URISyntaxException e) {
                    this.state = RadioStreamState.ERRORED;
                    Radio.LOGGER.error("Failed to start radio stream", e);
                }
            }, "RadioStreamStarter-%s".formatted(id)).start();
        });

    }

    private boolean preStartInternal(Throwable trace) throws IOException, URISyntaxException {
        if (this.radioData.getUrl() == null) {
            Radio.LOGGER.warn("Radio URL is null");
            return false;
        }

        VoicechatServerApi api = RadioVoicechatPlugin.voicechatServerApi;
        if (api == null) {
            Radio.LOGGER.error("Voice chat API is not yet loaded");
            //RadioVoicechatPlugin.runWhenReady(this::start); -- #start() should account for this.
            return false;
        }

        if (this.channel != null) {
            Radio.LOGGER.warn("Voice channel exists already. Ignoring.");
            return false;
            //stop();
        }

        if(this.serverLevel == null) {
            Radio.LOGGER.error("Server level is null while trying to create radio channel");
            return false;
        }

        de.maxhenkel.voicechat.api.ServerLevel level = api.fromServerLevel(this.serverLevel);
        Position pos = api.createPosition(this.position.getX() + 0.5D, this.position.getY() + 0.5D, this.position.getZ() + 0.5D);
        this.lastKnownChannelId = UUID.randomUUID();
        this.channel = api.createLocationalAudioChannel(this.lastKnownChannelId, level, pos);

        if(this.channel == null) {
            Radio.LOGGER.error("Failed to create locational audio channel.", trace);
            return false;
        }

        this.channel.setDistance(this.getOutputChannelRange());
        this.channel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        this.audioPlayer = api.createAudioPlayer(this.channel, api.createEncoder(OpusEncoderMode.AUDIO), this);

        if(this.audioPlayer == null) {
            Radio.LOGGER.error("Could not initialise radio stream player -- audio player is null.", trace);
            return false;
        }

        return true;
    }

    private boolean startInternal(Throwable trace) throws IOException, URISyntaxException {
        if(this.audioPlayer == null) {
            Radio.LOGGER.debug("Unable to start radio stream player -- was the player halted too quickly?", trace);
            return false;
        }

        this.reconnecting = false;
        this.reconnectAttempts = 0;
        this.openDecoder();

        this.audioPlayer.startPlaying();
        this.state = RadioStreamState.ACTIVE;
        return true;
    }

    private void openDecoder() throws IOException {
        InputStream input = RadioConnection.open(this.radioData.getUrl());
        this.bitstream = new Bitstream(input);
        this.decoder = new Decoder();
    }

    public void stop() {
        Radio.LOGGER.debug("Stopping radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        channel = null;
        reconnecting = false;
        if (audioPlayer != null) {
            audioPlayer.stopPlaying();
            audioPlayer = null;
        }
        if (bitstream != null) {
            try {
                bitstream.close();
            } catch (Exception e) {
                Radio.LOGGER.warn("Failed to close bitstream", e);
            }
            bitstream = null;
        }
        decoder = null;
        streamConverter = null;

        // update state only if it hasn't yet been updated.
        if(this.state.isActive()) {
            this.state = RadioStreamState.STOPPED;
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

    private volatile int reconnectAttempts;

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 2000L;
    private static final short[] SILENCE = new short[StreamConverter.FRAME_SIZE_SAMPLES];
    private static final int MAX_DECODED_FRAME_SHORTS = 2304;

    @Override
    public short[] get() {
        LocationalAudioChannel channel = this.channel;
        if (channel == null) {
            return null;
        }
        checkValid();
        spawnParticle();

        if (reconnecting) {
            return SILENCE;
        }

        Bitstream bitstream = this.bitstream;
        Decoder decoder = this.decoder;
        if (bitstream == null || decoder == null) {
            throw new IllegalStateException("Radio stream not started");
        }

        try {
            StreamConverter converter = this.streamConverter;
            if (converter != null && !converter.canAdd(MAX_DECODED_FRAME_SHORTS)) {
                return converter.getFrame();
            }

            Header frameHeader = bitstream.readFrame();
            if (frameHeader == null) {
                beginReconnect(new IOException("End of stream"));
                return SILENCE;
            }

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
            short[] samples = output.getBuffer();
            int length = output.getBufferLength();
            bitstream.closeFrame();

            if (converter == null) {
                converter = new StreamConverter(decoder.getOutputFrequency(), decoder.getOutputChannels());
                this.streamConverter = converter;
            }

            converter.add(samples, 0, length);
            reconnectAttempts = 0;
            return converter.getFrame();
        } catch (Exception e) {
            beginReconnect(e);
            return SILENCE;
        }
    }

    private void beginReconnect(Exception cause) {
        if (reconnecting) {
            return;
        }
        reconnecting = true;

        Bitstream old = this.bitstream;
        this.bitstream = null;
        this.decoder = null;
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }

        Thread thread = new Thread(() -> runReconnect(cause), "RadioReconnect-%s".formatted(id));
        thread.setDaemon(true);
        thread.start();
    }

    private void runReconnect(Exception cause) {
        while (this.channel != null) {
            reconnectAttempts++;
            if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
                Radio.LOGGER.warn("Giving up on radio stream {} after {} reconnect attempts", radioData.getUrl(), reconnectAttempts - 1, cause);
                this.state = RadioStreamState.ERRORED;
                stop();
                reconnecting = false;
                return;
            }

            Radio.LOGGER.warn("Radio stream {} interrupted ({}). Reconnect attempt {}/{}",
                    radioData.getUrl(), cause.getMessage(), reconnectAttempts, MAX_RECONNECT_ATTEMPTS);

            try {
                InputStream input = RadioConnection.open(this.radioData.getUrl());
                Bitstream newBitstream = new Bitstream(input);
                Decoder newDecoder = new Decoder();

                if (this.channel == null) {
                    try {
                        newBitstream.close();
                    } catch (Exception ignored) {
                    }
                    reconnecting = false;
                    return;
                }

                this.streamConverter = null;
                this.decoder = newDecoder;
                this.bitstream = newBitstream;
                reconnecting = false;
                Radio.LOGGER.info("Reconnected radio stream {}", radioData.getUrl());
                return;
            } catch (Exception e) {
                cause = e;
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    reconnecting = false;
                    return;
                }
            }
        }
        reconnecting = false;
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

    private void checkValid() {
        long time = System.currentTimeMillis();
        if (time - this.lastValidityCheck < 30000L) {
            return;
        }

        this.lastValidityCheck = time;

        this.serverLevel.getServer().execute(() -> {
            if (!RadioManager.isValidRadioLocation(this.id, this.position, this.serverLevel)) {
                RadioManager.getInstance().stopStream(this.id);
                Radio.LOGGER.warn("Stopped radio stream {} as it doesn't exist anymore", this.id);
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
                : Radio.SERVER_CONFIG.radioRange.get().floatValue();
    }

    public UUID getLastKnownChannelId() {
        return this.lastKnownChannelId;
    }

    public RadioStreamState getState() {
        return this.state;
    }
}
