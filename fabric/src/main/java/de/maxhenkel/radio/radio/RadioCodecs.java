package de.maxhenkel.radio.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * NBT codecs for {@link RadioData}, stored under {@link RadioData#NBT_CATEGORY}.
 */
public final class RadioCodecs {

    /** Used for placed radio blocks. */
    public static final Codec<RadioData> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    UUIDUtil.CODEC.fieldOf("id").orElseGet(UUID::randomUUID).forGetter(RadioData::getId),
                    Codec.STRING.fieldOf("stream_url").forGetter(RadioData::getUrl),
                    Codec.STRING.fieldOf("name").forGetter(RadioData::getStationName),
                    Codec.BOOL.fieldOf("active").forGetter(RadioData::isOn),
                    Codec.FLOAT.fieldOf("range").forGetter(RadioData::getRange)
            ).apply(builder, RadioData::new)
    );

    /** Used for radio items - these don't store an id, a new one is assigned when they are placed. */
    public static final Codec<RadioData> ITEM_CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    Codec.STRING.fieldOf("stream_url").forGetter(RadioData::getUrl),
                    Codec.STRING.fieldOf("name").forGetter(RadioData::getStationName),
                    Codec.BOOL.fieldOf("active").forGetter(RadioData::isOn),
                    Codec.FLOAT.fieldOf("range").forGetter(RadioData::getRange)
            ).apply(builder, RadioData::createAnonymousRadioData)
    );

    private RadioCodecs() {

    }
}
