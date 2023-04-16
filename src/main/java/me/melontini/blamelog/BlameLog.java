package me.melontini.blamelog;

import me.melontini.crackerutil.danger.instrumentation.InstrumentationAccess;
import me.melontini.crackerutil.util.Utilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.slf4j.Log4jLogger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BlameLog implements IMixinConfigPlugin {//we don't need ExtendedPlugin
    private static final Logger LOGGER = LogManager.getLogger("BlameLog");
    static Set<String> allowedNames = Utilities.consume(new HashSet<>(), strings -> {
        strings.add("fatal");
        strings.add("error");
        strings.add("warn");
        strings.add("info");
        strings.add("log");
        //strings.add("debug");
        //strings.add("trace");
    });

    static Map<String, Integer> HARDCODED_INDEX_OFFSETS = Utilities.consume(new HashMap<>(), map -> {
        map.put("(Lorg/slf4j/Marker;Ljava/lang/String;ILjava/lang/String;[Ljava/lang/Object;Ljava/lang/Throwable;)V", 2);//It gets called 3 times.
    });

    static {
        if (InstrumentationAccess.canInstrument()) {
            LOGGER.info("[BlameLog] Trying to hack Loggers with Instrumentation");
            AtomicInteger integer = new AtomicInteger();
            InstrumentationAccess.retransform(node -> {
                for (MethodNode method : node.methods) {
                    Type[] types = Type.getArgumentTypes(method.desc);

                    if (types.length == 0) continue;

                    if (allowedNames.contains(method.name)) {
                        int sIndex = -1;
                        for (int i = 0; i < types.length; i++) {
                            Type type = types[i];
                            if ("java.lang.String".equals(type.getClassName())) {
                                if (HARDCODED_INDEX_OFFSETS.containsKey(method.desc))
                                    i += HARDCODED_INDEX_OFFSETS.get(method.desc);
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


                            InsnList newInsn = new InsnList();
                            for (AbstractInsnNode instruction : method.instructions) {
                                if (instruction instanceof VarInsnNode varInsnNode) {
                                    if (varInsnNode.var == objIndex + 1) {
                                        newInsn.add(new LdcInsnNode("{}"));
                                        newInsn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/melontini/blamelog/Util", "getMessage", "(Ljava/lang/String;)Ljava/lang/String;"));
                                    }
                                }
                                if (instruction instanceof MethodInsnNode methodInsnNode) {
                                    methodInsnNode.desc = methodInsnNode.desc.replaceFirst("Ljava/lang/Object;", "Ljava/lang/String;Ljava/lang/Object;").replace("Ljava/lang/Throwable;", "Ljava/lang/Object;");
                                }
                                newInsn.add(instruction);
                            }
                            method.instructions = newInsn;
                            integer.getAndIncrement();

                            continue;
                        }

                        InsnList newInsn = new InsnList();
                        for (AbstractInsnNode instruction : method.instructions) {
                            newInsn.add(instruction);
                            if (instruction instanceof VarInsnNode varInsnNode) {
                                if (varInsnNode.var == sIndex + 1) {
                                    newInsn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/melontini/blamelog/Util", "getMessage", "(Ljava/lang/String;)Ljava/lang/String;"));
                                }
                            }
                        }
                        method.instructions = newInsn;
                        integer.getAndIncrement();
                    }
                }
                return node;
            }, true, AbstractLogger.class, Log4jLogger.class);
            LOGGER.info("[BlameLog] Hacking Loggers was successful, as you can see... Wait, I'm not \"java.lang.Class#forName0\" >:|");
            LOGGER.info("[BlameLog] Hacked {} methods", integer.get());
        } else {
            LOGGER.error("[BlameLog] Instrumentation went to get some milk, but never came back...");
        }
    }

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
