package me.melontini.blamelog;

import org.apache.commons.lang3.StringUtils;

public class Util {
    private static final StackWalker stackWalker = StackWalker.getInstance();

    public static String getMessage(String message) {
        int depth = 3;
        String name = getCallerName(depth);
        while (StringUtils.containsIgnoreCase(name, "log4j") || StringUtils.containsIgnoreCase(name, "slf4j") || StringUtils.containsIgnoreCase(name, "logger") || StringUtils.endsWithAny(name.split("#")[0], "Logger", "Log", "LogHelper", "LoggerAdapterAbstract")) {//hardcoded checks. This adds overhead and can have false-positives (like, mmm "BlameLog"), but it's better than useless "Log#info"
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
}
