package me.melontini.blamelog;

import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BiFunction;

public class BlameUtil {
    private static final StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final Map<String, BiFunction<StackWalker.StackFrame, MixinMerged, String>> patterns = Map.of(
            "{class}", (frame, mixin) -> mixin == null ? frame.getClassName() : mixin.mixin(),
            "{method}", (frame, mixin) -> frame.getMethodName(),
            "{simpleClass}", (frame, mixin) -> simpleClassName(mixin == null ? frame.getClassName() : mixin.mixin()),
            "{methodParams}",  (frame, mixin) -> {
                Class<?>[] params = frame.getMethodType().parameterArray();
                String[] paramNames = new String[params.length];
                for (int i = 0; i < params.length; i++) {
                    paramNames[i] = simpleClassName(params[i].getName());
                }
                return StringUtils.join(paramNames, ",");
            },
            "{methodReturnType}", (frame, mixin) -> simpleClassName(frame.getMethodType().returnType().getName())
    );

    public static String pattern = "[{simpleClass}#{method}] {message}";

    private static String simpleClassName(String cls) {
        String[] split = cls.split("\\.");
        return split[split.length - 1];
    }

    public static String getMessage(String msg) {
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

        MixinMerged mixin = null;
        if (frame.getClassName().startsWith("net.minecraft") && !StringUtils.equalsAny(methodName, "<init>", "<clinit>")) {
            try {
                Method m = frame.getDeclaringClass().getDeclaredMethod(methodName, frame.getMethodType().parameterArray());
                mixin = m.getAnnotation(MixinMerged.class);
            } catch (Exception ignored) {}//we don't care if this fails.
        }

        String message = pattern;
        for (Map.Entry<String, BiFunction<StackWalker.StackFrame, MixinMerged, String>> entry : patterns.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue().apply(frame, mixin));
        }
        return message.replace("{message}", msg);
    }

    public static StackWalker.StackFrame getCallerName(int depth) {
        return stackWalker.walk(s -> s.skip(depth).findFirst().orElse(null));
    }
}
