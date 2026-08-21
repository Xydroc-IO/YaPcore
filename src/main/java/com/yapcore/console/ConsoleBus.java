package com.yapcore.console;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures JUL log lines for the live GUI / headless console.
 */
public final class ConsoleBus {

    private static final ConsoleBus INSTANCE = new ConsoleBus();
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder recent = new StringBuilder(64 * 1024);
    private static final int MAX_RECENT_CHARS = 200_000;

    private ConsoleBus() {
    }

    public static ConsoleBus get() {
        return INSTANCE;
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public synchronized String getRecentText() {
        return recent.toString();
    }

    public void publish(String line) {
        String stamped = line.endsWith("\n") ? line : line + "\n";
        synchronized (this) {
            recent.append(stamped);
            if (recent.length() > MAX_RECENT_CHARS) {
                recent.delete(0, recent.length() - MAX_RECENT_CHARS);
            }
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(stamped);
            } catch (RuntimeException ignored) {
                // UI listeners must not break logging
            }
        }
    }

    public void installAsJulHandler() {
        Logger root = Logger.getLogger("");
        root.addHandler(new BusHandler());
    }

    private final class BusHandler extends Handler {
        private final Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                String time = TIME.format(Instant.ofEpochMilli(record.getMillis()));
                String msg = formatMessage(record);
                Throwable thrown = record.getThrown();
                StringBuilder sb = new StringBuilder();
                sb.append('[').append(time).append("] [")
                        .append(record.getLevel().getName()).append("] ")
                        .append(msg);
                if (thrown != null) {
                    sb.append(" (").append(thrown.getClass().getSimpleName())
                            .append(": ").append(thrown.getMessage()).append(')');
                }
                return sb.toString();
            }
        };

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            ConsoleBus.this.publish(formatter.format(record));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
