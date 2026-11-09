package de.maxhenkel.radio.paper;

import de.maxhenkel.radio.RadioCommon;
import de.maxhenkel.radio.radio.RadioData;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores {@link RadioData} in Bukkit's persistent data containers - used for both radio items and placed radio skulls.
 * The field names mirror the NBT codecs used by the Fabric mod.
 */
public final class PaperRadioData {

    private static final NamespacedKey DATA = key("data");
    private static final NamespacedKey ID = key("id");
    private static final NamespacedKey STREAM_URL = key("stream_url");
    private static final NamespacedKey STATION_NAME = key("name");
    private static final NamespacedKey ACTIVE = key("active");
    private static final NamespacedKey RANGE = key("range");

    private PaperRadioData() {

    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey(RadioCommon.MODID, key);
    }

    /**
     * @return the radio data stored in the container. Data without an id (e.g. from items) gets {@link RadioData#NIL_UUID}.
     */
    public static Optional<RadioData> read(PersistentDataContainerView holder) {
        if (!holder.has(DATA, PersistentDataType.TAG_CONTAINER)) {
            return Optional.empty();
        }

        PersistentDataContainer container = holder.get(DATA, PersistentDataType.TAG_CONTAINER);
        if (container == null
                || !container.has(STREAM_URL, PersistentDataType.STRING)
                || !container.has(STATION_NAME, PersistentDataType.STRING)) {
            return Optional.empty();
        }

        return Optional.of(new RadioData(
                readId(container),
                container.get(STREAM_URL, PersistentDataType.STRING),
                container.get(STATION_NAME, PersistentDataType.STRING),
                container.has(ACTIVE, PersistentDataType.BOOLEAN) && Boolean.TRUE.equals(container.get(ACTIVE, PersistentDataType.BOOLEAN)),
                container.has(RANGE, PersistentDataType.FLOAT) ? container.get(RANGE, PersistentDataType.FLOAT) : -1F
        ));
    }

    /**
     * Writes the radio data into the holder, replacing any radio data stored previously.
     *
     * @param includeId placed radios store their id, items don't - a new id is assigned when they're placed.
     */
    public static void write(PersistentDataContainer holder, RadioData data, boolean includeId) {
        PersistentDataContainer container = holder.getAdapterContext().newPersistentDataContainer();

        if (includeId) {
            container.set(ID, PersistentDataType.STRING, data.getId().toString());
        }
        if (data.getUrl() != null) {
            container.set(STREAM_URL, PersistentDataType.STRING, data.getUrl());
        }
        if (data.getStationName() != null) {
            container.set(STATION_NAME, PersistentDataType.STRING, data.getStationName());
        }
        container.set(ACTIVE, PersistentDataType.BOOLEAN, data.isOn());
        container.set(RANGE, PersistentDataType.FLOAT, data.getRange());

        holder.set(DATA, PersistentDataType.TAG_CONTAINER, container);
    }

    private static UUID readId(PersistentDataContainer container) {
        String id = container.has(ID, PersistentDataType.STRING) ? container.get(ID, PersistentDataType.STRING) : null;
        if (id == null) {
            return RadioData.NIL_UUID;
        }

        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            RadioCommon.LOGGER.warn("Failed to parse radio UUID '{}'", id, e);
            return RadioData.NIL_UUID;
        }
    }
}
