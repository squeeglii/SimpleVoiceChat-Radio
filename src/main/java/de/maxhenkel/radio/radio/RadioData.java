package de.maxhenkel.radio.radio;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.maxhenkel.radio.Radio;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.*;

public class RadioData {

    public static final UUID RADIO_ID = UUID.fromString("e333ec57-548d-41a1-aa4a-05bce4cfd028");
    public static final String RADIO_NAME = "Radio";

    public static final String NBT_CATEGORY = "radio";
    public static final String ID_TAG = "id";
    public static final String STREAM_URL_TAG = "stream_url";
    public static final String STATION_NAME_TAG = "station_name";
    public static final String ON_TAG = "on";
    public static final String RANGE_TAG = "range";

    private UUID id;
    private String url;
    private String stationName;
    private boolean on;
    private float range;

    public static final Codec<RadioData> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    UUIDUtil.CODEC.fieldOf("id").orElseGet(UUID::randomUUID).forGetter(RadioData::getId),
                    Codec.STRING.fieldOf("stream_url").forGetter(RadioData::getUrl),
                    Codec.STRING.fieldOf("name").forGetter(RadioData::getStationName),
                    Codec.BOOL.fieldOf("active").forGetter(RadioData::isOn),
                    Codec.FLOAT.fieldOf("range").forGetter(RadioData::getRange)
            ).apply(builder, RadioData::new)
    );

    public static final Codec<RadioData> ITEM_CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    Codec.STRING.fieldOf("stream_url").forGetter(RadioData::getUrl),
                    Codec.STRING.fieldOf("name").forGetter(RadioData::getStationName),
                    Codec.BOOL.fieldOf("active").forGetter(RadioData::isOn),
                    Codec.FLOAT.fieldOf("range").forGetter(RadioData::getRange)
            ).apply(builder, RadioData::createAnonymousRadioData)
    );

    public RadioData(UUID id, String url, String stationName, boolean on, float range) {
        this.id = id;
        this.url = url;
        this.stationName = stationName;
        this.on = on;
        this.range = range;
    }

    @Deprecated(since = "2.0")
    private RadioData(UUID id) {
        this.id = id;
        this.range = -1.0f;
    }

    public boolean assignIdIfNil() {
        if(this.id == Util.NIL_UUID) {
            this.id = UUID.randomUUID();
            return true;
        }

        return false;
    }

    public void serialiseIntoItemStack(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        CustomData newData = this.saveToNewItemData(data);
        item.set(DataComponents.CUSTOM_DATA, newData);
    }

    public CustomData saveToNewItemData() {
        return this.saveToNewItemData(null);
    }

    public CustomData saveToNewItemData(CustomData mergeWith) {
        CompoundTag workingTag = mergeWith == null
                ? new CompoundTag()
                : mergeWith.copyTag();

        workingTag.store(NBT_CATEGORY, ITEM_CODEC, this);

        return CustomData.of(workingTag);
    }

    public void toggleOn() {
        this.on = !this.on;
    }

    public boolean isRangeDefault() {
        return this.range < 0;
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

    public List<Component> toMessage(BlockPos blockPos) {
        ChatFormatting[] body = { ChatFormatting.GRAY, ChatFormatting.ITALIC };
        ChatFormatting[] title = { ChatFormatting.DARK_GRAY, ChatFormatting.UNDERLINE };

        String range = this.isRangeDefault() ? "<default>" : "%.1f block(s)".formatted(this.range);

        LinkedList<Component> message = new LinkedList<>(List.of(
                Component.literal("-- Radio Block Data at [%s]:\n".formatted(blockPos.toShortString())).withStyle(title),

                Component.literal("UUID:  %s\n".formatted(this.id)).withStyle(body),
                Component.literal("Name:  '%s'\n".formatted(this.stationName)).withStyle(body),
                Component.literal("URL:  '%s'\n".formatted(this.url)).withStyle(body),
                Component.literal("Range:  %s\n".formatted(range)).withStyle(body),
                Component.literal("On?  %s\n\n".formatted(this.isOn())).withStyle(body)
        ));

        Optional<RadioStream> optStream = RadioManager.getInstance().getRadioStream(this);

        message.add(Component.literal("-- Radio Stream Data:\n").withStyle(title));

        if(optStream.isPresent()) {
            RadioStream stream = optStream.get();
            message.addAll(List.of(
                    Component.literal("Channel Id:  %s\n".formatted(stream.getLastKnownChannelId())).withStyle(body),
                    Component.literal("State:  '%s'\n".formatted(stream.getState())).withStyle(body),
                    Component.literal("Range:  %s".formatted(stream.getOutputChannelRange())).withStyle(body)
            ));
        } else {
            message.add(Component.literal("...no radio stream found...").withStyle(body));
        }

        return message;
    }



    @Deprecated(since = "2.0")
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

        RadioData radioData = new RadioData(uuid);

        radioData.url = getValue(gameProfile, STREAM_URL_TAG);
        radioData.stationName = getValue(gameProfile, STATION_NAME_TAG);
        radioData.on = Boolean.parseBoolean(getValue(gameProfile, ON_TAG));
        radioData.range = getFloatValueOrElse(gameProfile, RANGE_TAG, -1.0f);

        gameProfile.properties().removeAll(STREAM_URL_TAG);
        gameProfile.properties().removeAll(STATION_NAME_TAG);
        gameProfile.properties().removeAll(ON_TAG);
        gameProfile.properties().removeAll(RANGE_TAG);

        return radioData;
    }

    @Deprecated(since = "2.0")
    @Nullable
    private static String getValue(GameProfile gameProfile, String key) {
        return gameProfile.properties().get(key)
                .stream()
                .filter(Objects::nonNull)
                .map(Property::value)
                .findFirst()
                .orElse(null);
    }

    @Deprecated(since = "2.0")
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

    public static RadioData createAnonymousRadioData(String url, String stationName, boolean on, float range) {
        return new RadioData(Util.NIL_UUID, url, stationName, on, range);
    }

}
