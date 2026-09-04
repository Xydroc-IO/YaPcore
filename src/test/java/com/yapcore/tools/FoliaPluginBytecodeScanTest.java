package com.yapcore.tools;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaPluginBytecodeScanTest {

    @Test
    void failsMissingFoliaSupportedAndLegacyScheduler() throws Exception {
        Path dir = Files.createTempDirectory("yap-folia-scan");
        Path bad = dir.resolve("yap-bad.jar");
        writeJar(bad, """
                name: YaPBad
                version: '1.0'
                main: com.yapcore.demo.Bad
                api-version: '1.21'
                """, "com/yapcore/demo/Bad.class", synthLegacySchedClass("com/yapcore/demo/Bad"));

        FoliaPluginBytecodeScan.Result r = FoliaPluginBytecodeScan.scan(List.of(bad));
        assertTrue(r.failed());
        assertTrue(r.findings().stream().anyMatch(f -> f.detail().contains("folia-supported")));
        assertTrue(r.findings().stream().anyMatch(f ->
                f.detail().contains("BukkitScheduler") || f.detail().contains("getScheduler")));
    }

    @Test
    void passesCleanFoliaPlugin() throws Exception {
        Path dir = Files.createTempDirectory("yap-folia-scan-ok");
        Path ok = dir.resolve("yap-ok.jar");
        writeJar(ok, """
                name: YaPOk
                version: '1.0'
                main: com.yapcore.demo.Ok
                api-version: '1.21'
                folia-supported: true
                """, "com/yapcore/demo/Ok.class", synthEmptyClass("com/yapcore/demo/Ok"));

        FoliaPluginBytecodeScan.Result r = FoliaPluginBytecodeScan.scan(List.of(ok));
        assertFalse(r.failed());
    }

    private static void writeJar(Path jar, String pluginYml, String classPath, byte[] cls) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            putStored(jos, "plugin.yml", pluginYml.getBytes(StandardCharsets.UTF_8));
            putStored(jos, classPath, cls);
        }
    }

    private static void putStored(JarOutputStream jos, String name, byte[] data) throws Exception {
        JarEntry e = new JarEntry(name);
        e.setMethod(JarEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        jos.putNextEntry(e);
        jos.write(data);
        jos.closeEntry();
    }

    private static byte[] synthEmptyClass(String internal) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] synthLegacySchedClass(String internal) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/bukkit/Bukkit", "getScheduler",
                "()Lorg/bukkit/scheduler/BukkitScheduler;", false);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/bukkit/scheduler/BukkitScheduler",
                "runTask", "(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
