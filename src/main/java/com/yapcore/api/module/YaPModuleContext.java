package com.yapcore.api.module;

import com.yapcore.api.YaPScheduler;

import java.io.File;
import java.util.logging.Logger;

public record YaPModuleContext(
        YaPModuleDescription description,
        File dataFolder,
        File jarFile,
        YaPScheduler scheduler,
        Logger logger
) {
}
