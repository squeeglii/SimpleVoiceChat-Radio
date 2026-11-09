package de.maxhenkel.radio.radio;

import java.util.UUID;

/**
 * The data stored on a radio item / block.
 * Each platform persists this in its own way (Fabric: NBT codecs, Paper: persistent data containers).
 */
public class RadioData {

    /** The id of radio data that hasn't been assigned an id yet (e.g. data stored on items). */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    public static final String NBT_CATEGORY = "radio";

    private UUID id;
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

    public boolean assignIdIfNil() {
        if(this.isAnonymous()) {
            this.id = UUID.randomUUID();
            return true;
        }

        return false;
    }

    public boolean isAnonymous() {
        return NIL_UUID.equals(this.id);
    }

    public void rerollId() {
        this.id = UUID.randomUUID();
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

    public static RadioData createAnonymousRadioData(String url, String stationName, boolean on, float range) {
        return new RadioData(NIL_UUID, url, stationName, on, range);
    }

}
