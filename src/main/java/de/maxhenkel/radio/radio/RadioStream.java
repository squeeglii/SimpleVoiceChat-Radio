package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.RadioVoicechatPlugin;
import de.maxhenkel.radio.utils.HeadUtils;
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
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

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
    private final ServerLevel serverLevel;
    private final BlockPos position;

    private UUID lastKnownChannelId;
    private RadioStreamState state;

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

        InputStream input = new URI(this.radioData.getUrl()).toURL().openStream();
        this.bitstream = new Bitstream(new BufferedInputStream(input));
        this.decoder = new Decoder();

        this.audioPlayer.startPlaying();
        this.state = RadioStreamState.ACTIVE;
        return true;
    }

    public void stop() {
        Radio.LOGGER.debug("Stopping radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        channel = null;
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
            Radio.LOGGER.warn("Failed to stream audio from {}", radioData.getUrl(), e);
            stop();
            this.state = RadioStreamState.ERRORED;
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
