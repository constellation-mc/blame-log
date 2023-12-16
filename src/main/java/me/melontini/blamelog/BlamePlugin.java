package me.melontini.blamelog;

import me.melontini.dark_matter.api.base.reflect.MiscReflection;
import me.melontini.dark_matter.api.base.reflect.Reflect;
import me.melontini.dark_matter.api.danger.instrumentation.InstrumentationAccess;
import me.melontini.dark_matter.api.base.util.MakeSure;
import me.melontini.dark_matter.api.danger.instrumentation.TransformationException;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.slf4j.Log4jLogger;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BlamePlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("BlameLog");

    static {
        if (InstrumentationAccess.canInstrument()) {
            LOGGER.info("[BlameLog] Retranforming Loggers...");

            Class<?> blameUtil;
            if (AbstractLogger.class.getClassLoader() != BlamePlugin.class.getClassLoader()) {
                LOGGER.warn("[BlameLog] AbstractLogger and BlamePlugin are on different classloaders!");
                LOGGER.warn("[BlameLog] This means you're probably running Quilt or Connector. Be ware of issues!");

                try (InputStream stream = BlamePlugin.class.getClassLoader().getResourceAsStream("me/melontini/blamelog/BlameUtil.class")) {
                    byte[] bytes = MakeSure.notNull(stream, "Can't access BlameUtil.class").readAllBytes();

                    ClassLoader a = AbstractLogger.class.getClassLoader();
                    //Thanks Su5eD for the fix. https://github.com/Sinytra/Connector/discussions/12#discussioncomment-6790140
                    InstrumentationAccess.addReads(AbstractLogger.class.getModule(), a.getUnnamedModule());

                    blameUtil = tryDefineClass(a, "me.melontini.blamelog.BlameUtil", bytes, BlameUtil.class.getProtectionDomain());
                } catch (IOException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } else {
                blameUtil = BlameUtil.class;
            }

            try {
                String pattern = "[{simpleClass}#{method}] {message}";
                Path path = FabricLoader.getInstance().getConfigDir().resolve("blamelog-pattern.txt");
                if (!Files.exists(path)) {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, pattern);
                } else {
                    pattern = Files.readString(path);
                }

                Field field = blameUtil.getField("pattern");
                field.setAccessible(true);
                field.set(null, pattern);
            } catch (Throwable t) {
                LOGGER.error("[BlameLog] Failed to set pattern. Using default", t);
            }


            AtomicInteger integer = new AtomicInteger();//Keeping track of the number of methods we've retransformed. Not really necessary, but it's nice to know.
            try {
                InstrumentationAccess.retransform(node -> LogPatcher.patch(node, integer),
                        FabricLoader.getInstance().isDevelopmentEnvironment(),
                        AbstractLogger.class, Log4jLogger.class);
            } catch (TransformationException e) {
                throw new RuntimeException("[BlameLog] Failed to retransform loggers", e);
            }
            LOGGER.info("Successfully retransformed {} methods.", integer.get());
        } else {
            LOGGER.error("[BlameLog] Instrumentation went to get some milk, but never came back...");
        }
    }

    private static Method defineClass;

    private static Class<?> tryDefineClass(ClassLoader a, String name, byte[] bytes, ProtectionDomain domain) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            return MiscReflection.defineClass(a, name, bytes, BlameUtil.class.getProtectionDomain());
        } catch (Throwable t ) {
            if (defineClass == null)
                defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);

            Module javaBase = ClassLoader.class.getModule();
            if (!defineClass.canAccess(null)) {
                if (InstrumentationAccess.get().isModifiableModule(javaBase)) {
                    LOGGER.warn("[BlameLog] Opening java.lang to UNNAMED to make defineClass accessible");
                    InstrumentationAccess.addOpens(javaBase, Map.of("java.lang", Set.of(BlamePlugin.class.getModule())));
                    defineClass.setAccessible(true);
                } else {
                    LOGGER.warn("[BlameLog] Using unsafe to make defineClass accessible");
                    Reflect.setAccessible(defineClass);
                }
            }

            //Is there a better way to define a class on a different class loader?
            return (Class<?>) defineClass.invoke(a, name, bytes, 0, bytes.length, domain);
        }
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() {return null;}
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {return false;}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() {return null;}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
