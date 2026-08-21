package com.yapcore.plugincompat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginCompatRewriterTest {

    @Test
    void remapsLegacyEnchantmentFieldAndCraftPackage() throws Exception {
        Path dir = Files.createTempDirectory("yap-compat-test");
        Path jar = dir.resolve("legacy-demo.jar");
        byte[] cls = synthLegacyClass();
        assertTrue(containsUtf8(cls, "DAMAGE_ALL"));
        assertTrue(containsUtf8(cls, "org/bukkit/craftbukkit/v1_20_R3/entity/CraftPlayer"));
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry e = new JarEntry("demo/LegacyRefs.class");
            e.setMethod(JarEntry.STORED);
            e.setSize(cls.length);
            e.setCompressedSize(cls.length);
            var crc = new java.util.zip.CRC32();
            crc.update(cls);
            e.setCrc(crc.getValue());
            jos.putNextEntry(e);
            jos.write(cls);
            jos.closeEntry();
        }

        PluginCompatRewriter.Result r =
                new PluginCompatRewriter(false).rewriteJar(jar, dir.resolve("bak"));
        assertTrue(r.rewritten());
        assertTrue(r.fieldHits() >= 1);
        assertTrue(r.packageHits() >= 1);

        byte[] rawAfter;
        try (var jf = new java.util.jar.JarFile(jar.toFile())) {
            try (var in = jf.getInputStream(jf.getJarEntry("demo/LegacyRefs.class"))) {
                rawAfter = in.readAllBytes();
            }
        }
        assertFalse(containsUtf8(rawAfter, "DAMAGE_ALL"));
        assertTrue(containsUtf8(rawAfter, "SHARPNESS"));
        assertFalse(containsUtf8(rawAfter, "v1_20_R3"));
        assertTrue(containsUtf8(rawAfter, "org/bukkit/craftbukkit/entity/CraftPlayer"));
    }

    @Test
    void packageRemapHelper() {
        assertTrue(PackageRemaps.rewriteInternalName(
                "org/bukkit/craftbukkit/v1_21_R1/CraftServer")
                .equals("org/bukkit/craftbukkit/CraftServer"));
    }

    private static boolean containsUtf8(byte[] data, String s) {
        byte[] needle = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** Class that references Enchantment.DAMAGE_ALL and CraftPlayer v1_20_R3. */
    private static byte[] synthLegacyClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/LegacyRefs", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "touch",
                "()Lorg/bukkit/enchantments/Enchantment;", null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC,
                "org/bukkit/enchantments/Enchantment",
                "DAMAGE_ALL",
                "Lorg/bukkit/enchantments/Enchantment;");
        mv.visitLdcInsn("org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer");
        mv.visitInsn(Opcodes.POP);
        mv.visitTypeInsn(Opcodes.NEW, "org/bukkit/craftbukkit/v1_20_R3/entity/CraftPlayer");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
