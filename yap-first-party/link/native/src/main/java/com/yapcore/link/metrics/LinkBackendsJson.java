package com.yapcore.link.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.status.ServerStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON body for {@code GET /backends} — live BackendMonitor snapshots for the chassis fleet UI. */
public final class LinkBackendsJson {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LinkBackendsJson() {
    }

    public static String render(BackendMonitor monitor) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> backends = new ArrayList<>();
        if (monitor != null) {
            for (Map.Entry<String, BackendMonitor.Snapshot> e : monitor.allSnapshots().entrySet()) {
                backends.add(toMap(e.getKey(), e.getValue()));
            }
            backends.sort((a, b) -> String.valueOf(a.get("name"))
                    .compareToIgnoreCase(String.valueOf(b.get("name"))));
        }
        root.put("ok", true);
        root.put("backends", backends);
        root.put("checkedAtMs", System.currentTimeMillis());
        return GSON.toJson(root);
    }

    public static Map<String, Object> toMap(String name, BackendMonitor.Snapshot snap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("up", snap != null && snap.up());
        m.put("checkedAtMs", snap != null ? snap.checkedAtMs() : 0L);
        m.put("latencyMs", snap != null ? snap.latencyMs() : -1L);
        m.put("error", snap != null ? snap.error() : "never probed");
        ServerStatus st = snap != null ? snap.status() : null;
        if (st != null) {
            m.put("online", st.online());
            m.put("max", st.max());
            m.put("versionName", st.versionName());
            m.put("protocol", st.protocol());
            m.put("motd", st.descriptionText());
        } else {
            m.put("online", 0);
            m.put("max", 0);
            m.put("versionName", "");
            m.put("protocol", 0);
            m.put("motd", "");
        }
        return m;
    }
}
