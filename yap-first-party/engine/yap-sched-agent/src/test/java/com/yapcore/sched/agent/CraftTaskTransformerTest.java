package com.yapcore.sched.agent;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.IllegalClassFormatException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTaskTransformerTest {

    @Test
    void prefixesCancelWithFoliaCancel() throws IllegalClassFormatException {
        byte[] original = synthesizeCraftTask();
        byte[] out = new CraftTaskTransformer().transform(
                null, "org/bukkit/craftbukkit/scheduler/CraftTask", null, null, original);
        assertNotNull(out);

        ClassReader reader = new ClassReader(out);
        StringBuilder body = new StringBuilder();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                            String signature, String[] exceptions) {
                if (!"cancel".equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                boolean isInterface) {
                        body.append(owner).append('.').append(name).append(';');
                    }
                };
            }
        }, 0);
        assertTrue(body.toString().contains("com/yapcore/sched/agent/SchedCompatRouter.cancelFolia"),
                "expected cancelFolia, got: " + body);
        assertTrue(body.toString().contains("org/bukkit/craftbukkit/scheduler/CraftTask.getTaskId"),
                "expected getTaskId, got: " + body);
    }

    private static byte[] synthesizeCraftTask() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "org/bukkit/craftbukkit/scheduler/CraftTask",
                null, "java/lang/Object", null);
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor id = cw.visitMethod(Opcodes.ACC_PUBLIC, "getTaskId", "()I", null, null);
        id.visitCode();
        id.visitInsn(Opcodes.ICONST_1);
        id.visitInsn(Opcodes.IRETURN);
        id.visitMaxs(1, 1);
        id.visitEnd();

        MethodVisitor cancel = cw.visitMethod(Opcodes.ACC_PUBLIC, "cancel", "()V", null, null);
        cancel.visitCode();
        cancel.visitInsn(Opcodes.RETURN);
        cancel.visitMaxs(0, 1);
        cancel.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
