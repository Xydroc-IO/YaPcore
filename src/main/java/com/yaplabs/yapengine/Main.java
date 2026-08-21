package com.yaplabs.yapengine;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Standalone YapEngine bootstrap — initializes all 16 threads and runs the
 * High-Speed → Bridge → Game Core item-click simulation.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        configureLogging();
        Logger log = Logger.getLogger("YapEngine.Main");
        YapEngine engine = new YapEngine();
        engine.start();
        try {
            Thread.sleep(300);
            boolean ok = engine.runItemClickSimulation();
            Thread.sleep(400);
            log.info(ok ? "YapEngine demo completed successfully" : "YapEngine demo incomplete");
            if (!ok) {
                throw new IllegalStateException("YapEngine item-click simulation failed");
            }
        } finally {
            engine.stop();
        }
    }

    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        for (var h : root.getHandlers()) {
            root.removeHandler(h);
        }
        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.INFO);
        console.setFormatter(new SimpleFormatter());
        root.addHandler(console);
    }
}
