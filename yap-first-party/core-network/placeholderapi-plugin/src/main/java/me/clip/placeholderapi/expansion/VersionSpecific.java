package me.clip.placeholderapi.expansion;

/** Optional NMS / version gate for expansions. */
public interface VersionSpecific {

    boolean isCompatibleWith(Version version);
}
