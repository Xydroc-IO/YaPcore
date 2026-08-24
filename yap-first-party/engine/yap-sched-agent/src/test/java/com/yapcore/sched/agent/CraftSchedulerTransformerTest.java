package com.yapcore.sched.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassVisitor;

import java.lang.instrument.IllegalClassFormatException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftSchedulerTransformerTest {

    @BeforeEach
    void reset() {
        SchedCompatMetrics.reset();
    }

    @Test
    void rewritesHandleMethod() throws IllegalClassFormatException {
        byte[] original = synthesizeCraftScheduler();
        CraftSchedulerTransformer tx = new CraftSchedulerTransformer(SchedCompatOptions.defaults());
        byte[] out = tx.transform(null, "org/bukkit/craftbukkit/scheduler/CraftScheduler", null, null, original);
        assertNotNull(out);
        assertTrue(out.length > 0);

        ClassReader reader = new ClassReader(out);
        StringBuilder body = new StringBuilder();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!"handle".equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        body.append(owner).append('.').append(name).append(';');
                    }
                };
            }
        }, 0);
        assertTrue(body.toString().contains("com/yapcore/sched/agent/SchedCompatRouter.handle"),
                "expected SchedCompatRouter.handle call, got: " + body);
    }

    /** Minimal CraftScheduler-shaped class with throwing handle(). */
    private static byte[] synthesizeCraftScheduler() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "org/bukkit/craftbukkit/scheduler/CraftScheduler",
                null, "java/lang/Object", null);
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor handle = cw.visitMethod(
                Opcodes.ACC_PROTECTED,
                "handle",
                "(Lorg/bukkit/craftbukkit/scheduler/CraftTask;J)Lorg/bukkit/craftbukkit/scheduler/CraftTask;",
                null,
                null);
        handle.visitCode();
        handle.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
        handle.visitInsn(Opcodes.DUP);
        handle.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException",
                "<init>", "()V", false);
        handle.visitInsn(Opcodes.ATHROW);
        handle.visitMaxs(2, 4);
        handle.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
