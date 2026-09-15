package com.yapcore.paper;

import com.yapcore.config.ServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PaperOpsTest {

    @TempDir
    Path temp;

    @Test
    void ensurePreservesExistingOpsNotInChassisList() throws Exception {
        Path paper = temp.resolve("paper");
        Files.createDirectories(paper);

        UUID kept = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Files.writeString(paper.resolve("ops.json"), """
                [
                  {
                    "uuid": "%s",
                    "name": "XYDROC",
                    "level": 4,
                    "bypassesPlayerLimit": false
                  }
                ]
                """.formatted(kept), StandardCharsets.UTF_8);

        Path cfgFile = temp.resolve("server.properties");
        Files.writeString(cfgFile, "ops=MadHatter\nauto-op=false\n", StandardCharsets.UTF_8);
        ServerConfig config = new ServerConfig(cfgFile);
        config.load();

        PaperOps.ensure(paper, config);

        String out = Files.readString(paper.resolve("ops.json"));
        assertTrue(out.contains(kept.toString()), "must keep existing OP uuid");
        assertTrue(out.contains("XYDROC"), "must keep existing OP name");
        assertTrue(out.contains("MadHatter"), "must add chassis ops= name");
        assertTrue(out.contains(PaperOps.offlineUuid("MadHatter").toString()),
                "must seed offline uuid for managed name");
    }
}
