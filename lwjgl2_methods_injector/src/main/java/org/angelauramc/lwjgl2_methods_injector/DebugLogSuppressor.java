package org.angelauramc.lwjgl2_methods_injector;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Suppresses debug logging from LWJGL2 compatibility classes:
 * - org/lwjgl/input/Cursor: "Encountered non-zero byte at X, custom cursor is not empty!"
 * - org/lwjgl/input/GLFWInputImplementation: "Grab: true/false"
 * 
 * These debug prints cause severe log spam during normal gameplay.
 */
public class DebugLogSuppressor implements ClassFileTransformer {

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new DebugLogSuppressor());
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if ("org/lwjgl/input/Cursor".equals(className) ||
            "org/lwjgl/input/GLFWInputImplementation".equals(className)) {
            try {
                System.out.print("KnightLauncher-Android: Suppressing debug logs in " + className + "\n");
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                ClassVisitor cv = new PrintlnRemover(cw);
                cr.accept(cv, 0);
                return cw.toByteArray();
            } catch (Exception e) {
                System.out.print("KnightLauncher-Android: Failed to patch " + className + ": " + e.getMessage() + "\n");
            }
        }
        return null;
    }

    /**
     * ClassVisitor that removes System.out.println calls from all methods.
     * It works by detecting the pattern:
     *   GETSTATIC java/lang/System.out
     *   ... (StringBuilder operations to build the message)
     *   INVOKEVIRTUAL java/io/PrintStream.println
     * 
     * And replacing them with appropriate stack cleanup.
     */
    static class PrintlnRemover extends ClassVisitor {
        public PrintlnRemover(ClassVisitor cv) {
            super(ASM4, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new PrintlnMethodVisitor(mv);
        }
    }

    /**
     * MethodVisitor that tracks System.out references and removes println calls.
     * 
     * Strategy: When we see GETSTATIC System.out, we start tracking. When we see
     * INVOKEVIRTUAL PrintStream.println, we know the whole sequence was for printing,
     * so we pop the arguments instead of actually calling println.
     */
    static class PrintlnMethodVisitor extends MethodVisitor {
        private boolean inPrintSequence = false;
        private int stackDepth = 0;

        public PrintlnMethodVisitor(MethodVisitor mv) {
            super(ASM4, mv);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == GETSTATIC && "java/lang/System".equals(owner) && "out".equals(name)) {
                // Start tracking a potential print sequence - skip pushing System.out
                inPrintSequence = true;
                stackDepth = 0;
                return; // Don't emit this instruction
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (inPrintSequence) {
                if (opcode == INVOKEVIRTUAL && "java/io/PrintStream".equals(owner) && "println".equals(name)) {
                    // End of print sequence - the string to print is on the stack, just pop it
                    super.visitInsn(POP);
                    inPrintSequence = false;
                    stackDepth = 0;
                    return;
                }
                // We're in a print sequence but this is an intermediate call (e.g., StringBuilder.append)
                // These calls modify the StringBuilder that's on the stack, so we need to handle them
                // For StringBuilder operations, they typically return 'this', so stack is maintained
                if ("java/lang/StringBuilder".equals(owner)) {
                    if ("<init>".equals(name)) {
                        // Skip StringBuilder init - pop the NEW'd object
                        super.visitInsn(POP);
                        return;
                    } else if ("append".equals(name) || "toString".equals(name)) {
                        // Skip append/toString operations
                        // append: pops arg, pushes this -> we need to not change stack for caller
                        // For append with object arg: pop object, and handle "this" reference
                        if (descriptor.contains("Ljava/lang/String;")) {
                            super.visitInsn(POP); // pop the string argument
                        } else if (descriptor.contains(";")) {
                            super.visitInsn(POP); // pop object argument
                        } else if (descriptor.equals("(I)Ljava/lang/StringBuilder;")) {
                            super.visitInsn(POP); // pop int argument
                        } else if (descriptor.equals("(Z)Ljava/lang/StringBuilder;")) {
                            super.visitInsn(POP); // pop boolean argument
                        }
                        if ("toString".equals(name)) {
                            // toString returns String - we'll leave something on stack for println to pop
                            super.visitInsn(ACONST_NULL);
                        }
                        return;
                    }
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (inPrintSequence && opcode == NEW && "java/lang/StringBuilder".equals(type)) {
                // Skip NEW StringBuilder
                return;
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (inPrintSequence && opcode == DUP) {
                // Skip DUP for StringBuilder
                return;
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (inPrintSequence) {
                // Skip loading string constants for the print message
                return;
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (inPrintSequence && (opcode == ILOAD || opcode == ALOAD)) {
                // Skip loading variables that are arguments to the print
                return;
            }
            super.visitVarInsn(opcode, var);
        }
    }
}
