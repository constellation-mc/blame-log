package me.melontini.blamelog;

import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class BlameUtil {
    private static final StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Class<? extends Annotation> MixinMerged;
    private static final Method mixinMethod;

    static {
        Class<? extends Annotation> c = null;
        try {
            c = (Class<? extends Annotation>) Class.forName("org.spongepowered.asm.mixin.transformer.meta.MixinMerged");
        } catch (Exception ignored) {}
        MixinMerged = c;
        Method m = null;
        try {
            m = c.getMethod("mixin");
            m.setAccessible(true);
        } catch (Exception ignored) {}
        mixinMethod = m;
    }

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
                Object mixin = m.getAnnotation(MixinMerged);
                if (mixin != null) {
                    return "[" + mixinMethod.invoke(mixin)+ "#" + methodName + "] " + message;
                }
            } catch (Exception ignored) {}//we don't care if this fails.
        }
        return "[" + frame.getClassName() + "#" + methodName + "] " + message;
    }

    public static StackWalker.StackFrame getCallerName(int depth) {
        return stackWalker.walk(s -> s.skip(depth).findFirst().orElse(null));
    }
}
