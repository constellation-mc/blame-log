package me.melontini.blamelog;

import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;

public class BlameUtil {
    private static final StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static String getMessage(String message) {
        int depth = 3;
        StackWalker.StackFrame frame = getCallerName(depth);
        String name = frame.getClassName();
        while (StringUtils.containsIgnoreCase(name, "log4j") ||
                StringUtils.containsIgnoreCase(name, "slf4j") ||
                StringUtils.containsIgnoreCase(name, "logger") ||
                StringUtils.endsWithAny(name, "Logger", "Log", "LogHelper", "LoggerAdapterAbstract", "Logging") ||
                "log".equals(frame.getMethodName())) {//hardcoded list of filters. While this might add overhead, it's better than undescriptive names.

            depth++;
            frame = getCallerName(depth);
            name = frame.getClassName();
        }
        String methodName = frame.getMethodName();
        if (frame.getClassName().startsWith("net.minecraft") && !StringUtils.equalsAny(methodName, "<init>", "<clinit>")) {
            try {
                Method m = frame.getDeclaringClass().getDeclaredMethod(methodName, frame.getMethodType().parameterArray());
                MixinMerged mixin = m.getAnnotation(MixinMerged.class);
                if (mixin != null) {
                    return "[" + mixin.mixin() + "#" + methodName + "] " + message;
                }
            } catch (Exception ignored) {}//we don't care if this fails.
        }
        return "[" + frame.getClassName() + "#" + methodName + "] " + message;
    }

    public static StackWalker.StackFrame getCallerName(int depth) {
        return stackWalker.walk(s -> s.skip(depth).findFirst().orElse(null));
    }
}
