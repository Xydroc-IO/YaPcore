package com.yapcore.sched.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
 * Wraps {@code RegisteredListener.callEvent} so entity/block/chunk events set
 * {@link SchedCompatContext} for the duration of the listener.
 */
final class RegisteredListenerTransformer implements ClassFileTransformer {

    private static final Logger LOG = Logger.getLogger("YaP.SchedCompat");
    private static final String TARGET = "org/bukkit/plugin/RegisteredListener";
    private static final String CTX = "com/yapcore/sched/agent/SchedCompatContext";
    private static final String ORIG = "yap$origCallEvent";
    private static final String CALL_DESC = "(Lorg/bukkit/event/Event;)V";
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
                    if ("callEvent".equals(name) && CALL_DESC.equals(descriptor)) {
                        hit.set(true);
                        return super.visitMethod(access, ORIG, descriptor, signature, exceptions);
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                @Override
                public void visitEnd() {
                    if (hit.get()) {
                        emitWrapper();
                    }
                    super.visitEnd();
                }

                private void emitWrapper() {
                    MethodVisitor raw = super.visitMethod(
                            Opcodes.ACC_PUBLIC,
                            "callEvent",
                            CALL_DESC,
                            null,
                            new String[]{"java/lang/Exception"});
                    GeneratorAdapter gen = new GeneratorAdapter(raw, Opcodes.ACC_PUBLIC, "callEvent", CALL_DESC);
                    gen.visitCode();
                    gen.loadArg(0);
                    gen.invokeStatic(
                            Type.getObjectType(CTX),
                            Method.getMethod("java.lang.AutoCloseable scopedFromEvent(java.lang.Object)"));
                    int slot = gen.newLocal(Type.getType(AutoCloseable.class));
                    gen.storeLocal(slot);

                    Label start = gen.newLabel();
                    Label end = gen.newLabel();
                    Label handler = gen.newLabel();
                    gen.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");

                    gen.mark(start);
                    gen.loadThis();
                    gen.loadArg(0);
                    gen.invokeVirtual(Type.getObjectType(TARGET), new Method(ORIG, CALL_DESC));
                    gen.mark(end);
                    closeLocal(gen, slot);
                    gen.returnValue();

                    gen.mark(handler);
                    int thrown = gen.newLocal(Type.getType(Throwable.class));
                    gen.storeLocal(thrown);
                    closeLocal(gen, slot);
                    gen.loadLocal(thrown);
                    gen.throwException();
                    gen.endMethod();
                }

                private void closeLocal(GeneratorAdapter gen, int slot) {
                    Label skip = gen.newLabel();
                    gen.loadLocal(slot);
                    gen.ifNull(skip);
                    gen.loadLocal(slot);
                    gen.invokeInterface(
                            Type.getType(AutoCloseable.class),
                            Method.getMethod("void close()"));
                    gen.mark(skip);
                }
            }, ClassReader.EXPAND_FRAMES);
            if (!hit.get()) {
                LOG.warning("yap-sched-agent: RegisteredListener.callEvent not found");
                return null;
            }
            if (transformed.compareAndSet(false, true)) {
                LOG.info("yap-sched-agent: wrapped RegisteredListener.callEvent");
            }
            return writer.toByteArray();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "yap-sched-agent: failed to transform RegisteredListener", t);
            return null;
        }
    }
}
