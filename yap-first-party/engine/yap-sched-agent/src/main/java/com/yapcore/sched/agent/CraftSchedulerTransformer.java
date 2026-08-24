package com.yapcore.sched.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rewrites {@code CraftScheduler.handle(CraftTask, long)} to call
 * {@link SchedCompatRouter#handle(Object, Object, long)}.
 */
final class CraftSchedulerTransformer implements ClassFileTransformer {

    private static final Logger LOG = Logger.getLogger("YaP.SchedCompat");
    private static final String TARGET = "org/bukkit/craftbukkit/scheduler/CraftScheduler";
    private static final String HANDLE_DESC =
            "(Lorg/bukkit/craftbukkit/scheduler/CraftTask;J)Lorg/bukkit/craftbukkit/scheduler/CraftTask;";
    private static final String ROUTER = "com/yapcore/sched/agent/SchedCompatRouter";

    private final SchedCompatOptions options;
    private final AtomicBoolean transformed = new AtomicBoolean();

    CraftSchedulerTransformer(SchedCompatOptions options) {
        this.options = options;
    }

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
                    if (!"handle".equals(name) || !HANDLE_DESC.equals(descriptor)) {
                        return mv;
                    }
                    hit.set(true);
                    GeneratorAdapter gen = new GeneratorAdapter(mv, access, name, descriptor);
                    return new MethodVisitor(Opcodes.ASM9, gen) {
                        @Override
                        public void visitCode() {
                            gen.visitCode();
                            // return (CraftTask) SchedCompatRouter.handle(this, task, delay);
                            gen.loadThis();
                            gen.loadArg(0);
                            gen.loadArg(1);
                            gen.invokeStatic(
                                    Type.getObjectType(ROUTER),
                                    Method.getMethod(
                                            "java.lang.Object handle(java.lang.Object, java.lang.Object, long)"));
                            gen.checkCast(Type.getObjectType("org/bukkit/craftbukkit/scheduler/CraftTask"));
                            gen.returnValue();
                            gen.endMethod();
                        }
                    };
                }
            }, ClassReader.EXPAND_FRAMES);
            if (!hit.get()) {
                LOG.warning("yap-sched-agent: CraftScheduler.handle signature not found — shim inactive");
                return null;
            }
            if (transformed.compareAndSet(false, true)) {
                LOG.info("yap-sched-agent: rewritten CraftScheduler.handle"
                        + (options.warnGlobal() ? " (warn-global=on)" : ""));
            }
            return writer.toByteArray();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "yap-sched-agent: failed to transform CraftScheduler", t);
            return null;
        }
    }
}
