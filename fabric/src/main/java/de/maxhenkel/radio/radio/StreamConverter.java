package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.utils.CircularShortBuffer;

public class StreamConverter {

    public static final int FRAME_SIZE_SAMPLES = 960;
    private static final int TARGET_SAMPLE_RATE = 48000;

    private final int inputSampleRate;
    private final int inputChannels;
    private final double resampleFactor;
    private final int sourceSamplesNeeded;
    private final CircularShortBuffer buffer;

    public StreamConverter(int inputSampleRate, int inputChannels) {
        if (inputChannels < 1 || inputChannels > 2) {
            throw new IllegalArgumentException("Only mono and stereo are supported");
        }
        if (inputSampleRate < 1) {
            throw new IllegalArgumentException("Sample rate must be greater than 0");
        }
        this.inputSampleRate = inputSampleRate;
        this.inputChannels = inputChannels;
        this.resampleFactor = (double) inputSampleRate / TARGET_SAMPLE_RATE;
        this.sourceSamplesNeeded = (int) (FRAME_SIZE_SAMPLES * resampleFactor) * inputChannels;
        this.buffer = new CircularShortBuffer(1024 * 64);
    }

    public boolean canAdd(int sampleCount) {
        return buffer.getFreeSpace() >= sampleCount;
    }

    public void add(short[] samples, int offset, int length) {
        buffer.add(samples, offset, length);
    }

    public void add(short[] samples) {
        add(samples, 0, samples.length);
    }

    public short[] getFrame() {
        if (inputSampleRate == TARGET_SAMPLE_RATE && inputChannels == 1) {
            if (buffer.sizeUsed() < FRAME_SIZE_SAMPLES) {
                Radio.LOGGER.debug("Not enough samples in buffer - waiting");
                return new short[FRAME_SIZE_SAMPLES];
            }
            short[] samples = new short[FRAME_SIZE_SAMPLES];
            buffer.get(samples);
            return samples;
        }

        if (buffer.sizeUsed() < sourceSamplesNeeded) {
            Radio.LOGGER.debug("Not enough samples in buffer - waiting");
            return new short[FRAME_SIZE_SAMPLES];
        }

        short[] convertedAudio = new short[FRAME_SIZE_SAMPLES];
        short[] audioData = new short[sourceSamplesNeeded];
        buffer.get(audioData);

        for (int i = 0; i < FRAME_SIZE_SAMPLES; i++) {
            double sourceIndex = i * inputChannels * resampleFactor;
            int sourceIndexFloor = (int) Math.floor(sourceIndex);
            int sourceIndexCeil = (int) Math.ceil(sourceIndex);

            if (sourceIndexCeil >= audioData.length - inputChannels) {
                sourceIndexCeil = audioData.length - inputChannels;
                sourceIndexFloor = audioData.length - inputChannels - 1;
            } else if (sourceIndexFloor < 0) {
                sourceIndexFloor = 0;
                sourceIndexCeil = 1;
            }
            double fraction = sourceIndex - sourceIndexFloor;
            if (inputChannels == 1) {
                convertedAudio[i] = (short) (audioData[sourceIndexFloor] + fraction * (audioData[sourceIndexCeil] - audioData[sourceIndexFloor]));
            } else if (inputChannels == 2) {
                short interpolatedAudioLeft = (short) (audioData[sourceIndexFloor] + fraction * (audioData[sourceIndexCeil] - audioData[sourceIndexFloor]));
                short interpolatedAudioRight = (short) (audioData[sourceIndexFloor + 1] + fraction * (audioData[sourceIndexCeil + 1] - audioData[sourceIndexFloor + 1]));
                convertedAudio[i] = (short) ((interpolatedAudioLeft + interpolatedAudioRight) / 2);
            }
        }

        return convertedAudio;
    }

}
