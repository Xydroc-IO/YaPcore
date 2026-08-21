package com.yapcore.protocol;

import com.yapcore.client.ClientEdition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Backwards-compatible protocol version registry for Java and Bedrock clients.
 * Unknown newer versions are accepted in lenient mode and mapped to the nearest
 * supported codec when possible.
 */
public final class ProtocolVersionRegistry {

    public record ProtocolVersion(
            ClientEdition edition,
            int protocolId,
            String minecraftVersion,
            String label,
            boolean recommended
    ) {
    }

    private final List<ProtocolVersion> versions;
    private final boolean lenient;

    public ProtocolVersionRegistry(boolean lenient) {
        this.lenient = lenient;
        List<ProtocolVersion> list = new ArrayList<>();

        // Java Edition (selected major releases — oldest → newest)
        list.add(new ProtocolVersion(ClientEdition.JAVA, 47, "1.8.9", "Java 1.8", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 107, "1.9", "Java 1.9", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 110, "1.9.4", "Java 1.9.4", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 210, "1.10.2", "Java 1.10", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 316, "1.11.2", "Java 1.11", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 340, "1.12.2", "Java 1.12", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 404, "1.13.2", "Java 1.13", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 498, "1.14.4", "Java 1.14", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 578, "1.15.2", "Java 1.15", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 754, "1.16.5", "Java 1.16", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 755, "1.17", "Java 1.17", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 756, "1.17.1", "Java 1.17.1", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 757, "1.18", "Java 1.18", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 758, "1.18.2", "Java 1.18.2", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 759, "1.19", "Java 1.19", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 760, "1.19.2", "Java 1.19.1-2", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 761, "1.19.3", "Java 1.19.3", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 762, "1.19.4", "Java 1.19.4", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 763, "1.20.1", "Java 1.20-1.20.1", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 764, "1.20.2", "Java 1.20.2", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 765, "1.20.4", "Java 1.20.3-1.20.4", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 766, "1.20.6", "Java 1.20.5-1.20.6", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 767, "1.21.1", "Java 1.21-1.21.1", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 768, "1.21.3", "Java 1.21.2-1.21.3", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 769, "1.21.4", "Java 1.21.4", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 770, "1.21.5", "Java 1.21.5", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 771, "1.21.6", "Java 1.21.6", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 772, "1.21.8", "Java 1.21.7-1.21.8", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 773, "1.21.10", "Java 1.21.9-1.21.10", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 774, "1.21.11", "Java 1.21.11", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 775, "26.1", "Java 26.1-26.1.2", true));
        list.add(new ProtocolVersion(ClientEdition.JAVA, 776, "26.2", "Java 26.2", true));

        // Bedrock Edition (protocol numbers used by Bedrock networking)
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 486, "1.18.30", "Bedrock 1.18", false));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 527, "1.19.50", "Bedrock 1.19", false));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 582, "1.19.80", "Bedrock 1.19.80", false));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 618, "1.20.40", "Bedrock 1.20", false));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 649, "1.20.80", "Bedrock 1.20.80", true));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 671, "1.21.0", "Bedrock 1.21", true));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 686, "1.21.20", "Bedrock 1.21.20", true));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 712, "1.21.50", "Bedrock 1.21.50", true));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 766, "1.21.50", "Bedrock 1.21.50 (proto 766)", true));
        list.add(new ProtocolVersion(ClientEdition.BEDROCK, 776, "1.21.60", "Bedrock 1.21.60+", true));

        list.sort(Comparator.comparingInt(ProtocolVersion::protocolId));
        this.versions = Collections.unmodifiableList(list);
    }

    public List<ProtocolVersion> all() {
        return versions;
    }

    public List<ProtocolVersion> forEdition(ClientEdition edition) {
        return versions.stream().filter(v -> v.edition() == edition).toList();
    }

    public Optional<ProtocolVersion> exact(ClientEdition edition, int protocolId) {
        return versions.stream()
                .filter(v -> v.edition() == edition && v.protocolId() == protocolId)
                .findFirst();
    }

    /**
     * Resolve a client protocol. Exact match preferred; in lenient mode, nearest
     * lower supported version is used so older and slightly-newer clients connect.
     */
    public Optional<ProtocolVersion> resolve(ClientEdition edition, int protocolId) {
        Optional<ProtocolVersion> exact = exact(edition, protocolId);
        if (exact.isPresent()) {
            return exact;
        }
        if (!lenient) {
            return Optional.empty();
        }
        return forEdition(edition).stream()
                .filter(v -> v.protocolId() <= protocolId)
                .max(Comparator.comparingInt(ProtocolVersion::protocolId))
                .or(() -> forEdition(edition).stream().findFirst());
    }

    public boolean isSupported(ClientEdition edition, int protocolId) {
        return resolve(edition, protocolId).isPresent();
    }

    public ProtocolVersion recommended(ClientEdition edition) {
        return forEdition(edition).stream()
                .filter(ProtocolVersion::recommended)
                .reduce((a, b) -> b)
                .orElseGet(() -> forEdition(edition).get(forEdition(edition).size() - 1));
    }

    public boolean isLenient() {
        return lenient;
    }
}
