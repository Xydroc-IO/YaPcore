package me.clip.placeholderapi.expansion;

@Deprecated
public final class Version {

    private final boolean isSpigot;
    private final String version;

    public Version(String version, boolean isSpigot) {
        this.version = version;
        this.isSpigot = isSpigot;
    }

    public String getVersion() {
        return version == null ? "unknown" : version;
    }

    public boolean isSpigot() {
        return isSpigot;
    }

    public boolean compareTo(String other) {
        return getVersion().equalsIgnoreCase(other);
    }

    @Override
    public String toString() {
        return getVersion();
    }
}
