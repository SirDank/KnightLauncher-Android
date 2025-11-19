package org.angelauramc.lwjgl2_methods_injector;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class ALC10Injector extends ClassVisitor implements ClassFileTransformer {
    protected ALC10Injector(int api) {
        super(api);
    }

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader l, String name, Class c,
                                    ProtectionDomain d, byte[] b) {
                if (!"org/lwjgl/openal/ALC10".equals(name)) {
                    return null;
                }
                System.out.println("Modifying ALC10 for LWJGL2 compatibility...");
                ClassReader cr = new ClassReader(b);
                ClassWriter cw = new ClassWriter(cr, 0);
                ClassVisitor cv = new AddMethodAdapter(cw);
                cr.accept(cv, 0);
                return cw.toByteArray();
            }
        });

    }

    public static class AddMethodAdapter extends ClassVisitor {
        public AddMethodAdapter(ClassVisitor cv) {
            super(ASM4, cv);
        }
        public void visitEnd() {
            // Create the method: public static ALCcontext alcGetCurrentContext()
            MethodVisitor mv = cv.visitMethod(
                    ACC_PUBLIC | ACC_STATIC,                        // method modifiers
                    "alcGetCurrentContext",                         // method name
                    "()Lorg/lwjgl/openal/ALCcontext;",              // descriptor (return type)
                    null,                                           // signature
                    null                                            // exceptions
            );

            mv.visitCode();

            // GETSTATIC org/lwjgl/openal/ALC10.alcContext : Lorg/lwjgl/openal/ALCcontext;
            mv.visitFieldInsn(
                    GETSTATIC,
                    "org/lwjgl/openal/ALC10",                       // owner (this class)
                    "alcContext",                                   // field name
                    "Lorg/lwjgl/openal/ALCcontext;"                 // field type descriptor
            );

            // Return it
            mv.visitInsn(ARETURN);

            // Stack size = 1 (field value), locals = 0 (static method)
            mv.visitMaxs(1, 0);
            mv.visitEnd();

            super.visitEnd();
        }
    }
}

