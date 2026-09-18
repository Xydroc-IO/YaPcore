package com.yapcore.sched.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prefixes {@code CraftTask.cancel()} so the Folia {@code ScheduledTask} is cancelled too.
 */
final class CraftTaskTransformer implements ClassFileTransformer {

    private static final Logger LOG = Logger.getLogger("YaP.SchedCompat");
    private static final String TARGET = "org/bukkit/craftbukkit/scheduler/CraftTask";
    private static final String ROUTER = "com/yapcore/sched/agent/SchedCompatRouter";
    private final AtomicBoolean transformed = new AtomicBoolean();

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (className == null || !TARGET.equals(className)) {
            return null;
        }
        try {
            SchedCompatAgent.injectInto(loader);
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            AtomicBoolean hit = new AtomicBoolean();
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(
                        int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!"cancel".equals(name) || !"()V".equals(descriptor)) {
                        return mv;
                    }
                    hit.set(true);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            mv.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TARGET, "getTaskId", "()I", false);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ROUTER, "cancelFolia", "(I)V", false);
                        }
                    };
                }
            }, ClassReader.EXPAND_FRAMES);
            if (!hit.get()) {
                LOG.warning("yap-sched-agent: CraftTask.cancel not found — cancel shim inactive");
                return null;
            }
            if (transformed.compareAndSet(false, true)) {
                LOG.info("yap-sched-agent: rewritten CraftTask.cancel");
            }
            return writer.toByteArray();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "yap-sched-agent: failed to transform CraftTask", t);
            return null;
        }
    }
}
