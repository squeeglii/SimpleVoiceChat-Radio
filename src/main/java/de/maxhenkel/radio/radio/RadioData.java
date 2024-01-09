package de.maxhenkel.radio.radio;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.utils.HeadUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

public class RadioData {

    public static final UUID RADIO_ID = UUID.fromString("e333ec57-548d-41a1-aa4a-05bce4cfd028");
    public static final String RADIO_NAME = "Radio";

    public static final String ID_TAG = "id";
    public static final String STREAM_URL_TAG = "stream_url";
    public static final String STATION_NAME_TAG = "station_name";
    public static final String ON_TAG = "on";
    public static final String RANGE_TAG = "on";

    private final UUID id;
    private String url;
    private String stationName;
    private boolean on;
    private float range;

    public RadioData(UUID id, String url, String stationName, boolean on, float range) {
        this.id = id;
        this.url = url;
        this.stationName = stationName;
        this.on = on;
        this.range = range;
    }

    public RadioData(UUID id) {
        this.id = id;
        this.range = -1.0f;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getStationName() {
        return stationName;
    }

    public boolean isOn() {
        return on;
    }

    public float getRange() {
        return this.range;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    public void setRange(float range) {
        this.range = range;
    }

    @Nullable
    public static RadioData fromGameProfile(GameProfile gameProfile) {
        if (!isRadio(gameProfile)) {
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

        RadioData radioData = new RadioData(uuid);

        radioData.url = getValue(gameProfile, STREAM_URL_TAG);
        radioData.stationName = getValue(gameProfile, STATION_NAME_TAG);
        radioData.on = Boolean.parseBoolean(getValue(gameProfile, ON_TAG));
        radioData.range = getFloatValueOrElse(gameProfile, RANGE_TAG, -1.0f);

        return radioData;
    }

    public GameProfile toGameProfile() {
        GameProfile gameProfile = HeadUtils.getGameProfile(RADIO_ID, RADIO_NAME, Radio.SERVER_CONFIG.radioSkinUrl.get());
        updateProfile(gameProfile);
        return gameProfile;
    }

    public void updateProfile(GameProfile gameProfile) {
        if (id.equals(Util.NIL_UUID)) {
            removeValue(gameProfile, ID_TAG);
        } else {
            putValue(gameProfile, ID_TAG, this.id.toString());
        }

        putValue(gameProfile, STREAM_URL_TAG, this.url);
        putValue(gameProfile, STATION_NAME_TAG, this.stationName);
        putValue(gameProfile, ON_TAG, String.valueOf(this.on));
        putValue(gameProfile, RANGE_TAG, String.valueOf(this.range));
    }

    @Nullable
    private static String getValue(GameProfile gameProfile, String key) {
        return gameProfile.getProperties().get(key)
                .stream()
                .map(Property::getValue)
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

    private static void putValue(GameProfile gameProfile, String key, String value) {
        PropertyMap properties = gameProfile.getProperties();
        List<Property> props = new ArrayList<>();
        props.add(new Property(key, value));
        properties.replaceValues(key, props);
    }

    private static void removeValue(GameProfile gameProfile, String key) {
        PropertyMap properties = gameProfile.getProperties();
        properties.replaceValues(key, new ArrayList<>());
    }

    private static ItemStack createRadio(RadioData radioData) {
        return HeadUtils.createHead(
                RADIO_NAME,
                Collections.singletonList(
                        Component.literal(radioData.stationName)
                                 .withStyle(style -> style.withItalic(false))
                                 .withStyle(ChatFormatting.GRAY)
                ),
                radioData.toGameProfile()
        );
    }

    public ItemStack toItemWithNoId() {
        RadioData radioData = new RadioData(Util.NIL_UUID, this.url, this.stationName, false, this.range);
        return createRadio(radioData);
    }

    public static boolean isRadio(GameProfile profile) {
        if (profile == null) {
            return false;
        }
        return profile.getId().equals(RADIO_ID);
    }

}
