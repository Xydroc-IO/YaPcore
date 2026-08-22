package me.clip.placeholderapi.expansion;

/** Expansion with start/stop lifecycle tied to register/unregister. */
public interface Taskable {

    void start();

    void stop();
}
