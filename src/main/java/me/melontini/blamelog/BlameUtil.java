package me.melontini.blamelog;

import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

public class BlameUtil {
    private static final StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final Map<String, BiFunction<StackWalker.StackFrame, MixinMerged, String>> patterns = Map.of(
            "{class}", (frame, mixin) -> mixin == null ? frame.getClassName() : mixin.mixin(),
            "{method}", (frame, mixin) -> frame.getMethodName(),
            "{simpleClass}", (frame, mixin) -> mixin == null ? simpleClassName(frame.getDeclaringClass()) : simpleClassName(mixin.mixin()),
            "{methodParams}",  (frame, mixin) -> {
                Class<?>[] params = frame.getMethodType().parameterArray();
                if (params.length == 0) return "";

                StringJoiner joiner = new StringJoiner(",");
                for (Class<?> param : params) {
                    joiner.add(simpleClassName(param));
                }
                return joiner.toString();
            },
            "{methodReturnType}", (frame, mixin) -> simpleClassName(frame.getMethodType().returnType())
    );

    private static final List<Predicate<StackWalker.StackFrame>> filters;

    static {
        List<Predicate<StackWalker.StackFrame>> base = new ArrayList<>();

        base.add(frame -> "log".equals(frame.getMethodName()));

        String[] classContains = new String[] {"log4j", "slf4j", "logger"};
        base.add(frame -> {
            for (String string : classContains) {
                if (containsIgnoreCase(frame.getClassName(), string)) return true;
            }
            return false;
        });

        String[] classEnds = new String[] {"Logger", "Log", "LogHelper", "LoggerAdapterAbstract", "Logging"};
        base.add(frame -> {
            for (String string : classEnds) {
                if (StringUtils.endsWith(frame.getClassName(), string)) return true;
            }
            return false;
        });

        filters = List.copyOf(base);
    }

    public static String pattern = "[{simpleClass}#{method}] {message}";

    private static String simpleClassName(String cls) {
        String[] split = cls.split("\\.");
        return split[split.length - 1];
    }

    private static String simpleClassName(Class<?> cls) {
        if (cls.isPrimitive()) return cls.getName();
        return cls.getName().substring(cls.getPackageName().length() + 1);
    }

    public static String getMessage(String msg) {
        StackWalker.StackFrame frame = firstMatching();
        String methodName = frame.getMethodName();

        MixinMerged mixin = null;
        if (frame.getClassName().startsWith("net.minecraft") && !StringUtils.equalsAny(methodName, "<init>", "<clinit>")) {
            var params = frame.getMethodType().parameterArray();
            for (Method method : frame.getDeclaringClass().getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != params.length) continue;
                if (!Arrays.equals(params, method.getParameterTypes())) continue;
                mixin = method.getAnnotation(MixinMerged.class);
            }
        }

        String message = pattern;
        for (Map.Entry<String, BiFunction<StackWalker.StackFrame, MixinMerged, String>> entry : patterns.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue().apply(frame, mixin));
        }
        return message.replace("{message}", msg);
    }

    public static StackWalker.StackFrame firstMatching() {
        return stackWalker.walk(s -> s.skip(3).dropWhile(frame -> {
            for (Predicate<StackWalker.StackFrame> filter : filters) {
                if (filter.test(frame)) return true;
            }
            return false;
        }).findFirst()).orElse(null);
    }
}
