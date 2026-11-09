package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.RadioCommon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class RadioManager {

    private final Map<UUID, RadioStream> radioStreams;

    public RadioManager() {
        this.radioStreams = new HashMap<>();
    }

    /**
     * Opens a new audio stream for the provided radio data.
     * If any previous stream existed with the same id, it is deleted.
     * This can be used to refresh the properties of the stream if they are updated while a stream is already playing.
     * @return true if this method has modified the contents of the RadioData parameter.
     */
    public boolean updateRadioStream(RadioData radioData, RadioLocation location) {
        boolean idChanged = radioData.assignIdIfNil();

        RadioStream radioStream = new RadioStream(radioData, location);
        RadioCommon.LOGGER.debug("Loaded radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        radioStream.init();
        RadioStream oldStream = this.radioStreams.put(radioData.getId(), radioStream);

        if (oldStream != null) {
            oldStream.close();
            RadioCommon.LOGGER.warn("Replacing radio stream for '{}' ({})  - Old stream state was '{}'", radioData.getStationName(), radioData.getId(), oldStream.getState());
        }

        return idChanged;
    }

    public Optional<RadioStream> getRadioStream(UUID id) {
        return Optional.ofNullable(this.radioStreams.get(id));
    }

    public Optional<RadioStream> getRadioStream(RadioData radio) {
        return this.getRadioStream(radio.getId());
    }

    public void onRemoveHead(UUID id) {
        RadioStream radioStream = this.radioStreams.remove(id);

        if (radioStream != null) {
            radioStream.close();
            RadioCommon.LOGGER.debug("Removed radio stream for '{}' ({})", radioStream.getRadioData().getStationName(), radioStream.getRadioData().getId());
        } else {
            RadioCommon.LOGGER.debug("Removed radio stream {}", id);
        }
    }

    public void stopStream(UUID id) {
        RadioStream radioStream = radioStreams.get(id);
        if (radioStream != null) {
            radioStream.stop();
        }
    }

    public void updateHeadOnState(UUID id, boolean on) {
        RadioStream radioStream = this.radioStreams.get(id);
        if (radioStream == null) {
            RadioCommon.LOGGER.info("No stream detected.");
            return;
        }

        // Keeps the stream's copy in sync on platforms where it isn't the same instance that the block stores.
        radioStream.getRadioData().setOn(on);

        if (on) {
            radioStream.start();
        } else {
            radioStream.stop();
        }
    }

    /**
     * Closes and removes all streams matching the filter - e.g. all streams within an unloaded chunk.
     */
    public void removeStreamsIf(Predicate<RadioStream> filter) {
        radioStreams.values().removeIf(radioStream -> {
            boolean remove = filter.test(radioStream);
            if (remove) {
                radioStream.close();
            }
            return remove;
        });
    }

    public void clear() {
        this.radioStreams.values().forEach(RadioStream::close);
        this.radioStreams.clear();
    }

    private static RadioManager instance;

    public static RadioManager getInstance() {
        if (instance == null) {
            instance = new RadioManager();
        }
        return instance;
    }
}
