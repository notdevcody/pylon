package pl.tomgirl.pylon.game.patch.impl;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import pl.tomgirl.pylon.game.patch.Patch;

public final class ScreenPatch extends Patch {
    private static final String CLIPBOARD = "java/awt/datatransfer/Clipboard";
    private static final String AWT_DESKTOP = "java.awt.Desktop";
    private static final String DESKTOP = "pl.tomgirl.pylon.game.Desktop";
    private final Map<Method, ScreenMethod> methods = new HashMap<>();
    private boolean inlineDesktop;

    public ScreenPatch(ClassVisitor next) { super(next); }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, next) {
            private boolean gets;
            private boolean sets;
            private boolean desktop;

            @Override
            public void visitMethodInsn(int opcode, String owner, String called, String calledDescriptor, boolean itf) {
                if (owner.equals(CLIPBOARD)) {
                    gets |= called.equals("getContents");
                    sets |= called.equals("setContents");
                }
                super.visitMethodInsn(opcode, owner, called, calledDescriptor, itf);
            }

            @Override
            public void visitLdcInsn(Object value) {
                desktop |= AWT_DESKTOP.equals(value);
                super.visitLdcInsn(value);
            }

            @Override
            public void visitEnd() {
                if (gets && descriptor.equals("()Ljava/lang/String;")) methods.put(new Method(name, descriptor), ScreenMethod.GET_CLIPBOARD);
                if (sets && descriptor.equals("(Ljava/lang/String;)V")) methods.put(new Method(name, descriptor), ScreenMethod.SET_CLIPBOARD);
                if (desktop) {
                    if (descriptor.equals("(Ljava/net/URI;)V")) methods.put(new Method(name, descriptor), ScreenMethod.OPEN_LINK);
                    else inlineDesktop = true;
                }
                super.visitEnd();
            }
        };
    }

    @Override
    public boolean matches() {
        return !methods.isEmpty() || inlineDesktop;
    }

    @Override
    public ClassVisitor apply(ClassVisitor writer) {
        return new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                ScreenMethod patch = methods.get(new Method(name, descriptor));
                if (patch == null) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            super.visitLdcInsn(AWT_DESKTOP.equals(value) ? DESKTOP : value);
                        }
                    };
                }
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                int argumentSlot = (access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
                method.visitCode();
                if (patch == ScreenMethod.GET_CLIPBOARD) {
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getClipboard", "()Ljava/lang/String;", false);
                    method.visitInsn(Opcodes.ARETURN);
                } else if (patch == ScreenMethod.SET_CLIPBOARD) {
                    method.visitVarInsn(Opcodes.ALOAD, argumentSlot);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "setClipboard", "(Ljava/lang/String;)V", false);
                    method.visitInsn(Opcodes.RETURN);
                } else {
                    method.visitVarInsn(Opcodes.ALOAD, argumentSlot);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "openLink", "(Ljava/net/URI;)V", false);
                    method.visitInsn(Opcodes.RETURN);
                }
                method.visitMaxs(1, argumentSlot + (patch == ScreenMethod.GET_CLIPBOARD ? 0 : 1));
                method.visitEnd();
                return null;
            }
        };
    }

    private enum ScreenMethod { GET_CLIPBOARD, SET_CLIPBOARD, OPEN_LINK }
}
