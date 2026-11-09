package de.maxhenkel.radio.utils;

public enum RadioStreamState {

    FRESH(false, false),
    ACTIVE(true, false),
    STOPPED(false, true),
    ERRORED_PRE_INIT(false, true),
    ERRORED(false, true),
    ERRORED_NO_CLEANUP(false, true);

    private final boolean isActive;
    private final boolean isSpent;

    RadioStreamState(boolean isActive, boolean isSpent) {
        this.isActive = isActive;
        this.isSpent = isSpent;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isSpent() {
        return this.isSpent;
    }

    public boolean canBeStarted() {
        return !this.isActive && !this.isSpent;
    }
}
