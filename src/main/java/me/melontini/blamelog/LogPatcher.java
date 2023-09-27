package me.melontini.blamelog;

import me.melontini.dark_matter.api.base.util.Utilities;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LogPatcher {

    private static final Set<String> allowedNames = Utilities.consume(new HashSet<>(), strings -> {
        strings.add("fatal");
        strings.add("error");
        strings.add("warn");
        strings.add("info");
        strings.add("log");
        //strings.add("debug");
        //strings.add("trace");
    });

    public static ClassNode patch(ClassNode node, AtomicInteger integer) {
        for (MethodNode method : node.methods) {
            Type[] types = Type.getArgumentTypes(method.desc);

            if (types.length == 0) continue;

            if (allowedNames.contains(method.name)) {
                int sIndex = -1;
                for (int i = 0; i < types.length; i++) {
                    Type type = types[i];
                    if ("java.lang.String".equals(type.getClassName())) {
                        if ("(Lorg/slf4j/Marker;Ljava/lang/String;ILjava/lang/String;[Ljava/lang/Object;Ljava/lang/Throwable;)V".equals(method.desc))
                            i += 2;
                        sIndex = i;
                        break;
                    }
                }

                if (sIndex == -1) {//we know that there's no String parameter
                    int objIndex = -1;
                    for (int i = 0; i < types.length; i++) {
                        Type type = types[i];
                        if ("java.lang.Object".equals(type.getClassName())) {
                            objIndex = i;
                            break;
                        }
                    }

                    if (objIndex == -1) continue;

                    patchObjectMethod(method, objIndex);
                    integer.getAndIncrement();
                    continue;
                }

                patchStringMethod(method, sIndex);
                integer.getAndIncrement();
            }
        }
        return node;
    }

    private static void patchObjectMethod(MethodNode method, int objIndex) {
        InsnList newInsn = new InsnList();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode varInsnNode) {
                if (varInsnNode.var == objIndex + 1) {
                    newInsn.add(new LdcInsnNode("{}"));
                    newInsn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/melontini/blamelog/BlameUtil", "getMessage", "(Ljava/lang/String;)Ljava/lang/String;"));
                }
            }
            if (instruction instanceof MethodInsnNode methodInsnNode) {
                methodInsnNode.desc = methodInsnNode.desc.replaceFirst("Ljava/lang/Object;", "Ljava/lang/String;Ljava/lang/Object;").replace("Ljava/lang/Throwable;", "Ljava/lang/Object;");
            }
            newInsn.add(instruction);
        }
        method.instructions = newInsn;
    }

    private static void patchStringMethod(MethodNode method, int sIndex) {
        InsnList newInsn = new InsnList();
        for (AbstractInsnNode instruction : method.instructions) {
            newInsn.add(instruction);
            if (instruction instanceof VarInsnNode varInsnNode) {
                if (varInsnNode.var == sIndex + 1) {
                    newInsn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/melontini/blamelog/BlameUtil", "getMessage", "(Ljava/lang/String;)Ljava/lang/String;"));
                }
            }
        }
        method.instructions = newInsn;
    }
}
