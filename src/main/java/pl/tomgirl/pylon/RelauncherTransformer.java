package pl.tomgirl.pylon;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.logging.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RelauncherTransformer implements ClassFileTransformer {
    public static final String LIBRARY_MANAGER_CLASS = "cpw/mods/fml/relauncher/RelaunchLibraryManager";
    public static final String COREMODS_PROP = "fml.coreMods.load";
    public static final String COREMOD = "pl.tomgirl.pylonfml.CpwCoreMod";

    private final Instrumentation instrumentation;

    RelauncherTransformer(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
        if (!LIBRARY_MANAGER_CLASS.equals(name)) {
            return null;
        }

        try {
            ClassReader reader = new ClassReader(bytes);
            char[] buffer = new char[reader.getMaxStringLength()];
            for (int i = 1; i < reader.getItemCount(); i++) {
                int item = reader.getItem(i);
                if (item != 0 && reader.readByte(item - 1) == 8 && COREMODS_PROP.equals(reader.readConst(i, buffer))) {
                    return null;
                }
            }

            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (!"<clinit>".equals(name)) {
                        return method;
                    }

                    return new MethodVisitor(Opcodes.ASM9, method) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                appendRootPlugin();
                            }
                            super.visitInsn(opcode);
                        }

                        private void appendRootPlugin() {
                            String array = "[Ljava/lang/String;";
                            super.visitFieldInsn(Opcodes.GETSTATIC, LIBRARY_MANAGER_CLASS, "rootPlugins", array);
                            super.visitInsn(Opcodes.DUP);
                            super.visitInsn(Opcodes.ARRAYLENGTH);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitInsn(Opcodes.IADD);
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "java/util/Arrays", "copyOf",
                                "([Ljava/lang/Object;I)[Ljava/lang/Object;",
                                false
                            );
                            super.visitTypeInsn(Opcodes.CHECKCAST, array);
                            super.visitInsn(Opcodes.DUP);
                            super.visitInsn(Opcodes.DUP);
                            super.visitInsn(Opcodes.ARRAYLENGTH);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitInsn(Opcodes.ISUB);
                            super.visitLdcInsn(COREMOD);
                            super.visitInsn(Opcodes.AASTORE);
                            super.visitFieldInsn(Opcodes.PUTSTATIC, LIBRARY_MANAGER_CLASS, "rootPlugins", array);
                        }
                    };
                }
            }, 0);
            return writer.toByteArray();
        } catch (RuntimeException e) {
            Pylon.LOG.log(Level.SEVERE, "Couldn't register Pylon with early FML", e);
            return null;
        } finally {
            instrumentation.removeTransformer(this);
        }
    }
}
