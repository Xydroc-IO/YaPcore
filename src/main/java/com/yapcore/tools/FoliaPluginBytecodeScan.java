package com.yapcore.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Static Folia-compat scan for first-party plugin jars.
 * Fails when plugin bytecode calls legacy BukkitScheduler sync APIs
 * (except allowlisted {@code com.yapcore.sched} fallbacks) or when
 * {@code plugin.yml} lacks {@code folia-supported: true}.
 */
public final class FoliaPluginBytecodeScan {

    private static final Set<String> FORBIDDEN_SCHED_METHODS = Set.of(
            "runTask",
            "runTaskLater",
            "runTaskTimer",
            "runTaskAsynchronously",
            "runTaskLaterAsynchronously",
            "runTaskTimerAsynchronously",
            "scheduleSyncDelayedTask",
            "scheduleSyncRepeatingTask",
            "scheduleAsyncDelayedTask",
            "scheduleAsyncRepeatingTask"
    );

    private FoliaPluginBytecodeScan() {
    }

    public record Finding(String jar, String severity, String detail) {
    }

    public record Result(List<Finding> findings) {
        public boolean failed() {
            return findings.stream().anyMatch(f -> "FAIL".equals(f.severity()));
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: FoliaPluginBytecodeScan <pluginsDir|jar>...");
            System.exit(2);
        }
        List<Path> inputs = new ArrayList<>();
        for (String a : args) {
            inputs.add(Path.of(a));
        }
        Result r = scan(inputs);
        for (Finding f : r.findings()) {
            System.out.println("[" + f.severity() + "] " + f.jar() + " — " + f.detail());
        }
        if (r.findings().isEmpty()) {
            System.out.println("PASS: no Folia-compat findings");
        }
        System.exit(r.failed() ? 1 : 0);
    }

    public static Result scan(List<Path> inputs) throws IOException {
        List<Finding> out = new ArrayList<>();
        Set<Path> jars = new LinkedHashSet<>();
        for (Path p : inputs) {
            if (Files.isDirectory(p)) {
                try (var stream = Files.list(p)) {
                    stream.filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                            .forEach(jars::add);
                }
            } else if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                jars.add(p);
            }
        }
        for (Path jar : jars) {
            scanJar(jar, out);
        }
        return new Result(List.copyOf(out));
    }

    static void scanJar(Path jar, List<Finding> out) throws IOException {
        String name = jar.getFileName().toString();
        if (shouldSkipJar(name)) {
            return;
        }
        boolean hasPluginYml = false;
        boolean foliaSupported = false;
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry yml = jf.getEntry("plugin.yml");
            if (yml == null) {
                yml = jf.getEntry("paper-plugin.yml");
            }
            if (yml != null) {
                hasPluginYml = true;
                String text;
                try (InputStream in = jf.getInputStream(yml)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                foliaSupported = text.lines()
                        .map(String::trim)
                        .anyMatch(l -> l.equals("folia-supported: true")
                                || l.equals("folia-supported:true"));
            }
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (!e.getName().endsWith(".class") || e.isDirectory()) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = jf.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
                try {
                    scanClass(name, e.getName(), bytes, out);
                } catch (IllegalArgumentException ex) {
                    out.add(new Finding(name, "WARN",
                            e.getName() + " unreadable by ASM (" + ex.getMessage() + ")"));
                }
            }
        }
        if (hasPluginYml && !foliaSupported) {
            out.add(new Finding(name, "FAIL", "plugin.yml missing folia-supported: true"));
        }
        if (!hasPluginYml && name.toLowerCase(Locale.ROOT).startsWith("yap-")) {
            out.add(new Finding(name, "WARN", "no plugin.yml / paper-plugin.yml (skipped folia-supported check)"));
        }
    }

    static boolean shouldSkipJar(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.contains("yap-sched-agent")
                || n.contains("yap-mspt-bench")
                || n.contains("yap-pop-sim")
                || n.contains("yap-compat-smoke")
                || n.contains("legacy-sched-smoke")
                || n.contains("scoreboard-smoke")
                || n.equals("placeholderapi.jar")
                || n.equals("grim.jar")
                || n.equals("tebex.jar");
    }

    static void scanClass(String jar, String classPath, byte[] bytes, List<Finding> out) {
        String internal = classPath.replace(".class", "");
        if (internal.startsWith("com/yapcore/sched/")
                || internal.startsWith("com/yapcore/tools/")) {
            return;
        }
        // Only enforce first-party packages — shaded third-party (bstats, adventure, etc.) is noise.
        boolean firstParty = internal.startsWith("com/yapcore/")
                || internal.startsWith("com/yaplabs/");
        if (!firstParty) {
            return;
        }
        ClassReader cr = new ClassReader(bytes);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean sawGetScheduler;

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String desc, boolean isInterface) {
                        if (("org/bukkit/Bukkit".equals(owner) || "org/bukkit/Server".equals(owner))
                                && "getScheduler".equals(method)) {
                            sawGetScheduler = true;
                        }
                        if ("org/bukkit/scheduler/BukkitScheduler".equals(owner)
                                && FORBIDDEN_SCHED_METHODS.contains(method)) {
                            out.add(new Finding(jar, "FAIL",
                                    classPath + " calls BukkitScheduler." + method));
                        }
                        if (sawGetScheduler && FORBIDDEN_SCHED_METHODS.contains(method)
                                && (opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL)) {
                            out.add(new Finding(jar, "FAIL",
                                    classPath + "#" + name + " uses Bukkit.getScheduler()." + method));
                            sawGetScheduler = false;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
