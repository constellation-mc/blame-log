package me.melontini.blamelog;

import me.melontini.dark_matter.api.danger.instrumentation.InstrumentationAccess;
import me.melontini.dark_matter.api.base.reflect.ReflectionUtil;
import me.melontini.dark_matter.api.base.util.MakeSure;
import me.melontini.dark_matter.api.base.util.Utilities;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.slf4j.Log4jLogger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BlamePlugin implements IMixinConfigPlugin {
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

    static {
        if (InstrumentationAccess.canInstrument()) {
            LOGGER.info("[BlameLog] Retranforming Loggers...");

            if (AbstractLogger.class.getClassLoader() != BlamePlugin.class.getClassLoader()) {
                LOGGER.warn("[BlameLog] AbstractLogger and BlamePlugin are on different classloaders!");
                LOGGER.warn("[BlameLog] This means you're probably running Quilt. Be ware of issues!");

                try (InputStream stream = BlamePlugin.class.getClassLoader().getResourceAsStream("me/melontini/blamelog/BlameUtil.class")) {
                    byte[] bytes = MakeSure.notNull(stream, "Can't access BlameUtil.class").readAllBytes();

                    ClassLoader a = AbstractLogger.class.getClassLoader();
                    Method define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);

                    Module javaBase = ClassLoader.class.getModule();
                    Instrumentation instr = InstrumentationAccess.getInstrumentation();
                    if (instr.isModifiableModule(javaBase)) {
                        LOGGER.warn("[BlameLog] Opening java.lang to UNNAMED to make defineClass accessible");
                        instr.redefineModule(javaBase, Set.of(), Map.of(), Map.of("java.lang", Set.of(BlamePlugin.class.getModule())), Set.of(), Map.of());
                        define.setAccessible(true);
                    } else {
                        LOGGER.warn("[BlameLog] Using unsafe to make defineClass accessible");
                        ReflectionUtil.setAccessible(define);
                    }

                    //Is there a better way to define a class on a different class loader?
                    define.invoke(a, "me.melontini.blamelog.BlameUtil", bytes, 0, bytes.length, BlameUtil.class.getProtectionDomain());
                } catch (IOException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            AtomicInteger integer = new AtomicInteger();//Keeping track of the number of methods we've retransformed. Not really necessary, but it's nice to know.
            InstrumentationAccess.retransform(node -> {
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
                            integer.getAndIncrement();

                            continue;
                        }

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
                        integer.getAndIncrement();
                    }
                }
                return node;
            }, FabricLoader.getInstance().isDevelopmentEnvironment(), AbstractLogger.class, Log4jLogger.class);
            LOGGER.info("Successfully retransformed {} methods.", integer.get());
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
