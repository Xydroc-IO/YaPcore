package com.yapcore.config;

import java.nio.file.Path;
import java.util.Map;

/** CLI entry for {@link BedrockModeApplier} used by {@code scripts/setup/set-bedrock-mode.sh}. */
public final class BedrockModeApplierCli {

    private BedrockModeApplierCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: BedrockModeApplierCli <root> <linkHome> <server.properties> <mode>");
            System.exit(2);
        }
        Map<String, Object> out = BedrockModeApplier.apply(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), args[3]);
        out.forEach((k, v) -> System.out.println(k + "=" + v));
        if (out.containsKey("warning")) {
            System.err.println(out.get("warning"));
        }
    }
}
