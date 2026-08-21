package com.yapcore.plugincompat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

/**
 * Light ASM rewrite: 1.20–1.21 field names + versioned CraftBukkit packages → Paper 26.2.
 */
public final class PluginCompatRewriter {

    private static final Logger LOG = Logger.getLogger("YaPcore.PluginCompat");

    private final Map<String, Map<String, String>> fieldRemaps;
    private final boolean backup;

    public PluginCompatRewriter(boolean backup) {
        this.fieldRemaps = FieldRemaps.catalog();
        this.backup = backup;
    }

    public record Result(boolean rewritten, int classHits, int fieldHits, int packageHits, Path jar) {
    }

    public Result rewriteJar(Path jar, Path backupDir) throws IOException {
        if (!Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
            return new Result(false, 0, 0, 0, jar);
        }
        String name = jar.getFileName().toString();
        if (shouldSkip(name)) {
            return new Result(false, 0, 0, 0, jar);
        }

        AtomicInteger fieldHits = new AtomicInteger();
        AtomicInteger packageHits = new AtomicInteger();
        AtomicInteger classHits = new AtomicInteger();

        Path tmp = Files.createTempFile("yap-compat-", ".jar");
        boolean any = false;
        try (JarFile in = new JarFile(jar.toFile());
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] raw;
                try (InputStream is = in.getInputStream(entry)) {
                    raw = is.readAllBytes();
                }
                String entryName = entry.getName();
                byte[] written = raw;
                if (entryName.endsWith(".class") && !entryName.contains("module-info")) {
                    Transformed t = transformClass(raw, fieldHits, packageHits);
                    if (t.changed()) {
                        written = t.bytes();
                        classHits.incrementAndGet();
                        any = true;
                    }
                }
                JarEntry outEntry = new JarEntry(entryName);
                outEntry.setTime(entry.getTime());
                out.putNextEntry(outEntry);
                out.write(written);
                out.closeEntry();
            }
        }

        if (!any) {
            Files.deleteIfExists(tmp);
            return new Result(false, 0, 0, 0, jar);
        }

        if (backup && backupDir != null) {
            Files.createDirectories(backupDir);
            Path bak = backupDir.resolve(name + "." + shortHash(jar) + ".bak");
            if (!Files.exists(bak)) {
                Files.copy(jar, bak, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        try {
            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        LOG.info("Plugin compat rewrite: " + name
                + " classes=" + classHits.get()
                + " fields=" + fieldHits.get()
                + " packages=" + packageHits.get());
        return new Result(true, classHits.get(), fieldHits.get(), packageHits.get(), jar);
    }

    public static boolean shouldSkip(String fileName) {
        String n = fileName.toLowerCase();
        return n.startsWith("yap-")
                || n.equals("placeholderapi.jar")
                || n.contains("yap-spatial-tick")
                || n.contains("yap-compat-smoke")
                || n.contains("yap-plugin-compat");
    }

    private Transformed transformClass(byte[] input, AtomicInteger fieldHits, AtomicInteger packageHits) {
        try {
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            Remapper pkgRemapper = new Remapper() {
                @Override
                public String map(String internalName) {
                    String n = PackageRemaps.rewriteInternalName(internalName);
                    if (n != null && !n.equals(internalName)) {
                        packageHits.incrementAndGet();
                    }
                    return n;
                }

                @Override
                public String mapDesc(String descriptor) {
                    String d = PackageRemaps.rewriteDescriptor(descriptor);
                    if (d != null && !d.equals(descriptor)) {
                        packageHits.incrementAndGet();
                    }
                    return d;
                }
            };
            ClassVisitor remapped = new ClassRemapper(writer, pkgRemapper);
            ClassVisitor fields = new ClassVisitor(Opcodes.ASM9, remapped) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.GETSTATIC) {
                                Map<String, String> map = fieldRemaps.get(owner);
                                if (map != null && map.containsKey(name)) {
                                    String neu = map.get(name);
                                    fieldHits.incrementAndGet();
                                    super.visitFieldInsn(opcode, owner, neu, descriptor);
                                    return;
                                }
                            }
                            super.visitFieldInsn(opcode, owner, name, descriptor);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String s) {
                                String n = rewriteStringLiteral(s);
                                if (!n.equals(s)) {
                                    packageHits.incrementAndGet();
                                    super.visitLdcInsn(n);
                                    return;
                                }
                            } else if (value instanceof Type t && t.getSort() == Type.OBJECT) {
                                String n = PackageRemaps.rewriteInternalName(t.getInternalName());
                                if (!n.equals(t.getInternalName())) {
                                    packageHits.incrementAndGet();
                                    super.visitLdcInsn(Type.getObjectType(n));
                                    return;
                                }
                            }
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor,
                                                           Handle bootstrapMethodHandle,
                                                           Object... bootstrapMethodArguments) {
                            Object[] args = bootstrapMethodArguments.clone();
                            for (int i = 0; i < args.length; i++) {
                                if (args[i] instanceof Handle h) {
                                    String owner = PackageRemaps.rewriteInternalName(h.getOwner());
                                    String desc = PackageRemaps.rewriteDescriptor(h.getDesc());
                                    if (!owner.equals(h.getOwner()) || !desc.equals(h.getDesc())) {
                                        packageHits.incrementAndGet();
                                        args[i] = new Handle(h.getTag(), owner, h.getName(), desc, h.isInterface());
                                    }
                                } else if (args[i] instanceof Type t && t.getSort() == Type.OBJECT) {
                                    String n = PackageRemaps.rewriteInternalName(t.getInternalName());
                                    if (!n.equals(t.getInternalName())) {
                                        packageHits.incrementAndGet();
                                        args[i] = Type.getObjectType(n);
                                    }
                                }
                            }
                            super.visitInvokeDynamicInsn(name,
                                    PackageRemaps.rewriteDescriptor(descriptor),
                                    bootstrapMethodHandle, args);
                        }
                    };
                }
            };
            reader.accept(fields, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            byte[] out = writer.toByteArray();
            boolean changed = out.length != input.length;
            if (!changed) {
                for (int i = 0; i < input.length; i++) {
                    if (out[i] != input[i]) {
                        changed = true;
                        break;
                    }
                }
            }
            return new Transformed(out, changed);
        } catch (Exception e) {
            LOG.fine("skip class transform: " + e.getMessage());
            return new Transformed(input, false);
        }
    }

    private static String rewriteStringLiteral(String s) {
        if (s.contains("org.bukkit.craftbukkit.v1_")) {
            return s.replaceAll("org\\.bukkit\\.craftbukkit\\.v1_2[01]_R[0-9]+",
                    "org.bukkit.craftbukkit");
        }
        if (s.contains("org/bukkit/craftbukkit/v1_")) {
            return PackageRemaps.rewriteInternalName(s);
        }
        return s;
    }

    private static String shortHash(Path jar) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            return "bak";
        }
        md.update(Files.readAllBytes(jar));
        return HexFormat.of().formatHex(md.digest()).substring(0, 10);
    }

    private record Transformed(byte[] bytes, boolean changed) {
    }

    /** Rewrite all eligible jars under a plugins directory. */
    public int rewritePluginsDir(Path pluginsDir) throws IOException {
        if (!Files.isDirectory(pluginsDir)) {
            return 0;
        }
        Path backupDir = pluginsDir.resolve(".yap-plugin-compat-backup");
        int n = 0;
        try (var stream = Files.list(pluginsDir)) {
            for (Path p : stream.filter(x -> x.getFileName().toString().endsWith(".jar")).toList()) {
                Result r = rewriteJar(p, backupDir);
                if (r.rewritten()) {
                    n++;
                }
            }
        }
        return n;
    }
}
