package com.yapcore.fleet.ops;

import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.model.FleetNode;
import com.yapcore.fleet.remote.FleetAgentClient;
import com.yapcore.fleet.store.FleetStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Push plugin jars / config blobs to selected fleet instances (local copy or agent deploy). */
public final class FleetDeploy {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Deploy");

    private FleetDeploy() {
    }

    public static Map<String, Object> deployFile(
            Path rootDir,
            FleetStore store,
            FleetAgentClient agents,
            List<String> instanceIds,
            Path sourceFile,
            String relativeTarget,
            String restartPolicy) throws IOException {
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("Deploy source missing: " + sourceFile);
        }
        byte[] bytes = Files.readAllBytes(sourceFile);
        String checksum = sha256(bytes);
        String fileName = sourceFile.getFileName().toString();
        String targetRel = relativeTarget == null || relativeTarget.isBlank()
                ? "plugins/" + fileName
                : relativeTarget.trim();

        List<Map<String, Object>> results = new ArrayList<>();
        for (String id : instanceIds) {
            FleetInstance inst = store.findInstance(id)
                    .orElseThrow(() -> new IOException("Unknown instance: " + id));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instanceId", inst.id());
            row.put("nodeId", inst.nodeId());
            try {
                if (inst.isLocal()) {
                    Path dest = rootDir.resolve(inst.relativeDir()).resolve(targetRel).normalize();
                    Files.createDirectories(dest.getParent());
                    Files.write(dest, bytes);
                    row.put("ok", true);
                    row.put("path", dest.toString());
                } else {
                    FleetNode node = store.findNode(inst.nodeId())
                            .orElseThrow(() -> new IOException("Unknown node: " + inst.nodeId()));
                    Map<String, Object> resp = agents.deploy(node, inst.id(), targetRel, bytes, checksum);
                    row.put("ok", Boolean.TRUE.equals(resp.get("ok")));
                    row.put("agent", resp);
                }
                row.put("checksum", checksum);
            } catch (Exception e) {
                row.put("ok", false);
                row.put("error", e.getMessage());
            }
            results.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("ok"))));
        out.put("checksum", checksum);
        out.put("fileName", fileName);
        out.put("target", targetRel);
        out.put("restartPolicy", restartPolicy == null ? "none" : restartPolicy);
        out.put("results", results);
        LOG.info("Fleet deploy " + fileName + " → " + results.size() + " instance(s)");
        return out;
    }

    public static Map<String, Object> deployFromRootPlugins(
            Path rootDir,
            FleetStore store,
            FleetAgentClient agents,
            List<String> instanceIds,
            String jarName,
            String restartPolicy) throws IOException {
        Path src = rootDir.resolve("plugins").resolve(jarName);
        return deployFile(rootDir, store, agents, instanceIds, src, "plugins/" + jarName, restartPolicy);
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
