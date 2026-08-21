package com.yapcore.api;

import java.io.File;
import java.util.logging.Logger;

public record YaPPluginContext(
        YaPPluginDescription description,
        File dataFolder,
        File jarFile,
        YaPScheduler scheduler,
        Logger logger
) {
}
