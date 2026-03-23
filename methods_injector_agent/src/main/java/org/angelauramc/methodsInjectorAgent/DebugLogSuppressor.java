package org.angelauramc.methodsInjectorAgent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class DebugLogSuppressor implements ClassFileTransformer {

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new DebugLogSuppressor());
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if ("org/lwjgl/input/Cursor".equals(className) || "org/lwjgl/input/GLFWInputImplementation".equals(className)) {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL && "java/io/PrintStream".equals(owner) && "println".equals(name) && "(Ljava/lang/String;)V".equals(descriptor)) {
                                // Pop the String argument and the PrintStream instance off the stack
                                super.visitInsn(Opcodes.POP);
                                super.visitInsn(Opcodes.POP);
                            } else if (opcode == Opcodes.INVOKEVIRTUAL && "java/io/PrintStream".equals(owner) && "println".equals(name) && "(Z)V".equals(descriptor)) {
                                // For boolean arguments (print "Grab: true/false")
                                super.visitInsn(Opcodes.POP);
                                super.visitInsn(Opcodes.POP);
                            } else if (opcode == Opcodes.INVOKEVIRTUAL && "java/io/PrintStream".equals(owner) && "println".equals(name) && "(I)V".equals(descriptor)) {
                                // For integer arguments
                                super.visitInsn(Opcodes.POP);
                                super.visitInsn(Opcodes.POP);
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        }
                    };
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        }

        if ("org/lwjgl/opengl/Display".equals(className)) {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                    if ("setIcon".equals(name)) {
                        return new MethodVisitor(Opcodes.ASM5, mv) {
                            private final Label startLabel = new Label();
                            private final Label endLabel = new Label();
                            private final Label handlerLabel = new Label();

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitTryCatchBlock(startLabel, endLabel, handlerLabel, "java/lang/ArrayIndexOutOfBoundsException");
                                super.visitLabel(startLabel);
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitLabel(endLabel);
                                super.visitLabel(handlerLabel);
                                super.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/ArrayIndexOutOfBoundsException"});
                                super.visitInsn(Opcodes.POP); // Pop the Exception
                                super.visitInsn(Opcodes.ICONST_0); // the method returns an int (0 for fail)
                                super.visitInsn(Opcodes.IRETURN);
                                super.visitMaxs(maxStack, maxLocals);
                            }
                        };
                    }
                    return mv;
                }
            };
            try {
                cr.accept(cv, 0);
                return cw.toByteArray();
            } catch (Exception e) {
                // Ignore if mapping frames fails (rare, but just in case)
            }
        }
        return null;
    }
}
