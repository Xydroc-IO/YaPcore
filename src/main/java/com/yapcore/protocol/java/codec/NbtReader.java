package com.yapcore.protocol.java.codec;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal big-endian NBT reader (named root compound). Supports gzip wrappers.
 */
public final class NbtReader {

    private NbtReader() {
    }

    public static Map<String, Object> readGzipResource(String classpath) throws IOException {
        try (InputStream in = NbtReader.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IOException("Missing resource " + classpath);
            }
            return readGzip(in.readAllBytes());
        }
    }

    public static Map<String, Object> readGzip(byte[] gzipBytes) throws IOException {
        try (GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(gzipBytes));
             DataInputStream data = new DataInputStream(gin)) {
            return readNamedRoot(data);
        }
    }

    public static Map<String, Object> readNamedRoot(DataInputStream in) throws IOException {
        byte type = in.readByte();
        if (type != 10) {
            throw new IOException("Expected TAG_Compound root, got " + type);
        }
        in.readUTF(); // root name (often empty)
        return readCompound(in);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, int[]> asIntArrayMap(Object value) {
        Map<String, int[]> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof int[] arr) {
                out.put(String.valueOf(e.getKey()), arr);
            } else if (v instanceof List<?> list) {
                int[] arr = new int[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    arr[i] = ((Number) list.get(i)).intValue();
                }
                out.put(String.valueOf(e.getKey()), arr);
            }
        }
        return out;
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) {
                return map;
            }
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, byte type) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                int n = in.readInt();
                byte[] arr = in.readNBytes(n);
                yield arr;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                byte listType = in.readByte();
                int n = in.readInt();
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(readPayload(in, listType));
                }
                yield list;
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int n = in.readInt();
                int[] arr = new int[n];
                for (int i = 0; i < n; i++) {
                    arr[i] = in.readInt();
                }
                yield arr;
            }
            case 12 -> {
                int n = in.readInt();
                long[] arr = new long[n];
                for (int i = 0; i < n; i++) {
                    arr[i] = in.readLong();
                }
                yield arr;
            }
            default -> throw new IOException("Unsupported NBT type " + type);
        };
    }
}
