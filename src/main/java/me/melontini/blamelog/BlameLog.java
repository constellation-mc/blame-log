package me.melontini.blamelog;

import me.melontini.crackerutil.danger.instrumentation.InstrumentationAccess;
import me.melontini.crackerutil.util.Utilities;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.slf4j.Log4jLogger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class BlameLog implements IMixinConfigPlugin {//we don't need ExtendedPlugin
    private static final Logger LOGGER = LogManager.getLogger("BlameLog");
    private static final StackWalker stackWalker = StackWalker.getInstance();
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
                                sIndex = i;
                                break;
                            }
                        }

                        if (sIndex == -1) continue;

                        InsnList newInsn = new InsnList();
                        for (AbstractInsnNode instruction : method.instructions) {
                            newInsn.add(instruction);
                            if (instruction instanceof VarInsnNode varInsnNode) {
                                if (varInsnNode.var == sIndex + 1) {
                                    newInsn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "me/melontini/blamelog/BlameLog", "getMessage", "(Ljava/lang/String;)Ljava/lang/String;"));
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

    public static String getMessage(String message) {
        int depth = 3;
        String name = getCallerName(depth);
        while (StringUtils.containsAnyIgnoreCase(name, "log4j", "slf4j", "logger") || StringUtils.endsWithAny(name.split("#")[0], "Logger", "Log", "LogHelper", "LoggerAdapterAbstract")) {//hardcoded checks. This adds overhead and can have false-positives (like, mmm "BlameLog"), but it's better than useless "Log#info"
            depth++;
            name = getCallerName(depth);
        }
        return "[" + name + "] " + message;
    }

    public static String getCallerName(int depth) {
        return stackWalker.walk(s -> {
            var first = s.skip(depth).findFirst().orElse(null);
            return first != null ? first.getClassName() + "#" + first.getMethodName() : "NoClassNameFound";
        });
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
