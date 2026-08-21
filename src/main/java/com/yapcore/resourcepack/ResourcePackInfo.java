package com.yapcore.resourcepack;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * A resource / texture pack available for seamless client download.
 */
public final class ResourcePackInfo {

    private final String id;
    private final String fileName;
    private final Path path;
    private final String sha1Hex;
    private final long sizeBytes;
    private final String prompt;
    private final boolean forced;

    public ResourcePackInfo(String id, String fileName, Path path, String sha1Hex,
                            long sizeBytes, String prompt, boolean forced) {
        this.id = Objects.requireNonNull(id);
        this.fileName = Objects.requireNonNull(fileName);
        this.path = Objects.requireNonNull(path);
        this.sha1Hex = Objects.requireNonNull(sha1Hex).toLowerCase(Locale.ROOT);
        this.sizeBytes = sizeBytes;
        this.prompt = prompt != null ? prompt : "This server uses a resource pack for the best experience.";
        this.forced = forced;
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public Path getPath() { return path; }
    public String getSha1Hex() { return sha1Hex; }
    public long getSizeBytes() { return sizeBytes; }
    public String getPrompt() { return prompt; }
    public boolean isForced() { return forced; }

    public String sizeLabel() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", sizeBytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
