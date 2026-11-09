package de.maxhenkel.radio.radio;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import de.maxhenkel.radio.Radio;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads radio data stored in the pre-2.0 format, where it was embedded within the player head's game profile.
 * Legacy head loading is currently disabled - this is kept for reference.
 */
@Deprecated(since = "2.0")
public final class LegacyRadioData {

    public static final UUID RADIO_ID = UUID.fromString("e333ec57-548d-41a1-aa4a-05bce4cfd028");
    public static final String RADIO_NAME = "Radio";

    public static final String ID_TAG = "id";
    public static final String STREAM_URL_TAG = "stream_url";
    public static final String STATION_NAME_TAG = "station_name";
    public static final String ON_TAG = "on";
    public static final String RANGE_TAG = "range";

    private LegacyRadioData() {

    }

    @Nullable
    public static RadioData fromGameProfile(GameProfile gameProfile) {
        if (!hasLegacyRadioData(gameProfile)) {
            return null;
        }

        UUID uuid = UUID.randomUUID();
        String value = getValue(gameProfile, ID_TAG);
        if (value != null) {
            try {
                uuid = UUID.fromString(value);
            } catch (Exception e) {
                Radio.LOGGER.warn("Failed to parse UUID '{}'", value, e);
            }
        }

        RadioData radioData = new RadioData(
                uuid,
                getValue(gameProfile, STREAM_URL_TAG),
                getValue(gameProfile, STATION_NAME_TAG),
                Boolean.parseBoolean(getValue(gameProfile, ON_TAG)),
                getFloatValueOrElse(gameProfile, RANGE_TAG, -1.0f)
        );

        gameProfile.properties().removeAll(STREAM_URL_TAG);
        gameProfile.properties().removeAll(STATION_NAME_TAG);
        gameProfile.properties().removeAll(ON_TAG);
        gameProfile.properties().removeAll(RANGE_TAG);

        return radioData;
    }

    @Nullable
    private static String getValue(GameProfile gameProfile, String key) {
        return gameProfile.properties().get(key)
                .stream()
                .filter(Objects::nonNull)
                .map(Property::value)
                .findFirst()
                .orElse(null);
    }

    private static float getFloatValueOrElse(GameProfile gameProfile, String key, float orElse) {
        String value = getValue(gameProfile, key);

        if(value == null) return orElse;

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException err) {
            Radio.LOGGER.warn("Malformed radio data: %s".formatted(err.getMessage()));
            return orElse;
        }
    }

    public static boolean hasLegacyRadioData(GameProfile profile) {
        if (profile == null) {
            return false;
        }
        return profile.id().equals(RADIO_ID);
    }

}
