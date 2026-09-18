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

class RegisteredListenerTransformerTest {

    @Test
    void wrapsCallEventWithSchedCompatContext() throws IllegalClassFormatException {
        byte[] original = synthesizeRegisteredListener();
        byte[] out = new RegisteredListenerTransformer().transform(
                null, "org/bukkit/plugin/RegisteredListener", null, null, original);
        assertNotNull(out);

        ClassReader reader = new ClassReader(out);
        boolean[] orig = {false};
        StringBuilder body = new StringBuilder();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                            String signature, String[] exceptions) {
                if ("yap$origCallEvent".equals(name)) {
                    orig[0] = true;
                }
                if (!"callEvent".equals(name)) {
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
        assertTrue(orig[0], "expected renamed original callEvent");
        assertTrue(body.toString().contains("com/yapcore/sched/agent/SchedCompatContext.scopedFromEvent"),
                "expected scopedFromEvent, got: " + body);
        assertTrue(body.toString().contains("org/bukkit/plugin/RegisteredListener.yap$origCallEvent"),
                "expected orig call, got: " + body);
    }

    private static byte[] synthesizeRegisteredListener() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "org/bukkit/plugin/RegisteredListener",
                null, "java/lang/Object", null);
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor call = cw.visitMethod(
                Opcodes.ACC_PUBLIC, "callEvent", "(Lorg/bukkit/event/Event;)V", null, null);
        call.visitCode();
        call.visitInsn(Opcodes.RETURN);
        call.visitMaxs(0, 2);
        call.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
