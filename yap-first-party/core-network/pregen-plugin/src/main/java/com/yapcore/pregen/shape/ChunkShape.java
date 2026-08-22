package com.yapcore.pregen.shape;

import java.util.List;

/** Produces chunk coordinates for a pregen job. */
public interface ChunkShape extends Iterable<ChunkPos> {
    long size();

    String description();

    static List<ChunkPos> materialize(ChunkShape shape) {
        return java.util.stream.StreamSupport.stream(shape.spliterator(), false).toList();
    }
}
