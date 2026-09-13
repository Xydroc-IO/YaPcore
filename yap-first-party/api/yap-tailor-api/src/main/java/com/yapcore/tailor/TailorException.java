package com.yapcore.tailor;

/** Checked failure from {@link TailorService} operations. */
public final class TailorException extends Exception {

    public TailorException(String message) {
        super(message);
    }

    public TailorException(String message, Throwable cause) {
        super(message, cause);
    }
}
